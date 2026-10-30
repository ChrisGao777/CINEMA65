# CINEMA 65 v0.9.0 — Director's Cuts Research

**Status: independent research / NOT RELEASED as an APK.** Extends v0.8.9 without altering its existing rendering behavior. v0.8.2 remains the user's stable installed test build.

## What changed

After v0.8.9 produced only ~0.075/255 extra mean RGB difference, continued microscopic safety-factor tuning was not a useful path toward a recognizably stronger visual style. This branch builds three **opt-in editorial finish candidates** on the identical v0.8.9 render:

- `DirectorCuts` cut 0: **Subject & surroundings**. Requires a trusted U2NETP segmentation. Lightly recover underexposed subject regions *only where measured light exists*, and selectively recess **nearby**, originally visible background. Exact no-op if no credible subject mask.
- cut 1: **Existing-light direction**. Enhance pre-existing illuminated and shadowed surfaces. Works without a semantic object; no simulated hard shadows or invented light source.
- cut 2: **Warm/cool narrative**. Conservative contrast and photographic split toning by recorded light/shadow; optional local background chroma reduction for accepted semantic object.

Distinct images, not just three names for a single attention map. The module is separate from `FilmCore` so the former stable rendering path is unchanged and still supports exact control comparisons. The three new cuts are NOT yet exposed in the Android production screen; they are generated for research offline.

## Reproducible experiment

- Photos: 5 original **BEFORE-only** crops of historical references (the screenshots include baked-in compression and words), and 2 privately supplied Morocco JPEG photos. All images evaluated locally; nothing is uploaded to GitHub.
- Before masks: genuine pretrained U2NETP 320x320 ONNX result; independent 320x320 and 48x48 geometrical gates, no fabricated subject for rejected masks.
- Baseline: render using `FilmCore.candidate(..., a.selectedTreatment)` from the same original before applying `DirectorCuts`.
- New cut results: every single proposed image compared with matching baseline. ModelRiskAuditor new clipping/artificial edges budget must pass (gain search 1.0, 0.7, 0.4, 0), else fallback.
- All 21 candidates passed thresholds with gain 1.0 on the seven tested scenes. This does **not** imply a human artistic preference win.

Aggregate mean *absolute RGB delta relative to identical v0.8.9 baseline*:

| hypothesis | 7-image mean delta /255 | min-max | validity |
|---|---:|---:|---|
| Subject & surroundings | 0.567 | 0–1.874 | 3 out of 7 no more than fallback; no trusted object -> exactly unchanged |
| Existing-light direction | 2.612 | 1.792–4.102 | all 7 scenarios measured |
| Warm/cool narrative | 2.136 | 1.348–2.567 | all 7 scenarios measured |

These deltas are considerably more measurable than v0.8.9's 0.075 *incremental* color/strategy tweak, but the methodologies are not identical and the values are NOT subjective quality scores. Main risk left: a non-semantic cue can still focus on wrong visual content; scene direction must be user-reviewed.

## Local review

Open **`research/CINEMA65-v0.9.0-DirectorCuts-offline-review.html`** without a server or internet. The page embeds only reduced-size JPEG versions of the seven original photographs. Choose each scene, flip between baseline and three candidates, drag before/after slider, and vote for the best (including baseline). Export a JSON of anonymous scene IDs and the chosen cut; no cloud endpoint or network request exists in this HTML.

`research/v090_director_cuts_contact.jpg` previews all 7×5 combinations. `research/director_ab/audit.csv` contains per-image risk/strength metrics.

## Core desktop verification

```
mkdir -p desktop/out
javac -encoding UTF-8 -d desktop/out app/src/main/java/com/cinema65/oneclick/{DirectorCuts,FilmCore,ToneSafety,SpatialFusion,ExposureAllocator,EnvironmentSculptor,StrategyDirector,ColorDensityBudget,CandidateRanker,ModelRiskAuditor,GenericSubjectGate}.java desktop/*.java
java -cp desktop/out DirectorCutsRegressionTest
```

Run `DirectorCutsAudit` with `outputDirectory (beforePhoto mask48 mask320)+` where a missing pair is represented by two `-` arguments. Regression tests verify exact disabled state, no semantic fallback, and deterministic per-pixel/tile identity. The Android compilation workflow verifies dependencies can compile with the new class but does NOT verify any phone UX or new cut inference latency. An original-size photo is still fully decoded in legacy export and can exhaust memory on 80–100MP photos.

## Future gate for real release

Obtain *blinded human preference votes* (baseline allowed), ideally on ≥30 high-res user originals representing portrait / architecture / landscape / high-key / night. Report wins and vetoes per scene instead of a combined generic NIMA score. Integrate only the winning look logic behind a preview switch, and evaluate Android CPU performance, full-size export and halo risk before merging into stable.