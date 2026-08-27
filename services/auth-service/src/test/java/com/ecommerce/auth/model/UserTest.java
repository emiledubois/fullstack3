package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void prePersist_createdAtNulo_loFijaAHoraActual() {

        // ARRANGE
        User user = User.builder().email("nuevo@pyme.cl").password("hash").role("ROLE_USER").build();

        // ACT
        user.prePersist();

        // ASSERT
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void prePersist_createdAtYaFijado_noLoSobrescribe() {

        // ARRANGE — simula una entidad ya cargada desde la BD con un valor propio
        java.time.LocalDateTime original = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        User user = User.builder().email("viejo@pyme.cl").password("hash")
                .role("ROLE_USER").createdAt(original).build();

        // ACT
        user.prePersist();

        // ASSERT
        assertEquals(original, user.getCreatedAt());
    }
}
