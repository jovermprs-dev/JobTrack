package com.jobtrack.common;

public class TooManyAttemptsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
