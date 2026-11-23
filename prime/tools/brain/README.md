# Brain asset

`render_brain.py` renders the glass-brain frames (spec §7.3) from the fsaverage5 cortex surface
bundled with the free `nilearn` package, plus a synthetic cerebellum and brainstem.
`app/src/main/res/raw/brain_loop.webp` is those 24 frames, alpha-extracted, resized to 360×300 and
played forward then backward at 230 ms per frame.

Licence: the fsaverage template's licence for redistribution in an app must be confirmed before
release (spec §14.3). If it doesn't allow it, swap the mesh for a CC0/CC-BY brain model and re-render.
Run with `pip install nilearn scipy pillow` and then `python render_brain.py`.
