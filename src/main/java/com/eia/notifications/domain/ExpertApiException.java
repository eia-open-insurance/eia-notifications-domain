package com.eia.notifications.domain;

/**
 * Error propagated from the expert's RFC 9457 response. The domain forwards the
 * expert's status and stable code so callers see one contract regardless of
 * which tier rejected the request.
 */
public class ExpertApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final String title;
    private final String category;
    private final boolean retryable;

    public ExpertApiException(int status, String code, String title, String detail, String category,
                              boolean retryable) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.category = category;
        this.retryable = retryable;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
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
