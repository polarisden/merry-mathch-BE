package com.fsd10.merry_match_backend.exception;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException() {
        super("Plan not found");
    }

    public PlanNotFoundException(String message) {
        super(message);
    }
}

