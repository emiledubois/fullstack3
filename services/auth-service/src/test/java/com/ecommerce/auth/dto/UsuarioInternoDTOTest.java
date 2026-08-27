package com.ecommerce.auth.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioInternoDTOTest {

    @Test
    void clase_noDeclaraNingunCampoPassword() {

        // ARRANGE
        Field[] campos = UsuarioInternoDTO.class.getDeclaredFields();

        // ACT
        boolean tienePassword = Arrays.stream(campos)
                .anyMatch(f -> f.getName().toLowerCase().contains("password")
                        || f.getName().toLowerCase().contains("contraseña")
                        || f.getName().toLowerCase().contains("contrasena"));

        // ASSERT — regresión estática, no solo en runtime (criterio de aceptación 6)
        assertTrue(!tienePassword, "UsuarioInternoDTO no debe declarar ningún campo de contraseña");
    }
}
