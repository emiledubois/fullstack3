package com.ecommerce.api.gateway.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RectificarEmailRequestTest {

    @Test
    void clase_noDeclaraNingunCampoDeIdentidadDeCuentaObjetivo() {

        // ARRANGE — criterio de aceptación 5 (IDOR crítico) de
        // arco-remaining-rights.md: la cuenta a corregir es siempre la de la
        // cookie sl_jwt verificada; este contrato no debe tener NINGÚN campo
        // (email/id/usuario) que pueda referirse a "de quién" es la cuenta
        Field[] campos = RectificarEmailRequest.class.getDeclaredFields();

        // ACT
        boolean tieneCampoDeIdentidadObjetivo = Arrays.stream(campos)
                .map(Field::getName)
                .map(String::toLowerCase)
                .anyMatch(nombre -> (nombre.contains("email") && !nombre.equals("emailnuevo"))
                        || nombre.contains("id")
                        || nombre.contains("usuario"));

        // ASSERT — regresión estática, no solo en runtime
        assertTrue(!tieneCampoDeIdentidadObjetivo,
                "RectificarEmailRequest no debe declarar ningún campo que identifique de quién es la cuenta objetivo");
    }
}
