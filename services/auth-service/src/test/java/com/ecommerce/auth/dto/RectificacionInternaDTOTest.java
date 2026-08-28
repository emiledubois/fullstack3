package com.ecommerce.auth.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectificacionInternaDTOTest {

    @Test
    void toString_nuncaExponeElTokenCompleto() {

        // ARRANGE — primer DTO interno de este codebase que lleva un token
        // de sesión vivo (arco-remaining-rights.md §7 A02) — un log accidental
        // de este objeto no debe filtrar el JWT completo
        String tokenSecreto = "un-jwt-muy-largo-y-secreto-1234567890";
        RectificacionInternaDTO dto = new RectificacionInternaDTO(
                "nueva@pyme.cl", "ROLE_USER", LocalDateTime.of(2026, 1, 15, 9, 0), tokenSecreto);

        // ACT
        String texto = dto.toString();

        // ASSERT
        assertFalse(texto.contains(tokenSecreto));
        assertTrue(texto.contains("***"));
    }
}
