# CINEMA 65 v0.8.5 · Real generic-object segmentation research

**Date:** 2026-10-27. **Status:** research candidate; **NOT** a stable update, nor evidence of improved final photographic quality.

## Real pretrained model and runtime

- **Model**: U2NETP ONNX from `edgetools/u2netp`, published model hash pinned at `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`.
- **Weight**: 4,574,861 bytes (4.36 MiB). Underlying U²-Net licence Apache-2.0, according to model card. ONNX package source `rembg` distribution; respect attribution and licences.
- Input: `[1,3,320,320]` float32 NCHW RGB normalized to ImageNet mean/std after dividing by input maximum. Output: fused mask `1959`, `[1,1,320,320]` float32. Output is normalized on per-image 2–98% quantiles for **proposal visualization**, not calibrated subject probability.
- GitHub Actions downloaded exact weights, verified SHA-256, performed an actual CPU inference; desktop then repeated actual inference offline via `onnxruntime 1.23.2`. Test assets are **not shared**; the nine local images never needed to be uploaded to GitHub.
- Android research branch uses `onnxruntime-android:1.23.2` with the ONNX weight bundled at build time and pinned. Models do not access any service while processing photos. Android **runtime itself still requires real phone validation**. The model can raise app footprint and native memory use.

## Nine real-photo masks and manual visual assessment

| Image | Subject represented in original | What U2NETP actually highlighted | Gate | Human research note |
|---|---|---|---|---|
| 5363 | Road sign | sign + small disparate patches | Fallback | Useful sign, but too tiny/fragmented to auto-trust |
| 5364 | Wall clock | clock & hands in one region | Accept | Geometrically good |
| 5365 | Nested room doorways | doorway structure / lit interior | Accept | Coherent, but interior highlights can clip |
| 5366 | Corridor and directional window light | single door leaf on right | Fallback | Geometrically plausible but wrong photography priority |
| 5367 | Desert road | road-edge debris and patches | Fallback | Fragmented; not a single object |
| 5368 | Hourglass | glass silhouette and sand | Accept | Geometrically good |
| 5369 | Purple door | door silhouette including window holes | Accept | Geometrically good but sky through holes needs protection |
| IMG_6034 | Canyon and distant people | fragmented rim/rocks | Fallback | No dominant object; do not force subject-based relight |
| IMG_6062 | Narrow old-town alley | small far building highlight | Fallback | Local highlight is not necessarily subject |

This **4/9** accept result is produced by a consciously conservative *heuristic* on a tiny, selected convenience sample. It is **not** benchmark accuracy, test sensitivity, or proof of objective mask correctness. The area cutoff at 6% deliberately omits some true small subjects (the sign); it requires further data before shipping.

## Paired baseline versus U2NETP-guided render

Using only the **actual U2NETP mask**, not synthetic MiDaS depth, Java FilmCore candidate0 was applied at the same source pixel dimensions (max 690 px side). The mean absolute RGB delta between original research rendering and model-guided candidate was:

- 5364 clock: **1.02/255**.
- 5365 doorways: **2.32/255**. Additional 2.0% of pixels were near white vs original when rendering candidate 0: **review highlights before release**.
- 5368 hourglass: **0.29/255**.
- 5369 door: **0.76/255**.
- All five rejected scenes: **zero** change, as required by safe fallback.

These numbers show the model does actually affect the renderer, but the extra light is comparatively weak and cannot be advertised as a perceptual improvement. The v0.8.4 baseline often already creates intense tonal edits. The mask cannot alone infer source direction, cast shadows or art-directed lighting.

## Required next gates, before any public app

1. **Android inference proof**: log ONNX load time, peak memory, CPU latency, mask shape, fraction accepted; test Xiaomi phone and 12MP/80MP cases.
2. **More varied photographs**: collect representative portrait, architecture, object, landscape and low-light imagery; score mask *correctness* separately from visually *appropriate lighting* with blind human inspection.
3. **Repair selection-vs-lighting policy**: model-generated foreground must not automatically lift an already bright doorway or sky within door windows; subject exposure should be budgeted by original illumination with separate shadow-negative-fill risks.
4. **Strong guardrails**: clipped-highlight delta, halos, harsh seams, skin hue, local black loss and near-tie NIMA fallback; keep an undoable A/B and export diagnostics.
5. **No PR claims based on single model score**. A module may be stable technically yet undesirable aesthetically.

## Reproduction

```bash
# In an internet-connected build runner, get the pinned ONNX model and verify hash.
# In a local Python env with numpy / Pillow / cv2 / onnxruntime:
python research/u2netp_probe.py --model research/model_cache/u2netp.onnx
python research/evaluate_u2netp.py research/model_cache/u2netp.onnx research/visual_masks test_5363.jpg ...
# Pure Java algorithm; app source has no Android dependency for gate and film core.
javac -d desktop/out app/src/main/java/com/cinema65/oneclick/{FilmCore,SpatialFusion,CandidateRanker,GenericSubjectGate}.java desktop/*.java
java -cp desktop/out GenericSubjectGateRegressionTest
java -Djava.awt.headless=true -cp desktop/out RealGenericMaskAudit research/paired_audit photo1.jpg mask1.png ...
```

The photographs are omitted from the source archive to avoid redistributing private imagery. The ONNX binary is also not embedded in source ZIP; GitHub Actions retrieves a hash-pinned copy during build.