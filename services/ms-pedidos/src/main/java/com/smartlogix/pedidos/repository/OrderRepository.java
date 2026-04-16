package com.smartlogix.pedidos.repository;

import com.smartlogix.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByUserId(Long userId);
    List<Pedido> findByStatus(String status);
}
