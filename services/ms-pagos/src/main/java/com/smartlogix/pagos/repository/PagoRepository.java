package com.smartlogix.pagos.repository;

import com.smartlogix.pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByFlowToken(String flowToken);
    Optional<Pago> findByPedidoId(Long pedidoId);
    Optional<Pago> findByCommerceOrder(String commerceOrder);

    // Derecho de cancelación ARCO+ (arco-cancelacion-oposicion.md §4.1):
    // anonimiza en bloque, no borra filas — preserva el historial de pagos
    // (monto/estado/timestamps) mientras elimina el único campo de PII del
    // titular de la cuenta en este servicio. Idempotente: en un reintento,
    // las filas ya anonimizadas simplemente no matchean el WHERE. Mismo
    // convenio LOWER(...) case-insensitive que OrderRepository.findByUserEmail.
    @Modifying
    @Query("UPDATE Pago p SET p.email = :marcador WHERE LOWER(p.email) = LOWER(:email)")
    int anonimizarPorEmail(@Param("email") String email, @Param("marcador") String marcador);
}
