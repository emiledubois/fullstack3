package com.smartlogix.pedidos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InternalTokenSignerTest {

    private InternalTokenSigner signer;

    @BeforeEach
    void setUp() {
        signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", "shared-internal-secret");
    }

    @Test
    void headers_generaLasTresCabecerasConFirmaHmacSha256ComoMsPedidos() throws Exception {

        // ARRANGE — no hay nada que preparar, el signer usa el reloj del sistema

        // ACT
        Map<String, String> headers = signer.headers();

        // ASSERT
        assertEquals("ms-pedidos", headers.get("X-Internal-Service"));
        String expectedSignature = hmacSha256(
            "shared-internal-secret", "ms-pedidos:" + headers.get("X-Internal-Timestamp"));
        assertEquals(expectedSignature, headers.get("X-Internal-Signature"));
        assertEquals(64, headers.get("X-Internal-Signature").length());
    }

    @Test
    void exchangeFilter_firmaLaPeticionSalienteYEliminaCabecerasPreexistentes() {

        // ARRANGE — simula una petición saliente con cabeceras internas
        // pre-existentes (defensa en profundidad, aunque los WebClient de
        // la Saga no las fijan hoy)
        ClientRequest original = ClientRequest.create(org.springframework.http.HttpMethod.POST,
                URI.create("http://ms-inventario:8082/inventario/1/reservar"))
            .header("X-Internal-Service", "otro-servicio")
            .build();

        AtomicReference<ClientRequest> capturado = new AtomicReference<>();

        // ACT
        signer.exchangeFilter()
            .filter(original, req -> {
                capturado.set(req);
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            })
            .block();

        // ASSERT
        ClientRequest signed = capturado.get();
        assertEquals("ms-pedidos", signed.headers().getFirst("X-Internal-Service"));
        assertEquals(1, signed.headers().get("X-Internal-Service").size());
        assertNotNull(signed.headers().getFirst("X-Internal-Timestamp"));
        assertNotNull(signed.headers().getFirst("X-Internal-Signature"));
    }

    @Test
    void validarConfiguracion_secretoVacio_lanzaIllegalStateException() {

        // ARRANGE
        ReflectionTestUtils.setField(signer, "secret", "");

        // ACT & ASSERT
        assertThrows(IllegalStateException.class, () -> signer.validarConfiguracion());
    }

    @Test
    void validarConfiguracion_secretoNulo_lanzaIllegalStateException() {

        // ARRANGE
        ReflectionTestUtils.setField(signer, "secret", null);

        // ACT & ASSERT
        assertThrows(IllegalStateException.class, () -> signer.validarConfiguracion());
    }

    private static String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
