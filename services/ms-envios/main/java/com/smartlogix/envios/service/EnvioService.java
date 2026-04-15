package com.smartlogix.envios.service;

import com.smartlogix.envios.dto.*;
import com.smartlogix.envios.factory.EnvioFactory;
import com.smartlogix.envios.model.Envio;
import com.smartlogix.envios.repository.EnvioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class EnvioService {

    private final EnvioRepository envioRepository;
    private final EnvioFactory envioFactory;

    public EnvioDTO crearEnvio(CreateEnvioRequest req) {
        // Factory Method crea el tipo correcto de Envio
        Envio envio = envioFactory.crearEnvio(req);
        return EnvioDTO.from(envioRepository.save(envio));
    }

    public List<EnvioDTO> getAll() {
        return envioRepository.findAll().stream()
            .map(EnvioDTO::from).collect(Collectors.toList());
    }

    public EnvioDTO getById(Long id) {
        return envioRepository.findById(id).map(EnvioDTO::from)
            .orElseThrow(() -> new RuntimeException("Envío no encontrado: " + id));
    }

    public EnvioDTO actualizarStatus(Long id, String nuevoStatus) {
        Envio envio = envioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Envío no encontrado: " + id));
        envio.setStatus(nuevoStatus);
        return EnvioDTO.from(envioRepository.save(envio));
    }
}
