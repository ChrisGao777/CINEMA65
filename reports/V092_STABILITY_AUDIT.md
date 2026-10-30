# CINEMA 65 v0.9.2 — Stability and efficiency audit

Date: 2026-10-27. Base: `cinema65-v091-crash-hotfix` (`937cfd5`).
Scope: source consolidation and defensive corrections; no change to v0.8.2 stable app, no automatic rollout.

## Version map

| Stream | Purpose | Current position |
|---|---|---|
| v0.8.2 | Original, separately installed stable test | Unmodified |
| v0.8.3–v0.8.9 | Models, budgets, tone safety and conservative blending | Preserved as source history |
| v0.9.0 | Three manually selectable Director Cuts | Preserved |
| v0.9.1 hotfix branches | Default native-model bypass, crash-stage journaling, reduced preview dimensions | Latest v0.9.1 hotfix selected as base; other experiments not blindly merged |
| v0.9.2 stability audit | Shared analysis, safer export, picker fallbacks, correct NIMA crop, regression gates | This isolated branch only |

## Processing contracts

1. Import through Android Photo Picker / gallery; decode an sRGB software preview capped at 1280 px on its longest side.
2. Safe default: compute traditional scene analysis once, render baseline grade and the chosen Director Cut. **No MediaPipe, ONNX or NIMA runtime is launched.**
3. Explicit full-model experiment: start from a copy of the same analysis, run visual guidance and model/risk evaluation. Keep the untreated baseline analysis independently adjustable.
4. All three Director Cuts use original-pixel evidence and normalized spatial maps. No guessed light source, automatic enhancement claims or cloud calls.
5. Export: re-decode the original image, apply grading in reusable stripes, preserve dimensions, encode JPEG. An entire mutable Bitmap is still resident; **stripes do not make decode/encode streaming**.

## Actual changes in this branch

- **Duplicate analysis removed** in full-model mode. One expensive `FilmCore.analyze` plus an independently mutable `copyAnalysis` replaces two full analyses. Source-derived maps are shared read-only; model state remains isolated. No change to the baseline's pixels is expected.
- **Export allocation guard strengthened**: checks source pixel dimensions, remaining app Java heap and Android system memory before allocating a mutable full-size bitmap; fails clearly rather than attempting a probable OOM. Export still refuses to silently downscale.
- **One export stripe array copy removed** for non-100% style intensity via `blendOutputPrefix`. Unused buffer tail is not touched; the blending math is unchanged.
- **Photo picker exceptions handled**: fallback picker errors no longer propagate uncaught, and single-item `ClipData` results work when `getData()` is absent.
- **NIMA input corrected** to a center square crop, then 224×224 resize. Earlier code distorted non-square photographs, contrary to its own documentation. This can legitimately change full-model candidate rankings.
- **Save UI state locked**: other style and mask controls are disabled until export success/failure, preventing conflicting mutable state changes during file writing.
- Added pure Java stability tests for analysis isolation, pixel equivalence, export prefix writes and memory threshold rejection.

## Verification and performance evidence

- 18 existing pure-Java regression tests + 1 new `StabilityRegressionTest` passed locally.
- Desktop `AnalysisReusePerformanceSmokeTest`, synthetic 960×720 input: repeated analyze pair **26.6 ms**, shared analyze+copy **11.4 ms** (2.33× faster **for this step**, on this host). This is neither total pipeline acceleration nor a Xiaomi 17 Ultra benchmark.
- GitHub Actions compiles Android and verifies a signed debug package once the audit branch is pushed; a passing build is not proof of real-device runtime stability.

## Outstanding risks, in priority order

1. **P0 / actual crash cause unproven.** Native U2NETP / MediaPipe / TFLite can fail outside Java `catch` and require device `logcat` or tombstones. Safe mode avoids these runtimes, but is not formally crash-free.
2. **P0 / large originals.** Android `ImageDecoder` and `Bitmap.compress` cannot be made full streaming by stripwise pixel edits. 50–100MP RAW/JPEG exports may now be safely refused depending on available memory. True very-large-image support requires a region decoder and a suitable streaming writer, plus metadata/orientation tests.
3. **P1 / subjective quality.** Center cropping NIMA fixes geometry but changes scores; no large original-photo human A/B validation yet. Director Cuts remain manual by design.
4. **P1 / device lifecycle.** If Android kills the Activity during long native analysis, work cancellation and crash-stage markers need handset stress testing. The stage journal is diagnostic evidence, not a crash proof.
5. **P2 / preview/export parity.** Color and normalized spatial decisions match, but full export uses native-size pixels, hence fine grain and local pixel relationships can differ from a downscaled preview. Prior same-resolution tile tests do not prove a resized preview is byte-identical to a full export.
6. **P2 / metadata and color.** Export is 8-bit sRGB JPEG, not a lossless or RAW preservation workflow. EXIF/ICC retention, HEIF corner cases and wide-gamut output require targeted device tests.

## Next device test matrix

1. Xiaomi 17 Ultra: repeat Photo Picker select/cancel/share with 12MP JPEG and HEIF, safe default, 20 cycles. Validate no force close, background/foreground transition, and all three styles.
2. Compare preview vs saved JPEG at 100%, 50%, 150%; verify orientation, dimensions and no unrequested overwrites.
3. Try 50MP/100MP originals; passing an explicit low-memory rejection counts as safe behavior, not as completed export capability.
4. Enable full-model mode only after baseline succeeds; attach anonymized diagnostics plus `adb logcat` on crash. Measure peak PSS / Java heap and elapsed time per stage.
5. Conduct same-source image A/B preference review before changing NIMA weights or Director Cut intensity again.

### Build policy

Changes stay on `cinema65-v092-stability-audit`; review CI and real-device logs before merging, publishing or replacing an existing APK. All downloaded research models are fetched from pinned or declared paths in CI; do not commit model binaries accidentally.
