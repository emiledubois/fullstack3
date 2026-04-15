package com.smartlogix.envios.factory;

import com.smartlogix.envios.dto.CreateEnvioRequest;
import com.smartlogix.envios.model.Envio;
import org.springframework.stereotype.Component;
import java.util.Map;

// Fábrica principal — RT-03: Factory Method obligatorio
@Component
public class EnvioFactory {

    private final Map<String, EnvioCreator> creators;

    // Spring inyecta todos los beans que implementan EnvioCreator
    public EnvioFactory(Map<String, EnvioCreator> creators) {
        this.creators = creators;
    }

    public Envio crearEnvio(CreateEnvioRequest req) {
        String tipo = req.getTipoEnvio() != null
            ? req.getTipoEnvio().toUpperCase() : "TERRESTRE";

        EnvioCreator creator = switch (tipo) {
            case "EXPRESS" -> creators.get("expressCreator");
            default        -> creators.get("terrestreCreator");
        };

        return creator.crearEnvio(req);
    }
}
