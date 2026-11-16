# P125 / VC152 — source-backed offline Quran-word audio

**What is recorded:** four word-by-word clips for Al-Fatihah 1:1:
001_001_001.mp3, 001_001_002.mp3, 001_001_003.mp3, 001_001_004.mp3.
They are played *inside* lesson R01 using original Android MediaPlayer.
The clips are not synthetic; no Quran text or page image is modified.

**License:** QuranHive Word-by-Word Quran Audio Dataset, CC BY 4.0.
Credit: QuranHive — © QuranHive, licensed under CC BY 4.0.
https://huggingface.co/datasets/quranhive/quran_word_by_word_audio
https://quranhive.com — qh@quranhive.com

**Scope limitation:** This does not complete teaching audio for all 58 lessons.
Specifically N13 رَبَّ / رَبَ has no exact authorized teacher recording:
the existing N13 video is a rule explanation only. No fake matching audio is supplied.

The build fetches pinned four QuranHive source files, verifies MIME and pinned
SHA256 before packaging, and preserves the original Quran reader and pages.
