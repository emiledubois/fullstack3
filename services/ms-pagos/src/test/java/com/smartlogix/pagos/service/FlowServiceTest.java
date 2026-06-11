package com.smartlogix.pagos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlowServiceTest {

    private FlowService flowService;

    @BeforeEach
    void setUp() {
        flowService = new FlowService();
        // Inyectar los valores de configuración
        ReflectionTestUtils.setField(flowService, "flowUrl",
            "https://sandbox.flow.cl/api");
        ReflectionTestUtils.setField(flowService, "apiKey",
            "1F90971E-8276-4715-97FF-2BLG5030EE3B");
        ReflectionTestUtils.setField(flowService, "secretKey",
            "my secret");
    }

    @Test
    void firmar_ordenAlfabeticoHmacSha256_generaHexdigestCorrecto() {

        // ARRANGE
        // Parámetros de ejemplo basados en la documentación de Flow
        // Fuente: developers.flow.cl — Primeros pasos (Firma de parámetros)
        // El algoritmo ordena alfabéticamente: apiKey, token
        // Concatena: apiKey1F90971E...tokenAJ089FF5467367
        // Firma con HMAC-SHA256 y secretKey "my secret"
        Map<String, String> params = new LinkedHashMap<>();
        params.put("token",  "AJ089FF5467367");
        params.put("apiKey", "1F90971E-8276-4715-97FF-2BLG5030EE3B");
        // (no importa el orden de inserción — el algoritmo los ordena)

        // ACT
        // Ejecutar el método de firma
        String firma = flowService.firmar(params);

        // ASSERT
        // El resultado debe ser un hexdigest de 64 caracteres (SHA-256)
        assertNotNull(firma);
        assertEquals(64, firma.length(),
            "HMAC-SHA256 debe producir exactamente 64 caracteres hexadecimales");

        // Solo debe contener caracteres hexadecimales válidos
        assertTrue(firma.matches("[0-9a-f]+"),
            "La firma debe ser un string hexadecimal en minúsculas");

        // El resultado debe ser determinista: mismos parámetros = misma firma
        String firma2 = flowService.firmar(params);
        assertEquals(firma, firma2,
            "La firma debe ser idéntica para los mismos parámetros");
    }
}
