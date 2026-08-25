# Cortex Final Update Gate — 2026-08-25

This integration branch is the single release-candidate line for the next Cortex update.

## Product invariants
- Every primary user-facing result surface has a micro-level model proposal pass.
- Proposals are content-specific and model-generated; zero proposals is valid.
- Fixed legacy pseudo-suggestions are not presented as AI proposals.
- External mutations remain preview / explicit approval first.
- Cloud proposal routing obeys source-level CloudEvidencePolicy; private results fall back to the local model.
- OX Alpha / OpenRouter remains the primary external reasoning route, with Gemini fallback and local Qwen fallback.
- The authoritative capability registry remains exactly 43 entries.

## Final PRIME surfaces
- Input → ProposalCaptureActivity → ProposalCaptureResultActivity
- Brief → ProposalBriefActivity
- People / Projects → ProposalPeopleProjectsActivity
- Brain → ProposalAskCortexActivity
- Quick Voice tile, Understand Screen tile, Android Share and widget setup converge on the same proposal-aware capture/result path.

## UI tokens
- Graphite near-black surfaces
- Icy blue / periwinkle primary cognition and navigation accent
- Soft violet decisions / reasoning
- Amber waiting / pending
- Sage confirmed / useful
- Coral red only for recording / urgent / destructive signal

## Regression gate
`termux-build-cortex.sh` runs `scripts/cortex-repo-audit.sh` before Gradle. The audit scans every tracked source/configuration file for unresolved merge markers and verifies restore, manifest, proposal routing, privacy, entry points, UI color semantics and the exact 43-capability contract. Gradle remains the authoritative compile/resource/type gate.

No APK is considered final until the repository audit passes, the clean Termux compile succeeds, and runtime smoke validation passes over the installed existing Cortex data.
