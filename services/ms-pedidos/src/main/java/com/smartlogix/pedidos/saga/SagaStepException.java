package com.smartlogix.pedidos.saga;

public class SagaStepException extends RuntimeException {
    private final String stepName;

    public SagaStepException(String stepName, String message, Throwable cause) {
        super(message, cause);
        this.stepName = stepName;
    }

    public String getStepName() { return stepName; }
}
