package com.smartlogix.envios.factory;

import com.smartlogix.envios.dto.CreateEnvioRequest;  
import com.smartlogix.envios.model.Envio;

// Interfaz base del Factory Method
public interface EnvioCreator {
    Envio crearEnvio(CreateEnvioRequest req);
}
