# SmartLogix

**Plataforma de Gestión Logística para PYMEs**

SmartLogix es una aplicación SaaS de gestión logística desarrollada con arquitectura de microservicios. Permite a pequeñas y medianas empresas gestionar su inventario, pedidos, envíos y pagos en línea desde una interfaz web unificada, con autenticación segura, tolerancia a fallos y transacciones distribuidas incorporadas.

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
| Pagos | Flow Chile API (HMAC-SHA256) |
| Frontend | React 18 · Vite · Tailwind CSS · Axios |
| Pruebas | JUnit 5 · Mockito (estructura AAA) |
| Contenedores | Docker · Docker Compose |

---

## Arquitectura

```
Frontend React :5173
        │
        ▼  HTTP (proxy nginx)
api-gateway :8080  [AuthFilter JWT + WebHub/BFF]
        │
        ├──▶ auth-service          :8081  ──▶ auth_db
        ├──▶ ms-inventario         :8082  ──▶ inventario_db
        ├──▶ ms-pedidos            :8083  ──▶ pedidos_db   (saga_estado, JSONB)
        ├──▶ ms-envios             :8084  ──▶ envios_db
        ├──▶ notification-service :8085  (sin BD — Observer)
        └──▶ ms-pagos              :8086  ──▶ pagos_db
                    │
                    ▼  HTTPS (firma HMAC-SHA256)
              Flow Chile API (sandbox.flow.cl)
                    │
                    ▼  Webhook (sin JWT, firma verificada)
              POST /api/pagos/webhook/flow
```

Cada microservicio con persistencia tiene su propia base de datos PostgreSQL — patrón **Database-per-Service**.

El **api-gateway** cumple un doble rol: valida el JWT de cada petición (excepto `/api/auth/*` y el webhook de Flow) y actúa como **WebHub / Backend for Frontend (BFF)**, agregando en paralelo datos de tres microservicios en el endpoint `/api/dashboard`.

---

## Patrones de diseño implementados

| Patrón | Tipo | Servicio | Descripción |
|--------|------|----------|-------------|
| **API Gateway** | Arquitectura | api-gateway | Punto único de entrada. JWT validado centralmente en `AuthFilter`. |
| **WebHub / BFF** | Arquitectura | api-gateway | `DashboardController` agrega en paralelo inventario, pedidos y envíos con `WebClient` + `Mono.zip`, tolerante a fallos parciales con `onErrorReturn`. |
| **Repository** | GoF — Datos | Todos los servicios | Acceso a datos vía `JpaRepository`. Desacopla lógica de negocio de la BD. |
| **Factory Method** | GoF — Creacional | ms-pedidos, ms-envios | `PedidoFactory` crea NACIONAL o INTERNACIONAL. `EnvioFactory` crea TERRESTRE o EXPRESS. |
| **Circuit Breaker** | Resiliencia | ms-pedidos | Resilience4j protege la llamada a ms-inventario. Fallback instantáneo si el servicio cae. |
| **Observer** | GoF — Comportamiento | ms-pedidos | `PedidoAprobadoEvent` publicado con Spring Events, consumido por `NotificationListener @Async`. |
| **Strategy** | GoF — Comportamiento | ms-inventario | Algoritmos de alerta de stock intercambiables en runtime: `UmbralFijo`, `Porcentaje`, `StockCritico`. |
| **Facade** | GoF — Estructural | ms-pedidos | `LogisticaFacade` coordina 4 subsistemas con una sola llamada desde el controller. |
| **Saga** | Arquitectura distribuida | ms-pedidos | Orquestador de 4 pasos con compensaciones automáticas en orden inverso. Estado persistido en `saga_estado` (columna JSONB). |
| **Flow Payment Gateway** | Integración externa | ms-pagos | Integración con Flow Chile: firma HMAC-SHA256, webhook público de confirmación, disparo de Saga tras pago exitoso. |

---

## Estructura del proyecto

```
ecommerce_microservices/
├── docker-compose.yml
├── .env                          # Credenciales de Flow (no versionado)
├── frontend/
│   └── smartlogix-app/            # React + Vite + Tailwind
│       ├── nginx.conf             # Proxy /api/* → api-gateway
│       └── src/
│           ├── App.jsx            # Layout, navegación, intercepta /pago-resultado
│           ├── pages/
│           │   ├── Login.jsx
│           │   ├── Dashboard.jsx       # Una sola llamada a /api/dashboard (BFF)
│           │   ├── Inventario.jsx
│           │   ├── Pedidos.jsx          # Carrito multi-producto, búsqueda por SKU
│           │   ├── Envios.jsx
│           │   └── PagoResultado.jsx    # Página de retorno desde Flow
│           └── services/api.js    # Axios + interceptor JWT + dashboardAPI + pagoAPI
└── services/
    ├── api-gateway/              # Spring Cloud Gateway MVC + BFF, puerto 8080
    ├── auth-service/             # JWT auth, puerto 8081
    ├── ms-inventario/            # CRUD productos + Strategy alerts, puerto 8082
    ├── ms-pedidos/               # Pedidos + Facade + Saga, puerto 8083
    ├── ms-envios/                # Gestión envíos + Factory, puerto 8084
    ├── notification-service/    # REST endpoint notificaciones, puerto 8085
    └── ms-pagos/                 # Integración Flow Chile, puerto 8086
```

