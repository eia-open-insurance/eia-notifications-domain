package com.eia.notifications.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Lifecycle of a notification template. A template is created as DRAFT, may be
 * approved into ACTIVE, and is retired when it must no longer issue
 * notifications (NTF-14).
 */
public enum NotificationTemplateStatus {
    DRAFT,
    ACTIVE,
    RETIRED;

    public static NotificationTemplateStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_TEMPLATE_STATUS,
                    "Template status '" + value + "' is not one of: " + Arrays.stream(values())
                            .map(Enum::name)
                            .collect(Collectors.joining(", ")) + ".");
        }
    }
}
