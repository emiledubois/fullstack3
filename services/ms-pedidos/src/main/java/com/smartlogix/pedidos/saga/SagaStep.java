package com.smartlogix.pedidos.saga;

/**
 * Interfaz que define el contrato de cada paso de la Saga.
 *   execute()    -> realiza la acción del paso
 *   compensate() -> revierte la acción si un paso posterior falla
 */
public interface SagaStep {
    String getName();
    void execute(SagaContext ctx) throws SagaStepException;
    void compensate(SagaContext ctx);  // no lanza excepción — absorbe errores internamente
}
