package org.mediaecosystem.experimental.platformproof.model;

import java.util.Set;

public final class EvidencePolicy {
    public static final Set<String> PROHIBITED_FIELDS = Set.of(
            "raw_document_uri",
            "volume_id",
            "volume_uuid",
            "account_name",
            "wifi_ssid",
            "installed_applications",
            "serial_number",
            "advertising_id",
            "personal_path",
            "media_library"
    );

    private EvidencePolicy() {}

    public static FormatDisposition sessionDisposition(
            boolean started,
            boolean completed,
            boolean failed,
            boolean enoughEvidence
    ) {
        if (!started) {
            return FormatDisposition.NOT_RUN;
        }
        if (failed) {
            return FormatDisposition.FAILED;
        }
        if (!completed || !enoughEvidence) {
            return FormatDisposition.INCONCLUSIVE;
        }
        return FormatDisposition.PASSED;
    }

    public static boolean relinkMaySucceed(boolean explicitPickerAction, boolean markerValidated) {
        return explicitPickerAction && markerValidated;
    }
}
