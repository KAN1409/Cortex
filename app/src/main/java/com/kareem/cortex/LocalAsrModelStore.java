package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import java.io.File;

/**
 * Compatibility shim kept temporarily so older UI/test code can migrate without
 * blocking capture. Production transcription is cloud-only as of v1.0.2.
 */
@Deprecated
public final class LocalAsrModelStore {
    public static final String MODEL_FILENAME="disabled-cloud-asr";
    private LocalAsrModelStore(){}

    /** Voice analysis is always ready because it no longer needs a local model. */
    public static boolean ready(Context c){return true;}
    public static String profileId(Context c){return "cloud_gpt_transcribe";}
    public static String profileLabel(Context c){return "Cortex Cloud ASR";}
    public static String statusText(Context c){return "Cloud ASR active • no local model required";}

    /** Legacy API intentionally disabled: Cortex no longer imports ASR models. */
    public static File importModel(Context c,Uri uri){
        throw new UnsupportedOperationException("Local ASR model import was removed; Cortex now uses cloud transcription");
    }

    /** Legacy path only; no production code reads or executes this file. */
    public static File modelFile(Context c){return new File(c.getFilesDir(),MODEL_FILENAME);}

    /** Retained only so archived GGML unit tests continue documenting the old file format. */
    static boolean isGgmlHeader(byte[] h){return WhisperGgmlModel.hasGgmlMagic(h);}
}