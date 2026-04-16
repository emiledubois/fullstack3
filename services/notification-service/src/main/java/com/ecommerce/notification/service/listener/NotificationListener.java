package com.ecommerce.notification.service.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

// notification-service: microservicio REST independiente
@RestController
@RequestMapping("/notificaciones")
@Slf4j
public class NotificationListener {

    // Endpoint REST para recibir notificaciones desde otros servicios
    @PostMapping
    public ResponseEntity<String> recibirNotificacion(@RequestBody Map<String, Object> payload) {
        log.info("[notification-service] Notificación recibida: {}", payload);
        return ResponseEntity.ok("Notificación procesada");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("notification-service UP");
    }
}
