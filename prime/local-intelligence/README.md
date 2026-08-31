# local-intelligence

Grounded proposal layer for Cortex Prime.

This milestone deliberately starts with a deterministic baseline before any learned model is trusted with state:

- extracts conversation/person labels from captured notification evidence
- links repeated evidence into local conversation threads
- proposes task-like signals only when explicit cue phrases exist
- detects coarse temporal hints
- never writes canonical Cortex state
- every proposal carries evidence ids

The baseline becomes the golden comparator for the later Extractor and Linker model adapters. Learned models must beat it on the Cortex test corpus without increasing unsupported proposals.
