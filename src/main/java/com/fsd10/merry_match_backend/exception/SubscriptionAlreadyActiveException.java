package com.fsd10.merry_match_backend.exception;

public class SubscriptionAlreadyActiveException extends RuntimeException {
    public SubscriptionAlreadyActiveException(String message) {
        super(message);
    }
}
