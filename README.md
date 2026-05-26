# SmartLogix 

**Plataforma de Gestión Logística para PYMEs**

SmartLogix es una aplicación SaaS de gestión logística desarrollada con arquitectura de microservicios. Permite a pequeñas y medianas empresas gestionar su inventario, pedidos y envíos desde una interfaz web unificada, con autenticación segura y tolerancia a fallos incorporada.

---

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Backend | Java 17 · Spring Boot 3.4.5 · Maven |
| API Gateway | Spring Cloud Gateway MVC 2024.0.1 |
| Seguridad | Spring Security · JWT (jjwt 0.12.x) |
| Base de datos | PostgreSQL 15 (instancia independiente por servicio) |
| ORM | Spring Data JPA · Hibernate 6.6 |
| Resiliencia | Resilience4j 2.2.0 (Circuit Breaker) |
| Frontend | React 18 · Vite · Tailwind CSS |
| Contenedores | Docker · Docker Compose |

---

## Arquitectura

```
Frontend React :5173
        │
        ▼  HTTP (proxy nginx)
api-gateway :8080  [AuthFilter JWT + GatewayConfig]
        │
        ├──▶ auth-service      :8081  ──▶ auth_db
        ├──▶ ms-inventario     :8082  ──▶ inventario_db
        ├──▶ ms-pedidos        :8083  ──▶ pedidos_db  (saga_estado)
        ├──▶ ms-envios         :8084  ──▶ envios_db
        └──▶ notification-service :8085
```

Cada microservicio tiene su propia base de datos PostgreSQL — patrón **Database-per-Service**.

---

## Patrones de diseño implementados

| Patrón | Tipo | Servicio | Descripción |
|--------|------|----------|-------------|
| **API Gateway** | Arquitectura | api-gateway | Punto único de entrada. JWT validado centralmente en AuthFilter. |
| **Repository** | GoF — Datos | Todos los servicios | Acceso a datos vía `JpaRepository`. Desacopla lógica de negocio de la BD. |
| **Factory Method** | GoF — Creacional | ms-pedidos, ms-envios | `PedidoFactory` crea NACIONAL o INTERNACIONAL. `EnvioFactory` crea TERRESTRE o EXPRESS. |
| **Circuit Breaker** | Resiliencia | ms-pedidos | Resilience4j protege la llamada a ms-inventario. Fallback instantáneo si el servicio cae. |
| **Observer** | GoF — Comportamiento | ms-pedidos | `PedidoAprobadoEvent` publicado con Spring Events, consumido por `NotificationListener @Async`. |
| **Strategy** | GoF — Comportamiento | ms-inventario | Algoritmos de alerta de stock intercambiables en runtime: `UmbralFijo`, `Porcentaje`, `StockCritico`. |
| **Facade** | GoF — Estructural | ms-pedidos | `LogisticaFacade` coordina 4 subsistemas con una sola llamada desde el controller. |
| **Saga** | Arquitectura distribuida | ms-pedidos | Orquestador de 4 pasos con compensaciones automáticas en orden inverso. Estado persistido en `saga_estado`. |

---

## Estructura del proyecto

```
ecommerce_microservices/
├── docker-compose.yml
├── frontend/
│   └── smartlogix-app/          # React + Vite + Tailwind
│       ├── nginx.conf           # Proxy /api/* → api-gateway
│       └── src/
│           ├── pages/           # Login, Dashboard, Inventario, Pedidos, Envios
│           └── services/api.js  # Axios con interceptor JWT
└── services/
    ├── api-gateway/             # Spring Cloud Gateway MVC, puerto 8080
    ├── auth-service/            # JWT auth, puerto 8081
    ├── ms-inventario/           # CRUD productos + Strategy alerts, puerto 8082
    ├── ms-pedidos/              # Pedidos + Facade + Saga, puerto 8083
    ├── ms-envios/               # Gestión envíos + Factory, puerto 8084
    └── notification-service/   # REST endpoint notificaciones, puerto 8085
```

---

## Requisitos

- Docker Engine 26+
- Docker Compose 2+
- 4 GB RAM disponible (recomendado)

---

## Instalación y ejecución

```bash
# Clonar el repositorio
git clone https://github.com/emiledubois/fullstack3.git
cd fullstack3/ecommerce_microservices

# Construir y levantar todos los servicios
docker compose up --build -d

# Verificar que todos los servicios están corriendo
docker compose ps

# Ver logs en tiempo real
docker compose logs -f
```

