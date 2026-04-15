package com.smartlogix.envios.factory;

// Interfaz base del Factory Method
public interface EnvioCreator {
    Envio crearEnvio(CreateEnvioRequest req);
}
