package com.ecommerce.api.gateway.security;

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
    void headers_generaLasTresCabecerasConFirmaHmacSha256Valida() throws Exception {

        // ARRANGE — nada que preparar, el signer usa el reloj del sistema

        // ACT
        Map<String, String> headers = signer.headers();

        // ASSERT
        assertEquals("api-gateway", headers.get("X-Internal-Service"));
        assertNotNull(headers.get("X-Internal-Timestamp"));
        String expectedSignature = hmacSha256(
            "shared-internal-secret",
            "api-gateway:" + headers.get("X-Internal-Timestamp"));
        assertEquals(expectedSignature, headers.get("X-Internal-Signature"));
        assertEquals(64, headers.get("X-Internal-Signature").length());
        assertTrue(headers.get("X-Internal-Signature").matches("[0-9a-f]+"));
    }

    @Test
    void headers_timestampEsFrescoEnMilisegundos() {

        // ARRANGE
        long antes = System.currentTimeMillis();

        // ACT
        Map<String, String> headers = signer.headers();

        // ASSERT
        long despues = System.currentTimeMillis();
        long timestamp = Long.parseLong(headers.get("X-Internal-Timestamp"));
        assertTrue(timestamp >= antes && timestamp <= despues);
    }

    @Test
    void exchangeFilter_eliminaCabecerasForjadasYFijaLasPropias() throws Exception {

        // ARRANGE — una petición saliente con cabeceras internas ya
        // presentes (simulan un intento de spoofing antes del filtro)
        ClientRequest original = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                URI.create("http://ms-inventario:8082/inventario"))
            .header("X-Internal-Service", "servicio-forjado")
            .header("X-Internal-Timestamp", "0")
            .header("X-Internal-Signature", "firma-forjada")
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
        assertNotNull(signed);
        assertEquals("api-gateway", signed.headers().getFirst("X-Internal-Service"));
        assertEquals(1, signed.headers().get("X-Internal-Service").size());
        String timestamp = signed.headers().getFirst("X-Internal-Timestamp");
        assertNotEquals("0", timestamp);
        String expectedSignature = hmacSha256("shared-internal-secret", "api-gateway:" + timestamp);
        assertEquals(expectedSignature, signed.headers().getFirst("X-Internal-Signature"));
        assertNotEquals("firma-forjada", signed.headers().getFirst("X-Internal-Signature"));
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
