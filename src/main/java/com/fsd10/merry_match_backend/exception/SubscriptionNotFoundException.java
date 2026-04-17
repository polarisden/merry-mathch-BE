package com.fsd10.merry_match_backend.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException() {
        super("Subscription not found");
    }
}