Una vez levantado, acceder a **http://localhost:5173**

---

## Primer uso

```bash
# Registrar usuario administrador
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@smartlogix.cl","password":"Password123!"}'

# Iniciar sesión y capturar token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@smartlogix.cl","password":"Password123!"}' | tr -d '"')
```

---

## API endpoints principales

Todos los endpoints (excepto `/api/auth/*`) requieren header `Authorization: Bearer <token>`.

### Autenticación
```
POST /api/auth/register   → registrar usuario
POST /api/auth/login      → obtener JWT
```

### Inventario
```
GET    /api/inventario                          → listar productos
POST   /api/inventario                          → crear producto
GET    /api/inventario/{id}                     → detalle producto
GET    /api/inventario/alertas                  → alertas con estrategia por defecto
GET    /api/inventario/alertas/estrategia?estrategia=critico  → cambiar estrategia en runtime
GET    /api/inventario/alertas/estrategias      → listar estrategias disponibles
POST   /api/inventario/{id}/reservar?cantidad=N → reservar stock (usado por Saga)
POST   /api/inventario/{id}/liberar?cantidad=N  → liberar stock (compensación Saga)
```

### Pedidos
```
GET  /api/pedidos        → listar pedidos
POST /api/pedidos        → crear pedido (flujo normal via Facade)
GET  /api/pedidos/{id}   → detalle pedido
```

### Saga (transacción distribuida)
```
POST /api/sagas/pedido   → crear pedido con Saga (reserva stock + crea pedido + crea envío + notifica)
GET  /sagas/{sagaId}     → consultar estado de una saga (directo a :8083)
```

### Envíos
```
GET   /api/envios                          → listar envíos
POST  /api/envios                          → crear envío
PATCH /api/envios/{id}/status?status=ASIGNADO  → avanzar estado
DELETE /api/envios/{id}/cancelar           → cancelar envío (compensación Saga)
```

---

## Patrón Strategy — Cambio de estrategia en runtime

```bash
# Estrategia umbral fijo (por defecto): alerta cuando stock < umbralMinimo
curl http://localhost:8080/api/inventario/alertas -H "Authorization: Bearer $TOKEN"

# Estrategia porcentaje: alerta cuando stock < umbralMinimo * 1.5
curl "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=porcentaje" \
  -H "Authorization: Bearer $TOKEN"

# Estrategia crítico: alerta solo cuando stock == 0
curl "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=critico" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Patrón Saga — Flujo completo y compensaciones

```bash
# Ejecutar Saga completa (4 pasos: reservar stock → crear pedido → crear envío → notificar)
curl -X POST http://localhost:8080/api/sagas/pedido \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"userEmail":"cliente@pyme.cl","clienteNombre":"Juan Pérez",
       "total":999.99,"tipoPedido":"NACIONAL","destino":"Santiago",
       "productoId":1,"cantidad":2}'

# Simular fallo en Paso 3 (compensaciones automáticas)
docker compose stop ms-envios
# → ejecutar saga → stock restaurado + pedido CANCELADO automáticamente
docker compose start ms-envios

# Auditar sagas en BD
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
  -c "SELECT saga_id, estado, pedido_id, envio_id FROM saga_estado ORDER BY actualizado_en DESC LIMIT 5;"
```

---

## Variables de entorno relevantes

| Variable | Servicio | Descripción |
|----------|----------|-------------|
| `JWT_SECRET` | api-gateway, auth-service | Clave HMAC-SHA256 para firmar/validar tokens |
| `DB_HOST` | Todos los servicios con BD | Host de la base de datos (nombre del contenedor) |
| `DB_NAME` | Todos los servicios con BD | Nombre de la base de datos |
| `INVENTARIO_URL` | ms-pedidos | URL de ms-inventario para la Saga |
| `ENVIOS_URL` | ms-pedidos | URL de ms-envios para la Saga |
| `NOTIFICATION_URL` | ms-pedidos | URL de notification-service para la Saga |

---

## Comandos útiles

```bash
# Reconstruir un servicio específico
docker compose build ms-pedidos && docker compose up -d ms-pedidos

# Ver logs de un servicio
docker compose logs ms-pedidos --tail=50

# Health check de todos los servicios
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8082/actuator/health   # ms-inventario
curl http://localhost:8083/actuator/health   # ms-pedidos

# Detener todo
docker compose down

# Detener todo y eliminar volúmenes (resetea las BDs)
docker compose down --volumes
```

---

Proyecto semestral — DSY1106 Desarrollo Fullstack III
