package com.eia.notifications.domain;

/**
 * One field-level problem, shaped like the `errors[]` entries in SPEC 6.11.
 */
public record FieldViolation(String field, String code, String message) {
}
