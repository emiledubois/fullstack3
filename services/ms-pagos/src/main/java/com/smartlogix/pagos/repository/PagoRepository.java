package com.smartlogix.pagos.repository;

import com.smartlogix.pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByFlowToken(String flowToken);
    Optional<Pago> findByPedidoId(Long pedidoId);
    Optional<Pago> findByCommerceOrder(String commerceOrder);
}
