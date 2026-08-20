# Cortex v0.5 — Vision

Vision turns screenshots/images into structured memory rather than storing pixels only.

Pipeline:
1. Latin OCR with ML Kit Text Recognition v2.
2. Arabic fallback with Tesseract 5 via Tesseract4Android when Arabic is likely or Latin OCR is weak.
3. Local screenshot/document classifier.
4. Structured field extraction for chats, receipts, settings, products, documents and AI results.
5. Persisted vision metadata and semantic re-indexing.

Privacy: recognition and classification run on-device. The Arabic traineddata file is downloaded once from the official tesseract-ocr/tessdata_fast 4.1.0 release and kept in Cortex private storage.
