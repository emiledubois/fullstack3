package com.smartlogix.pedidos.repository;

import com.smartlogix.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByUserId(Long userId);
    List<Pedido> findByStatus(String status);

    // Case-insensitive: CreatePedidoRequest.userEmail lo ingresa el creador
    // del pedido (no siempre el propio dueño de la cuenta), así que no hay
    // garantía de que coincida en mayúsculas/minúsculas con el email de
    // login del data subject (ver arco-acceso-personal-data.md §9, punto 3).
    @Query("SELECT p FROM Pedido p WHERE LOWER(p.userEmail) = LOWER(:email)")
    List<Pedido> findByUserEmail(@Param("email") String email);

    // Derecho de cancelación ARCO+ (arco-cancelacion-oposicion.md §4.1):
    // anonimiza en bloque, no borra filas — preserva el historial operativo
    // (total/status/timestamps) mientras elimina el único campo de PII del
    // titular de la cuenta en este servicio. Idempotente: en un reintento,
    // las filas ya anonimizadas simplemente no matchean el WHERE.
    @Modifying
    @Query("UPDATE Pedido p SET p.userEmail = :marcador WHERE LOWER(p.userEmail) = LOWER(:email)")
    int anonimizarPorEmail(@Param("email") String email, @Param("marcador") String marcador);
}
