package com.ab.khatma.quran

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * P104: how much silence a verse recording has at its start and at its end.
 * Measured once, in the background, from the copy of the verse that is already on the phone;
 * the verse player uses it to start the next verse so the two silences do not add up to an audible stop.
 */
object P104Silence {
    class Edges(val headMs: Int, val tailMs: Int)

    private const val LOUD = 400          // 16-bit peak above which a moment counts as sound (about -38 dBFS)
    private const val MAX_EDGE_MS = 4000
    private val memory = ConcurrentHashMap<String, Edges>()
    private val busy = ConcurrentHashMap<String, Boolean>()

    fun get(path: String): Edges? = memory[path]

    /** Starts the measurement in the background when [path] is a local file that has not been measured yet. */
    fun measureLater(path: String) {
        if (memory.containsKey(path) || !path.startsWith("/")) return
        if (busy.putIfAbsent(path, true) != null) return
        Thread {
            try {
                measure(path)?.let { memory[path] = it }
            } catch (_: Throwable) {
            } finally {
                busy.remove(path)
            }
        }.start()
    }

    private fun measure(path: String): Edges? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var fmt: MediaFormat? = null
            var mime = ""
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    ex.selectTrack(i)
                    fmt = f
                    mime = m
                    break
                }
            }
            if (fmt == null) return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            var rate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val info = MediaCodec.BufferInfo()
            var framesDone = 0L
            var firstLoud = -1.0
            var lastLoud = -1.0
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && guard < 20000) {
                guard += 1
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii) ?: return null
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    if (info.size > 0) {
                        val out = c.getOutputBuffer(oi) ?: return null
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val sh = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val ch = if (channels < 1) 1 else channels
                        val frames = sh.remaining() / ch
                        val step = if (rate / 200 < 1) 1 else rate / 200      // 5 ms blocks
                        var f = 0
                        while (f < frames) {
                            val end = if (f + step < frames) f + step else frames
                            var peak = 0
                            var k = f * ch
                            while (k < end * ch) {
                                val v = Math.abs(sh.get(k).toInt())
                                if (v > peak) peak = v
                                k += 1
                            }
                            if (peak > LOUD) {
                                if (firstLoud < 0) firstLoud = (framesDone + f) * 1000.0 / rate
                                lastLoud = (framesDone + end) * 1000.0 / rate
                            }
                            f = end
                        }
                        framesDone += frames
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (!outputDone || firstLoud < 0 || framesDone <= 0L) return null
            val totalMs = framesDone * 1000.0 / rate
            val head = firstLoud.toInt().coerceIn(0, MAX_EDGE_MS)
            val tail = (totalMs - lastLoud).toInt().coerceIn(0, MAX_EDGE_MS)
            return Edges(head, tail)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
    }
}
