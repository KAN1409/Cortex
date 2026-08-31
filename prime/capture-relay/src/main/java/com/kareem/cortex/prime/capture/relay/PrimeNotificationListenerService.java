package com.kareem.cortex.prime.capture.relay;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

/**
 * Observation-only notification listener embedded inside Cortex Prime.
 * It normalizes Android notifications and persists immutable evidence locally.
 */
public final class PrimeNotificationListenerService extends NotificationListenerService {
    private EvidenceSqliteStore evidenceStore;

    @Override
    public void onCreate() {
        super.onCreate();
        evidenceStore = new EvidenceSqliteStore(this);
        RelayCaptureRuntime.installSink((evidence, observation) -> evidenceStore.append(evidence));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!RelayCaptureRuntime.isCaptureEnabled()) return;
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;

        NotificationObservation observation = NotificationNormalizer.from(sbn);
        RelayCaptureRuntime.submit(RelayEvidenceMapper.toEvidence(observation), observation);
    }

    @Override
    public void onDestroy() {
        RelayCaptureRuntime.resetSink();
        if (evidenceStore != null) {
            evidenceStore.close();
            evidenceStore = null;
        }
        super.onDestroy();
    }
}
