package com.smartlogix.envios.factory;

import com.smartlogix.envios.dto.CreateEnvioRequest;
import com.smartlogix.envios.model.Envio;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

// Implementación concreta 1: Envío Terrestre
@Component("terrestreCreator")
public class EnvioTerrestreCreator implements EnvioCreator {
    @Override
    public Envio crearEnvio(CreateEnvioRequest req) {
        return Envio.builder()
            .pedidoId(req.getPedidoId())
            .tipoEnvio("TERRESTRE")
            .transportista(req.getTransportista())
            .destino(req.getDestino())
            .rutaDescripcion("Ruta terrestre — " + req.getDestino())
            .fechaEstimadaEntrega(LocalDateTime.now().plusDays(5))
            .build();
    }
}
