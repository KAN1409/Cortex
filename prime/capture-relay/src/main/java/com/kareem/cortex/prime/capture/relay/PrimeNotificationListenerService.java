package com.kareem.cortex.prime.capture.relay;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Observation-only notification listener embedded inside Cortex Prime.
 * It normalizes Android notifications and hands immutable evidence to a process-local sink.
 */
public final class PrimeNotificationListenerService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!RelayCaptureRuntime.isCaptureEnabled()) return;
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;

        NotificationObservation observation = NotificationNormalizer.from(sbn);
        RelayCaptureRuntime.submit(RelayEvidenceMapper.toEvidence(observation), observation);
    }
}
