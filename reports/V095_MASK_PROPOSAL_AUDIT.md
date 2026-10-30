# CINEMA 65 v0.9.5 — U2NETP proposal review, no artistic algorithm changes

Evidence on Xiaomi 17 Ultra (2026-10-27): 3 ONNX calls succeeded in 255–271 ms.
High-coherence U2NETP masks were rejected solely because their photo coverage
was 5.7%, 3.7%, and 3.1%, less than GenericSubjectGate's 6.0% minimum.
Do not assume high connectedness establishes photographer intent.

## What changes
- Store *all* U2NETP proposals separately as `Guidance.genericProposal` (48x48 floats);
  accepted masks alone may set `Guidance.subject` and `hasGeneric`.
- New choice in diagnostics ③: **U2NETP 候选蒙版（仅诊断）**.
  Rejected proposals are never eligible for grade, style budgets, or export.
- The gate's min-area acceptance rule remains exactly 6%. The reason string
  explicitly states when a mask is rejected for that threshold rather than incorrectly
  suggesting it is fragmented.
- Preserve ORT Android 1.30.0 and v0.9.4 Director Cut black/white clipping protection.
- Distinct debug Android applicationId to avoid overwriting older versions.

## How to test
1. Enable full model + U2NETP independently, select same three scenes.
2. In ③ choose "U2NETP 候选蒙版（仅诊断）"; check whether mask is on the intended
   foreground and whether there is spatial distortion (model input is 320x320
   stretched, particularly important for 9580x5389 landscapes).
3. Compare ③ "人物 / 主体蒙版": this SHOULD be unavailable for rejected masks;
   this is an intentional safety fallback.
4. Export remains an independent 8-bit JPEG path, not tested by mask audit.

## Caveats
- This build reveals a potential mask for human validation but does not establish
  that its geometry, object identity, or semantic goal is correct.
- No change to the 6% trust threshold pending labelled photo evidence.
- Do not conflate successful preview analysis of 50MP images with full-size export.
