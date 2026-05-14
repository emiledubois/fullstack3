package com.smartlogix.pedidos.saga;

/**
 * Interfaz que define el contrato de cada paso de la Saga.
 * execute() realiza la acción; compensate() la revierte.
 * SagaContext es mutable y se va enriqueciendo entre pasos.
 */
public interface SagaStep {
    String getName();
    void execute(SagaContext ctx) throws SagaStepException;
    void compensate(SagaContext ctx);
}
