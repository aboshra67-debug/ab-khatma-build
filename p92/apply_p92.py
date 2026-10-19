#!/usr/bin/env python3
"""P92/VC116 Safe Patch: integrity of retained MP3 files and offline replay regression.

Apply ONLY to the exact verified P91/VC115 source. Never deletes previous data.
"""
import hashlib
import pathlib
import shutil
import sys

ROOT = pathlib.Path(sys.argv[1]).resolve()
HERE = pathlib.Path(__file__).resolve().parent

def checked(path, sha):
    p = ROOT / path
    old = p.read_bytes()
    digest = hashlib.sha256(old).hexdigest()
    if digest != sha:
        raise RuntimeError(f"Unexpected baseline: {path} ({digest})")
    return p, old.decode("utf-8")

def one(text, old, new, path):
    if text.count(old) != 1:
        raise RuntimeError(f"Expected one patch anchor in {path}; got {text.count(old)}")
    return text.replace(old, new, 1)

def put(path, text):
    path.write_text(text, encoding="utf-8")

gradle = "app/build.gradle.kts"
p, data = checked(gradle, "e5823b1d05b6c1d22c41477a526a2293e64a70b6841f60a23e05511ca8091753")
data = one(data, 'versionCode = 115', 'versionCode = 116', gradle)
data = one(data, 'versionName = "0.19.86-p91-offline-guide-safe"',
           'versionName = "0.19.87-p92-offline-replay-integrity-safe"', gradle)
put(p, data)

path = "app/src/main/java/com/ab/khatma/media/P91OfflineAudio.kt"
p, data = checked(path, "a445949bae23cdb352f686a52ba72e10ecb404b9bc2429a5dc47d68151e5658a")
old = '''    fun saved(context: Context, url: String): File? {
        if (!validUrl(url)) return null
        return location(context, url).takeIf { it.isFile && it.length() in 1_024..MAX_BYTES }
    }
    fun count(context: Context): Int = dir(context).listFiles()?.count { it.extension == "mp3" } ?: 0
    fun usedBytes(context: Context): Long = dir(context).listFiles()?.filter { it.extension == "mp3" }?.sumOf { it.length() } ?: 0L
'''
new = '''    // P92: a downloaded file cannot be advertised as saved solely because it exists.
    // Read only four header bytes; never delete or alter existing recordings.
    private fun validSavedFile(file: File): Boolean {
        if (!file.isFile || file.length() !in 1_024..MAX_BYTES) return false
        return runCatching {
            val header = ByteArray(4)
            val n = file.inputStream().use { it.read(header) }
            n >= 3 && ((header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()) ||
                ((header[0].toInt() and 0xFF) == 0xFF &&
                 (header[1].toInt() and 0xE0) == 0xE0))
        }.getOrDefault(false)
    }
    fun saved(context: Context, url: String): File? {
        if (!validUrl(url)) return null
        return location(context, url).takeIf(::validSavedFile)
    }
    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.extension == "mp3" && validSavedFile(it) } ?: 0
    fun usedBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.extension == "mp3" && validSavedFile(it) }
            ?.sumOf { it.length() } ?: 0L
'''
data = one(data, old, new, path)
put(p, data)

source = HERE / "overlay/app/src/androidTest/java/com/ab/khatma/guide/P92OfflineReplayDeviceTest.kt"
assert source.is_file()
target = ROOT / "app/src/androidTest/java/com/ab/khatma/guide/P92OfflineReplayDeviceTest.kt"
if target.exists():
    raise RuntimeError("P92 test exists in baseline; aborting")
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source, target)
assert "versionCode = 116" in (ROOT / gradle).read_text()
assert "private fun validSavedFile(" in p.read_text()
print("P92 Safe Patch applied: app/build.gradle.kts, P91OfflineAudio.kt, new P92 offline replay device test")
