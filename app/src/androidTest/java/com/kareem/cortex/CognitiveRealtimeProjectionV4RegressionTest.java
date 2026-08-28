package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveRealtimeProjectionV4RegressionTest {
    @Test public void richerConnectorTextWinsMemoryProjection(){
        String base="Kareem Abdel Nasser";
        String connector="Kareem Abdel Nasser CORTEX_E2E_001 يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده";
        assertEquals(connector,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }

    @Test public void explicitConnectorRequestWinsEvenWhenShorter(){
        String base="Conversation update with several ordinary words and no responsibility or request at all";
        String connector="محتاج منك تبعتلي الملف";
        assertEquals(connector,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }

    @Test public void weakerConnectorTextDoesNotReplaceUsefulMemory(){
        String base="محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده";
        String connector="حاضر";
        assertEquals(base,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }
}
