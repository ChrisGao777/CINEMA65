# CINEMA 65 · v0.8.8 Environment Sculpt Research

**Status:** experimental source only; no public APK release. **Date:** 2026-10-27.

## 1. Starting point

v0.8.7 preserved scene-dependent highlights/shadows and v0.8.6 separated foreground dodge and distant background burn, but a high-key hourglass passed segmentation yet got virtually no model-specific emphasis: its glass was bright enough and exposure protection rightly forbade further lift. The missing option was *environment-first emphasis*.

## 2. Algorithm

`EnvironmentSculptor.compute()` adds a fourth, separately measured spatial signal:

1. Require credible **semantic** object evidence (`semanticTrust > 0.35`), mask coverage 0.6–58%, and a lit foreground (`subject mean L* ≥ about 61`, with smooth saturation near 69). Depth-only guidance cannot turn on background sculpting.
2. Gaussian-diffuse the binary-like object mask at the 48-cell spatial lattice. Use only the *exterior*, and maintain a soft, object-relative nearby context. No center-radius vignette and no added cast-shadow geometry.
3. Permit only midtone/highlight-surplus **ambient surfaces** (locally and broadly exposed); never depress already-dark surroundings. This is a budget, not a pixel-valued exposure instruction.
4. `FilmCore.pixel()` uses source-pixel blue-sky and cyan guards, per-pixel luminous protection, the skin mask, and the exact same rendering/gamut safeguards as v0.8.7. Independent style weights are 5.0/7.4/8.2 in original FilmCore L* units for styles 0/1/2.
5. The whole environment effect is under `guidanceGain` (existing quality gate) *and* `environmentStrength` (exact A/B switch 0..1); `EnvironmentSculptor` can be audited independently from classic dodge/burn and tone-safety.

**Important:** a generated object mask cannot determine lighting physics. This method does not synthesize cast shadows, add lamps, relight specular reflections, or recover clipped details.

## 3. Desktop regression

Added `EnvironmentSculptRegressionTest`, checking:
- Bright rectangular foreground causes **localized** background emphasis but nearly no extra subject brightening.
- Dark foreground cannot trigger environment-first darkening even when bright background contaminates the spatial illumination estimate; threshold tightened after detecting this regression in development.
- Blue/cyan sky rejects background-context darkening.
- No valid foreground mask gives **byte-identical** previous output.
- Whole-frame and striped processing are **pixel-identical** with guided environment enabled.

The previous 12+ plain-Java regression tests also pass, including ToneSafety, SpatialFusion, EdgeSafety, SkyLeak, ExposureAllocator, and ModelRiskAuditor. These checks do **not** establish Android runtime performance or photographic preference.

## 4. Nine true U2NETP model masks, 27 comparison cases

Rebuilt actual U2NETP masks offline from the 7 historical reference JPG screenshots and two Moroccan stills. Model was from the pinned, SHA-256-verified ONNX resource used in v0.8.5. Each case used the same source, gate, selected model gain, treatment and tone-safety; **only** environmentStrength changed from 0 to 1. `desktop/RealEnvironmentAudit.java` generated `research/environment_ab/audit.csv`.

- The semantic gate accepted **four** model masks (clock, nested doorways, hourglass, purple door); five rejected scenes were pixel-identical fallback.
- Brightness evidence allowed environment-specific adaptation **only for hourglass** (image `test_5368.jpg`). All three styles showed measurable, restrained extra changes; the other 24 cases remained byte-identical.
- Hourglass subject estimated L* **87.1**; context budget mean **0.130**; semantic trust **0.90**; existing ModelRiskAuditor model gain **1.00**.
- In style 2 (Color Narrative), environment-on relative to environment-off: average RGB delta **1.2859/255**; foreground mean luma difference **-0.1028/255**, background **-1.5447/255**. There were **zero detected incremental near-black, near-white, new hard-edge or edge-reversal events** according to current heuristics. This does not imply zero perceptual defects.
- This is a small, hand-selected sample, so **not** a benchmark or a proof of aesthetic superiority. A reference screenshot includes the word “Before” and is not a native high-bit-depth photo; true full-size camera images are still required to validate the artistic result.

**Contact sheet:** `research/environment_ab/v088_environment_fiveway.jpg`. The difference image is amplified ×16 and must not be interpreted as literal correction strength.

## 5. Risks and limitations

1. **Foreground mean from blurred scene illumination:** this can be biased toward bright background. Synthetic tests caught false triggering on a dark subject. Threshold was strengthened; needs further mixed-light portrait tests.
2. **Bright colored backgrounds / translucent subjects:** sky/cyan and already-dark protections reduce risk; real stained glass, purple façades and reflective water can still need different photographic policies.
3. **Very gentle result:** difference is around 1–1.5 RGB levels for current hourglass test. More aggressive shadowing would be trivially possible but would risk fake vignette and unwanted image deformation.
4. **No built-in saliency certainty:** geometric U2NETP acceptance remains a convenience heuristic, not validated subject accuracy. If the wrong subject passes a gate, safe spatial editing can still tell a wrong photographic story.
5. **Android size/memory:** ONNX Runtime, six+ models, and original-resolution full decode still need real-device memory instrumentation. This stage does not solve the large-bitmap decode issue.

## 6. Next research criterion

Do not simply increase the environment coefficient. First build an A/B preference set with native-resolution stills of: lit glass objects, bright plaster objects, backlit human faces, reflective metallic subjects, overcast architecture, natural wide scenery, night interiors, and cyan sky. The model should show consistent, objective protection of clipped areas and acceptable lighting transitions. Then decide whether to add an **ambient color-density** hypothesis independent of exposure while guarding skin and local reflections. A preference signal should use *blind, randomized paired review*, not just NIMA score.