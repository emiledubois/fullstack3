package com.smartlogix.envios.repository;
import com.smartlogix.envios.model.Envio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnvioRepository extends JpaRepository<Envio, Long> {
    List<Envio> findByPedidoId(Long pedidoId);
    List<Envio> findByStatus(String status);
}
