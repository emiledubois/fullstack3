package com.smartlogix.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MsPagosApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsPagosApplication.class, args);
    }
}
