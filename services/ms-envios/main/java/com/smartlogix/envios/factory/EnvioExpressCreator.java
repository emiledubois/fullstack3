package com.smartlogix.envios.factory;

import com.smartlogix.envios.dto.CreateEnvioRequest;
import com.smartlogix.envios.model.Envio;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

// Implementación concreta 2: Envío Express
@Component("expressCreator")
public class EnvioExpressCreator implements EnvioCreator {
    @Override
    public Envio crearEnvio(CreateEnvioRequest req) {
        return Envio.builder()
            .pedidoId(req.getPedidoId())
            .tipoEnvio("EXPRESS")
            .transportista(req.getTransportista())
            .destino(req.getDestino())
            .rutaDescripcion("Express prioritario — " + req.getDestino())
            .fechaEstimadaEntrega(LocalDateTime.now().plusDays(1))
            .build();
    }
}
