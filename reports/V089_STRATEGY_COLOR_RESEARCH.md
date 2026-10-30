# CINEMA 65 v0.8.9 · Strategy coordination & local chromatic density

Status: **research candidate; Java regression validated; Android CI pending**.

## Problem

v0.8.8 added physically constrained local exposure budgets, but per-candidate multipliers were fixed inside the pixel renderer, and color grading of surroundings was primarily globally determined. There was no independent switch to measure a new color-control hypothesis.

## Implemented

1. `StrategyDirector.plan(...)` combines semantic confidence, depth credibility, original subject brightness and nocturnal-scene protection. The three candidate grades remain alternative choices rather than unconditionally additive strategies. Previous values are multiplied conservatively; the existing light field, highlight reserve, ModelRiskAuditor and tone-safety remain authoritative.
2. `ColorDensityBudget.compute(...)` creates a smooth low-resolution map of eligible *background chroma*. Original photo RGB feature means feed it, not RGB from processed renders. The actual edit is a bounded blend toward the final pixel's weighted luma; sky, prelit/near-white and subject interiors are excluded. This is *not* a white-balance correction or global saturation knob.
3. U2NETP is still an ONNX CPU-provided mask. Salient-object decisions are not equivalent to subjective intent. For the original 320×320 float inference mask, connected component checks now run **before** preview downsampling. A second 48×48 check is retained.
4. Diagnostics include strategy-plan summary, color budget map, and 3-way exact ablations: v0.8.8, strategy only, strategy plus color at the **same treatment, same photo, same tone and same risk-gate gain**.

## Evaluation protocol and quantitative results

Seven historic reference screenshots were separated into their **BEFORE half only** before running ONNX. This avoids grading a screenshot containing both the original and the reference After. Two previously supplied original photos were also included. Raw grayscale PNG values are read via Raster samples, rather than Java's color-converted `getRGB`, to prevent mask false positives in the desktop pipeline. High-resolution (320²) and low-resolution (48²) mask gates are evaluated independently. The actual Android ONNX adapter never used gamma-converted PNG mask data.

- 9 photos; 4 have accepted geometric object proposals, 5 reject and return pixel-exact v0.8.8 output; 27 grade comparisons (3 per image).
- Accepted 12 comparisons: mean absolute RGB v0.8.9 vs v0.8.8 = **0.0748 / 255**; strategy component **0.0562/255**, color component **0.0192/255**.
- Background mean chroma spread changes **-0.0674 / 255** (over all background pixels); the effect is intentionally restrained and near-field only.
- Additional measured hard clipping (new near-black / near-white): **0** in all 27 limited tests. Edge artifact delta is very small (a few millionths of sampled neighboring pairs).
- This modest pixel change is **not an aesthetic improvement claim**. NIMA has not been calibrated to the user's subjective preference and image quality still requires human side-by-side judgement.

Visual contact: `research/before_ab/v089_before_only_contact.jpg` (final diff magnified ×12; screenshot-derived sources are lower-quality references). Full `research/before_ab/audit.csv` has per-image/per-treatment metrics.

## New bugs caught

- Synthetic portrait-background test initially softened the **inside** of a person/object mask at boundaries; fixed by an independent full-resolution interior guard.
- Synthetic bright sky originally changed due to insufficient sky exclusion; fixed and regression tested.
- Raw mask PNG (`getRGB`) was subject to colorspace conversion, giving incorrect desktop coverage. Fixed **in this audit**, with original ONNX 320x320 float gate now also checked in the Android adapter. Previous desktop segmentation-gate statistics should be revalidated before being used for tuning.

## Model/code boundaries

Only model masks and local relative depth guide the deterministic grade. NIMA still selects among actual candidates with cautious tie logic. The scene strategy module does not estimate where photons should originate and cannot synthesize plausible hard cast shadows or an HDR reconstruction. Tone safety, background illumination preservation and the strong original-look style remain independently tested.

## Remaining blockers

- Strong aesthetic uplift is not yet demonstrated; the new edits are objectively tiny in most examples.
- Need independently annotated subject masks and original RAW/JPEG files for high-quality testing; historic screenshots alone have constraints.
- Model rollout and memory/latency profiling required on Xiaomi 17 Ultra; build success alone is insufficient.
- Large-photo `ImageDecoder` currently creates an entire mutable ARGB bitmap, a memory risk for 80–100 MP images.

## Next step

Shift from adding complexity to a **proper paired preference evaluation**: at least 30 diverse high-resolution original photos, ground-truth user ranking of v0.8.8 vs three new variants, plus subject/sky false-light checks. Keep candidate only if image preference gains are reproducible without edge/highlight regression.