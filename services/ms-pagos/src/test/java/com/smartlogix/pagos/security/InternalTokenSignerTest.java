package com.smartlogix.pagos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InternalTokenSignerTest {

    private InternalTokenSigner signer;

    @BeforeEach
    void setUp() {
        signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", "shared-internal-secret");
    }

    @Test
    void headers_generaLasTresCabecerasConFirmaHmacSha256ComoMsPagos() throws Exception {

        // ARRANGE — el signer usa el reloj del sistema, nada más que preparar

        // ACT
        Map<String, String> headers = signer.headers();

        // ASSERT
        assertEquals("ms-pagos", headers.get("X-Internal-Service"));
        String expectedSignature = hmacSha256(
            "shared-internal-secret", "ms-pagos:" + headers.get("X-Internal-Timestamp"));
        assertEquals(expectedSignature, headers.get("X-Internal-Signature"));
        assertEquals(64, headers.get("X-Internal-Signature").length());
        assertTrue(headers.get("X-Internal-Signature").matches("[0-9a-f]+"));
    }

    @Test
    void headers_secretosDistintosProducenFirmasDistintas() {

        // ARRANGE — mismo timestamp, secretos distintos (simula un
        // despliegue con INTERNAL_SERVICE_SECRET mal configurado)
        ReflectionTestUtils.setField(signer, "secret", "secreto-A");
        long timestamp = 1_700_000_000_000L;
        String firmaA = signer.sign(timestamp);

        // ACT
        ReflectionTestUtils.setField(signer, "secret", "secreto-B");
        String firmaB = signer.sign(timestamp);

        // ASSERT
        assertNotEquals(firmaA, firmaB);
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
