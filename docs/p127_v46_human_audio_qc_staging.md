# P127 V4.6 — Human audio technical QC (STAGING, not a release)

Baseline: P126 / VC153, verified original GitHub Actions Safe Source.

## Newly measured from the actual 56 P126 WAV assets
- 56/56 decodable 16-bit PCM WAV files; 44.1 kHz; stereo.
- Combined durations approximately 60.64 seconds.
- 52/56 files have >=0.1% samples with |PCM| >=32760 out of 32768: clipping/saturation *review flags*, not a conclusive listening judgment.
- 54/56 files have near-full-scale peaks (>=0.995).
- Files were compared against the V4.5 inventory SHA-256 values and remained unchanged.
- **0 audio files are thereby certified for Tajweed or mapped to the 408 required P127 lesson slots.**

V4.5 still lists 408 proposed audio positions and 80 verse/context references.
59 of 80 reference slots are associated with candidate source *surahs only*; none has approved timestamps, audible verification, a teacher review, complete license provenance review, or Android playback QA.

Wikimedia candidate download was rejected with HTTP 429 on run 44188558593. Automatic downloads remain disabled. Do not bypass Wikimedia rate limiting or copy uncertain-license recordings.

Execution is tracked in the independently testable V4.6 Safe Staging ZIP prepared separately. Do not merge into a release or call this P127/VC154. No APK or signing keys are included.

## Guards
- No changes to 604 P126 mushaf pages, Uthmani text, existing modules or progress database
- No modified baseline WAV; lowering digital gain cannot undo clipping
- Publish only after rights review, complete human recitation correction, device test and original-owner signing
