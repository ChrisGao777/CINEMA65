# v0.9.0 · Director’s Cuts: distinct photographic hypotheses

**2026-10-27 | Independent research | No APK release.**

## Why change approach?

v0.8.9's additional coordinated strategy + background color budget changed mean RGB by ~0.075/255 across 12 accepted candidate comparisons. It was objective evidence of low impact, not a significant image-quality uplift. Real, stage-level progress requires diverse *render hypotheses* that human observers can distinguish. Previous safety limits remain relevant, but their presence alone cannot establish artistic success.

## Hypotheses and evidence boundaries

1. Subject/background separability: trust-gated U2NETP only, subject lift moderated by original brightness and measured lit field, surround adjustment anchored to segmented geometry. May change only images with accepted generic-object masks; no arbitrary radial vignette.
2. Natural measured lighting: analyze 48×48 original low- and wide-scale illumination, accentuate pre-existing lit midtone regions, deepen some already-shaded midtones; no cast-shadow synthesis. Allows scene-wide effect where no object mask exists, but can still emphasize an irrelevant high-contrast part.
3. Color narrative: cool shadow / warm existing bright surfaces with scene-dependent contrast and semantic background color density, preserving obvious sky and skin-like surfaces as far as simple pixel tests can establish.

`DirectorCuts` operates on the existing final v0.8.9 output, with original photograph as read-only luminance evidence. Deterministic map generation occurs once and final per-pixel transforms are tile-stable. Zero effect if gain=0. Cut 0 is identity when no trusted segmentation. ModelRiskAuditor is applied against the identical baseline and the original source image; fallback is a valid result.

## Real-data evaluation (seven scenes, 21 renders)

| Cut | Mean RGB absolute delta vs v0.8.9 (0–255) | Range | Risk checks |
|---|---:|---:|---|
| Subject/background | 0.567 | 0–1.874 | 0 incremental new black/white; 0 new hard edges measured |
| Natural measured light | 2.612 | 1.792–4.102 | no incremental clipped black/white; largest additional hard-edge rate 0.000070 |
| Warm/cool narrative | 2.136 | 1.348–2.567 | no incremental clipped black/white; 0 additional hard edges in this sample |

All 21 variants passed gain=1.0 safety criteria and were not attenuated. Mean delta is not a proxy for attractiveness; cut 0's mean includes three no-semantic exact baselines. These are seven very limited photographs, five being screenshot-derived low-res references with baked-in labels. Separate photographic preference evaluation is mandatory.

### What counts as progress

- Clearly separated render hypotheses: not merely rescaled baseline sensitivity.
- A local offline user preference survey that includes the option to preserve the original grade.
- Explicit model attribution: optional masks only for a subset of photos; measured lighting-only alternatives for remaining scenes.
- No drop-in alteration of the stable Android application's established default output.

### Remaining risks

- Perceptual distinctiveness does **not** mean human preference gain. Clock, doorways and hourglass can still need photographer-guided framing priority.
- Shadow and color cues are inferred from SDR RGB only. A clipped or backlit JPEG does not contain physically recoverable HDR radiance.
- Mask confidence is geometric rather than an artistic-subject probability. Real skin segmentation is not tested in this set.
- True RAW/large JPEG export and Android runtime have not been profiled for the new layer. Legacy mutable full-size decode remains the main large-photo memory risk.
- Contrast enhancement can cause inconsistent color if user preferences differ by scene. Do not train or deploy automatic preference based on just seven photographs.

## Human preference protocol

Open the offline HTML. For each photograph, switch between old base and three new hypotheses with old/base slider option. Pick one preferred outcome or preserve the baseline. Export `CINEMA65-v090-personal-preferences.json`; record future scenes, split by subject, and estimate win rates with a forced-choice baseline option. Do not treat NIMA score differences <0.1 as resolved preferences.

## Files

- Java: `app/src/main/java/com/cinema65/oneclick/DirectorCuts.java`
- Tests: `desktop/DirectorCutsRegressionTest.java`, `desktop/DirectorCutsAudit.java`
- CSV: `research/director_ab/audit.csv`
- Review contact: `research/v090_director_cuts_contact.jpg`
- Offline interactive reviewer: `research/CINEMA65-v0.9.0-DirectorCuts-offline-review.html`