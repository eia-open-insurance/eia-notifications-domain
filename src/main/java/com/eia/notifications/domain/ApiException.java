package com.eia.notifications.domain;

import java.util.List;

/**
 * Domain validation or business-rule failure that maps to RFC 9457 problem JSON.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> errors;

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of());
    }

    public ApiException(ErrorCode errorCode, String detail, List<FieldViolation> errors) {
        super(detail);
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> errors() {
        return errors;
    }
}
