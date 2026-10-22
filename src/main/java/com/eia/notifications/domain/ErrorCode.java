package com.eia.notifications.domain;

import org.springframework.http.HttpStatus;

/**
 * Domain error codes. They use the same SPEC Appendix C scheme as the expert so
 * a code means the same thing across the vertical.
 */
public enum ErrorCode {
    NOT_FOUND("EIA-NTF-9001", HttpStatus.NOT_FOUND, "Resource not found", "NOT_FOUND", false),
    TEMPLATE_REQUIRED("EIA-NTF-9201", HttpStatus.BAD_REQUEST, "Approved template required", "BUSINESS_RULE", false),
    SEND_BEFORE_SCHEDULED("EIA-NTF-9301", HttpStatus.BAD_REQUEST, "Notification is not due", "BUSINESS_RULE", false),
    INVALID_STATUS_TRANSITION("EIA-NTF-9302", HttpStatus.BAD_REQUEST, "Invalid status transition", "CONFLICT", false),
    INVALID_CHANNEL("EIA-NTF-9401", HttpStatus.BAD_REQUEST, "Invalid notification channel", "VALIDATION", false),
    INVALID_STATUS("EIA-NTF-9402", HttpStatus.BAD_REQUEST, "Invalid notification status", "VALIDATION", false),
    INVALID_NOTIFICATION_TYPE("EIA-NTF-9403", HttpStatus.BAD_REQUEST, "Invalid notification type", "VALIDATION", false),
    VALIDATION_FAILED("EIA-NTF-9404", HttpStatus.BAD_REQUEST, "Request validation failed", "VALIDATION", false),
    INVALID_TEMPLATE_VERSION("EIA-NTF-9405", HttpStatus.BAD_REQUEST, "Invalid template version", "VALIDATION", false),
    MALFORMED_REQUEST("EIA-NTF-9406", HttpStatus.BAD_REQUEST, "Malformed request", "VALIDATION", false),
    INVALID_TEMPLATE_STATUS("EIA-NTF-9408", HttpStatus.BAD_REQUEST, "Invalid template status", "VALIDATION", false),
    TENANT_HEADER_REQUIRED("EIA-NTF-9501", HttpStatus.BAD_REQUEST, "Tenant header required", "VALIDATION", false),
    INVALID_TENANT("EIA-NTF-9502", HttpStatus.BAD_REQUEST, "Invalid tenant header", "VALIDATION", false),
    INVALID_ARGUMENT("EIA-NTF-9503", HttpStatus.BAD_REQUEST, "Invalid argument", "VALIDATION", false),
    EXPERT_UNAVAILABLE("EIA-NTF-9601", HttpStatus.BAD_GATEWAY, "Notifications expert unavailable", "DEPENDENCY", true),
    INTERNAL_ERROR("EIA-NTF-9999", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "INTERNAL", false);

    private final String code;
    private final HttpStatus status;
    private final String title;
    private final String category;
    private final boolean retryable;

    ErrorCode(String code, HttpStatus status, String title, String category, boolean retryable) {
        this.code = code;
        this.status = status;
        this.title = title;
        this.category = category;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }
}