---

## Requisitos

- Docker Engine 26+
- Docker Compose 2+
- 4 GB RAM disponible (recomendado)
- Cuenta en [sandbox.flow.cl](https://sandbox.flow.cl) (solo para probar pagos)
- [ngrok](https://ngrok.com) (solo para probar el webhook de Flow en desarrollo local)

---

## Instalación y ejecución

```bash
# Clonar el repositorio
git clone https://github.com/emiledubois/fullstack3.git
cd fullstack3/ecommerce_microservices

# Crear archivo .env con las credenciales de Flow Sandbox
cat > .env << 'EOF'
FLOW_API_KEY=tu_api_key_sandbox
FLOW_SECRET_KEY=tu_secret_key_sandbox
FLOW_URL_CONFIRMATION=https://TU_NGROK.ngrok-free.app/api/pagos/webhook/flow
EOF

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

## API Endpoints principales

Todos los endpoints (excepto `/api/auth/*` y el webhook de Flow) requieren header `Authorization: Bearer <token>`.

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
GET  /api/pedidos                                  → listar pedidos
POST /api/pedidos                                  → crear pedido (flujo normal vía Facade)
GET  /api/pedidos/{id}                              → detalle pedido
POST /api/pedidos/{id}/confirmar-pago?token=XXX     → confirmar pago y disparar Saga (interno, llamado por ms-pagos)
POST /api/pedidos/{id}/pago-fallido?estado=XXX      → marcar pago rechazado/anulado (interno)
```

### Saga (transacción distribuida)
```
POST /api/sagas/pedido   → crear pedido con Saga (reserva stock + crea pedido + crea envío + notifica)
GET  /sagas/{sagaId}     → consultar estado de una saga (directo a :8083, no pasa por el gateway)
```

### Envíos
```
GET    /api/envios                              → listar envíos
POST   /api/envios                              → crear envío
PATCH  /api/envios/{id}/status?status=ASIGNADO  → avanzar estado
DELETE /api/envios/{id}/cancelar                → cancelar envío (compensación Saga)
```

### Dashboard — WebHub / BFF
```
GET /api/dashboard   → datos agregados de inventario + pedidos + envíos en una sola llamada,
                        con métricas calculadas (valor de inventario, pedidos pendientes, envíos en ruta)
```

### Pagos — Flow Chile
```
POST /api/pagos/crear            → iniciar orden de pago en Flow, devuelve URL de checkout
POST /api/pagos/webhook/flow     → webhook público de confirmación (sin JWT, firma HMAC-SHA256)
GET  /api/pagos/{id}             → consultar estado de un pago
```

### Health checks
```
GET http://localhost:8080/actuator/health   # gateway
GET http://localhost:8082/actuator/health   # ms-inventario
GET http://localhost:8083/actuator/health   # ms-pedidos
GET http://localhost:8086/actuator/health   # ms-pagos
```

> Una colección Postman completa con los 29 endpoints, ejemplos de request/response y un script que captura el JWT automáticamente está disponible en `docs/SmartLogix_Postman_Collection.json`.

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

## Integración de pagos — Flow Chile

SmartLogix integra la pasarela de pago [Flow](https://www.flow.cl) a través del microservicio `ms-pagos`. El flujo completo es:

1. El frontend crea un pedido con `metodoPago: "FLOW"` → `ms-pedidos` lo registra como `PENDIENTE_PAGO`.
2. `ms-pedidos` llama a `ms-pagos`, que firma los parámetros con HMAC-SHA256 y crea la orden en Flow (`POST /payment/create`).
3. El frontend redirige al usuario a la URL de checkout devuelta por Flow.
4. Tras el pago, Flow notifica el resultado al webhook `POST /api/pagos/webhook/flow` (ruta pública, excluida del `AuthFilter`).
5. `ms-pagos` verifica la firma, consulta el estado real con `GET /payment/getStatus`, y si el pago fue exitoso, llama a `ms-pedidos` para disparar la Saga original.
6. El usuario es redirigido de vuelta a `/pago-resultado` en el frontend.

```bash
# Probar el flujo completo localmente con ngrok
ngrok http 8086
# Copiar la URL de Forwarding y actualizar FLOW_URL_CONFIRMATION en .env

docker compose build ms-pagos && docker compose up -d ms-pagos

# Crear un pago de prueba
curl -X POST http://localhost:8080/api/pagos/crear \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pedidoId":1,"monto":5000,"email":"cliente@pyme.cl","descripcion":"Pedido de prueba"}'
```

### Tarjetas de prueba (Sandbox)

| Tipo | Número | Resultado |
|------|--------|-----------|
| Visa (éxito) | 4051885600446623 | Status 2 — Pagado |
| Visa (rechazo) | 4051885600446631 | Status 3 — Rechazado |
| RUT / Clave (todas) | 11.111.111-1 / 123 | — |
| Monto mínimo | $350 CLP | Montos menores son rechazados |

---

## WebHub / BFF — Dashboard agregado

```bash
# Una sola petición reemplaza 4 llamadas independientes del frontend
curl http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"

# Probar tolerancia a fallos parciales
docker compose stop ms-envios
curl http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
# → la respuesta llega igual, con ultimosEnvios:[] y estadoServicios:"PARCIAL"
docker compose start ms-envios
```

---

## Pruebas unitarias

El proyecto incluye **17 pruebas unitarias** con JUnit 5 y Mockito, siguiendo la estructura **AAA (Arrange, Act, Assert)**, distribuidas en los tres microservicios con mayor lógica de negocio:

| Microservicio | Clases de prueba | Patrón cubierto |
|---|---|---|
| ms-pedidos | `OrderServiceTest` (3), `OrderControllerTest` (2), `SagaOrchestratorTest` (2), `SagaControllerTest` (2) | Factory Method, Facade, Saga + compensaciones |
| ms-inventario | `AlertaServiceTest` (3), `InventarioControllerTest` (1) | Strategy |
| ms-pagos | `PagoServiceTest` (3), `FlowServiceTest` (1), `PagoControllerTest` (1) | Integración Flow, firma HMAC-SHA256 |

```bash
# Ejecutar las pruebas de un microservicio
cd services/ms-pedidos
bash mvnw test

# Ejecutar las pruebas de los 3 microservicios principales de una vez
set BASE /ruta/a/ecommerce_microservices/services   # fish shell
for svc in ms-pedidos ms-inventario ms-pagos
    echo "=== $svc ==="
    cd $BASE/$svc
    bash mvnw test 2>&1 | grep -E "Tests run.*Time|BUILD"
end
```

**Resultado esperado:** 17 tests, 0 fallos, 0 errores, `BUILD SUCCESS` en los tres servicios.

---

## Variables de entorno relevantes

| Variable | Servicio | Descripción |
|----------|----------|-------------|
| `JWT_SECRET` | api-gateway, auth-service | Clave HMAC-SHA256 para firmar/validar tokens |
| `DB_HOST` / `DB_NAME` | Todos los servicios con BD | Host y nombre de la base de datos (nombre del contenedor) |
| `INVENTARIO_URL` | ms-pedidos, api-gateway (BFF) | URL de ms-inventario |
| `ENVIOS_URL` | ms-pedidos, api-gateway (BFF) | URL de ms-envios |
| `NOTIFICATION_URL` | ms-pedidos | URL de notification-service |
| `PEDIDOS_URL` | ms-pagos, api-gateway (BFF) | URL de ms-pedidos |
| `PAGOS_URL` | api-gateway | URL de ms-pagos |
| `FLOW_API_URL` | ms-pagos | URL base de la API de Flow (sandbox o producción) |
| `FLOW_API_KEY` / `FLOW_SECRET_KEY` | ms-pagos | Credenciales del comercio en Flow |
| `FLOW_URL_CONFIRMATION` | ms-pagos | URL pública del webhook (ngrok en desarrollo local) |
| `FLOW_URL_RETURN` | ms-pagos | URL del frontend a la que Flow redirige tras el pago |

---

## Comandos útiles

```bash
# Reconstruir un servicio específico
docker compose build ms-pedidos && docker compose up -d ms-pedidos

# Ver logs de un servicio
docker compose logs ms-pagos --tail=50

# Health check de todos los servicios
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8082/actuator/health   # ms-inventario
curl http://localhost:8083/actuator/health   # ms-pedidos
curl http://localhost:8086/actuator/health   # ms-pagos

# Auditar pagos en base de datos
docker compose exec postgres-pagos psql -U postgres pagos_db \
  -c "SELECT id, pedido_id, flow_token, estado, monto FROM pagos ORDER BY creado_en DESC LIMIT 5;"

# Detener todo
docker compose down

# Detener todo y eliminar volúmenes (resetea las BDs)
docker compose down --volumes
```

---

## Documentación adicional

- `docs/SmartLogix_Diagrama_Arquitectura.png` — diagrama de arquitectura completo
- `docs/SmartLogix_Persistencia_de_Datos.pdf` — descripción de la persistencia y recursos JPA por microservicio
- `docs/SmartLogix_Postman_Collection.json` — colección Postman con los 29 endpoints documentados
- `frontend/smartlogix-app/README.md` — instrucciones específicas de instalación, ejecución y prueba del frontend

---

Proyecto semestral — DSY1106 Desarrollo Fullstack III
**Integrantes:** Agustín Mira · Franco Porra
