# SmartLogix - Plataforma de Logística Full-Stack

[![Documentación](https://img.shields.io/badge/Documentación-Mintlify-2ea44f)](https://emiledubois-fullstack3-29-0.mintlify.app/)
[![Java](https://img.shields.io/badge/Java-21-red)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0-green)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue)](https://reactjs.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://www.docker.com/)

##  Descripción General

SmartLogix es una plataforma de logística fullstack que provee visibilidad y control sobre una cadena de operaciones de suministro. Construida con una arquitectura de microservicios con backend de Spring Boot y un frontend de React, maneja el registro de inventario, manejo de órdenes, logísticas de envíos, y todo es accesible mediante una API gateway con autenticación JWT.

## Características Clave

*   **Gestión de Inventario**: Rastrea productos con SKUs, la ubicación de bodegas, niveles de stock, y posee alertas automatizadas de stock bajo. 
*   **Procesamiento de Pedidos**: Crea y gestiona órdenes nacionales e internacionales con validación de stock a tiempo real.
*   **Seguimiento de Envíos**: Asigna transportistas y rastrea las entregas mediante una pipeline de cuatro etapas: desde que es creado el pedido hasta que es entregado. 
*   **Notificaciones**: Recibe alertas event-driven asíncronas cuando las órdenes son aprobadas.
*   **Autenticación**: Autenticación con usuario JWT-based y API security.


## Tecnologías Utilizadas

*   **Backend**: Java 21, Spring Boot 3, Maven, PostgreSQL
*   **Frontend**: React 18, JavaScript (ES6+), HTML5 & CSS3
*   **Infraestructura**: Docker & Docker Compose, API Gateway
*   **Pruebas**: cURL

