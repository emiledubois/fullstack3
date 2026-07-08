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
| Resiliencia | Resilience4j 2.2.0 (Circuit Breaker + Rate Limiter) |
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
                    ▼  Webhook (sin JWT, firma verificada en producción)
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
├── .env                          # Credenciales sensibles (no versionado)
├── frontend/
│   └── smartlogix-app/
│       ├── nginx.conf             # Proxy /api/* → api-gateway + manejo de /pago-resultado
│       └── src/
│           ├── App.jsx            # Layout, navegación, intercepta /pago-resultado
│           ├── pages/
│           │   ├── Login.jsx
│           │   ├── Dashboard.jsx       # Una sola llamada a /api/dashboard (BFF)
│           │   ├── Inventario.jsx
│           │   ├── Pedidos.jsx         # Carrito multi-producto, búsqueda por SKU
│           │   ├── Envios.jsx
│           │   └── PagoResultado.jsx   # Página de retorno desde Flow (estados: PAGADO/RECHAZADO/ANULADO/PENDIENTE)
│           └── services/api.js    # Axios + interceptor JWT + dashboardAPI + pagoAPI
└── services/
    ├── api-gateway/              # Spring Cloud Gateway MVC + BFF, puerto 8080
    ├── auth-service/             # JWT auth + Rate Limiter en /login, puerto 8081
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

# Crear archivo .env con las credenciales
cat > .env << 'EOF'
JWT_SECRET=tu_secreto_generado_con_openssl_rand_base64_64
FLOW_API_KEY=tu_api_key_sandbox
FLOW_SECRET_KEY=tu_secret_key_sandbox
FLOW_API_URL=https://sandbox.flow.cl/api
FLOW_URL_CONFIRMATION=https://TU_NGROK.ngrok-free.app/pagos/webhook/flow
FLOW_URL_RETURN=http://localhost:5173/pago-resultado
FLOW_VERIFY_SIGNATURE=false
EOF

# Construir y levantar todos los servicios
docker compose up --build -d

# Verificar que todos los servicios están corriendo
docker compose ps
```

Una vez levantado, acceder a **http://localhost:5173**

## Configuración de ngrok (requerido para pagos con Flow)

Flow necesita una URL pública para enviar el webhook de confirmación de pago. En desarrollo local se usa ngrok para exponer el puerto 8086.

**1. Instalar ngrok**
```bash
# Arch Linux
yay -S ngrok
# O descargar desde https://ngrok.com/download
```

**2. Autenticar ngrok** (solo la primera vez)
```bash
ngrok config add-authtoken TU_AUTHTOKEN
```

**3. Levantar el tunnel antes de iniciar los servicios**
```bash
ngrok http 8086
# Copiar la URL "Forwarding", ejemplo:
# https://zombie-unedited-greyhound.ngrok-free.app
```

**4. Actualizar `.env` con la URL de ngrok**
```bash
# La FLOW_URL_CONFIRMATION debe incluir la ruta completa del webhook:
FLOW_URL_CONFIRMATION=https://TU_URL.ngrok-free.app/pagos/webhook/flow
```

**5. Reiniciar ms-pagos para aplicar el cambio**
```bash
docker compose up -d ms-pagos
```

> **Plan Free de ngrok:** el dominio estático se mantiene entre sesiones (`zombie-unedited-greyhound.ngrok-free.app`). Solo necesitas actualizar `.env` si cambias de cuenta o regeneras el dominio.
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
POST /api/auth/login      → obtener JWT (rate limited: 5 intentos/minuto)
```

### Inventario
```
GET    /api/inventario                                        → listar productos
POST   /api/inventario                                        → crear producto
GET    /api/inventario/{id}                                   → detalle producto
GET    /api/inventario/alertas                                → alertas con estrategia activa
GET    /api/inventario/alertas/estrategia?estrategia=critico  → cambiar estrategia en runtime
GET    /api/inventario/alertas/estrategias                    → listar estrategias disponibles
POST   /api/inventario/{id}/reservar?cantidad=N               → reservar stock (usado por Saga)
POST   /api/inventario/{id}/liberar?cantidad=N                → liberar stock (compensación Saga)
```

### Pedidos
```
GET  /api/pedidos                               → listar pedidos
POST /api/pedidos                               → crear pedido (flujo normal vía Facade)
GET  /api/pedidos/{id}                          → detalle pedido
POST /api/pedidos/{id}/confirmar-pago?token=XXX → confirmar pago y disparar Saga (interno)
POST /api/pedidos/{id}/pago-fallido?estado=XXX  → marcar pago rechazado/anulado (interno)
```

### Saga (transacción distribuida)
```
POST /api/sagas/pedido   → crear pedido con Saga completa (4 pasos + compensaciones)
GET  /sagas/{sagaId}     → consultar estado de una saga (directo a :8083)
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
GET /api/dashboard   → datos agregados de inventario + pedidos + envíos en una sola llamada
                        incluye métricas: valor inventario, pedidos pendientes, envíos en ruta
                        tolerante a fallos parciales: estadoServicios = OK | PARCIAL | ERROR
```

### Pagos — Flow Chile
```
POST /api/pagos/crear        → iniciar orden de pago en Flow, devuelve urlPago
POST /api/pagos/webhook/flow → webhook público de confirmación (sin JWT)
GET  /api/pagos/{id}         → consultar estado de un pago por ID
GET  /api/pagos/por-token/{token} → consultar estado de un pago por flowToken
```

### Health checks
```
GET http://localhost:8080/actuator/health   # gateway
GET http://localhost:8082/actuator/health   # ms-inventario
GET http://localhost:8083/actuator/health   # ms-pedidos
GET http://localhost:8086/actuator/health   # ms-pagos
```

> Colección Postman completa con 29 endpoints disponible en `docs/SmartLogix_Postman_Collection.json`.

Health Check con curl completo
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8081/actuator/health   # auth-service
curl http://localhost:8082/actuator/health   # ms-inventario
curl http://localhost:8083/actuator/health   # ms-pedidos
curl http://localhost:8084/actuator/health   # ms-envios
curl http://localhost:8085/actuator/health   # notification-service 
curl http://localhost:8086/actuator/health   # ms-pagos
---

## Patrón Strategy — Cambio de estrategia en runtime

```bash
# Umbral fijo (por defecto): alerta cuando stock < umbralMinimo
curl http://localhost:8080/api/inventario/alertas -H "Authorization: Bearer $TOKEN"

# Porcentaje: alerta cuando stock < umbralMinimo * 1.5
curl "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=porcentaje" \
  -H "Authorization: Bearer $TOKEN"

# Crítico: alerta solo cuando stock == 0
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

# Simular fallo en Paso 3 — compensaciones automáticas en orden inverso
docker compose stop ms-envios
# ejecutar saga → stock restaurado + pedido CANCELADO automáticamente
docker compose start ms-envios

# Auditar sagas en BD
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
  -c "SELECT saga_id, estado, pedido_id, envio_id FROM saga_estado ORDER BY actualizado_en DESC LIMIT 5;"
```

---

## Integración de pagos — Flow Chile

El flujo completo de pago integrado con Flow Chile:

1. El frontend crea un pedido y solicita pago con Flow → `ms-pedidos` registra estado `PENDIENTE_PAGO`.
2. `ms-pedidos` llama a `ms-pagos`, que firma los parámetros con HMAC-SHA256 y crea la orden en Flow.
3. El frontend redirige al usuario al checkout de Flow.
4. Flow notifica el resultado vía webhook a `POST /api/pagos/webhook/flow` (ruta pública, sin JWT).
5. `ms-pagos` consulta el estado real con `GET /payment/getStatus` y notifica a `ms-pedidos`.
6. Si el pago fue exitoso, `ms-pedidos` dispara la Saga de logística completa (4 pasos).
7. Flow redirige al usuario a `/pago-resultado`, que muestra el resultado con diseño profesional.

```bash
# Script de prueba completo del flujo
fish run_tests.fish
```

### Tarjetas de prueba (Sandbox)

| Tipo | Número | Resultado |
|------|--------|-----------|
| Visa exitosa | 4051885600446623 | Status 2 — Pagado |
| Visa rechazada | 4051885600446631 | Status 3 — Rechazado |
| RUT / Clave | 11.111.111-1 / 123 | — |
| Monto mínimo | $350 CLP | Menores son rechazados |

---

## WebHub / BFF — Dashboard agregado

```bash
# Una sola petición reemplaza 4 llamadas independientes
curl http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"

# Demostrar tolerancia a fallos parciales
docker compose stop ms-envios
curl http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
# Responde igual: ultimosEnvios:[], estadoServicios:"PARCIAL"
docker compose start ms-envios
```

---

## Pruebas unitarias

**17 pruebas unitarias** con JUnit 5 + Mockito, estructura **AAA (Arrange, Act, Assert)**:

| Microservicio | Clases de prueba | Tests | Patrón cubierto |
|---|---|---|---|
| ms-pedidos | OrderServiceTest, OrderControllerTest, SagaOrchestratorTest, SagaControllerTest | 9 | Factory Method, Facade, Saga |
| ms-inventario | AlertaServiceTest, InventarioControllerTest | 4 | Strategy |
| ms-pagos | PagoServiceTest, FlowServiceTest, PagoControllerTest | 5 | Integración Flow, HMAC-SHA256 |

```bash
# Ejecutar todos los tests de una vez (fish shell)
set BASE /ruta/completa/ecommerce_microservices/services
for svc in ms-pedidos ms-inventario ms-pagos
    echo "=== $svc ==="
    cd $BASE/$svc
    bash mvnw test 2>&1 | grep -E "Tests run.*Time|BUILD"
end
```

**Resultado esperado:** 17 tests · 0 fallos · 0 errores · `BUILD SUCCESS` en los 3 servicios.

---

## Seguridad implementada

| Medida | Descripción |
|--------|-------------|
| JWT sin fallback hardcodeado | `jwt.secret=${JWT_SECRET}` — falla al arrancar si no está configurado |
| Firma HMAC-SHA256 obligatoria | Webhook de Flow verifica firma en producción (`FLOW_VERIFY_SIGNATURE=true`) |
| Rate limiting en login | Resilience4j: máximo 5 intentos/60s por instancia, responde 429 |
| CORS restringido | Headers permitidos explícitos, sin wildcard `*` |
| Idempotencia en webhook | Pagos ya procesados se ignoran silenciosamente (evita doble cobro) |
| Comparación de tiempo constante | `MessageDigest.isEqual()` en verificación HMAC evita timing attacks |
| `.env` fuera del repositorio | Credenciales nunca versionadas |

---

## Variables de entorno

| Variable | Servicio | Descripción |
|----------|----------|-------------|
| `JWT_SECRET` | api-gateway, auth-service | Secreto HMAC-SHA256 para JWT (generar con `openssl rand -base64 64`) |
| `DB_HOST` / `DB_NAME` | Todos los servicios con BD | Host y nombre de BD (nombre del contenedor Docker) |
| `INVENTARIO_URL` | ms-pedidos, api-gateway | URL de ms-inventario |
| `ENVIOS_URL` | ms-pedidos, api-gateway | URL de ms-envios |
| `NOTIFICATION_URL` | ms-pedidos | URL de notification-service |
| `PEDIDOS_URL` | ms-pagos, api-gateway | URL de ms-pedidos |
| `PAGOS_URL` | api-gateway | URL de ms-pagos |
| `FLOW_API_URL` | ms-pagos | URL de la API de Flow (sandbox o producción) |
| `FLOW_API_KEY` | ms-pagos | API Key del comercio en Flow |
| `FLOW_SECRET_KEY` | ms-pagos | Secret Key para firma HMAC-SHA256 |
| `FLOW_URL_CONFIRMATION` | ms-pagos | URL pública del webhook (ngrok en desarrollo) |
| `FLOW_URL_RETURN` | ms-pagos | URL del frontend para retorno post-pago |
| `FLOW_VERIFY_SIGNATURE` | ms-pagos | `false` en sandbox, `true` en producción |

---

## Comandos útiles

```bash
# Reconstruir un servicio específico
docker compose build ms-pagos && docker compose up -d ms-pagos

# Ver logs de un servicio
docker compose logs ms-pagos --tail=50 -f

# Health check de todos los servicios
for port in 8080 8082 8083 8086; do
  echo -n "Puerto $port: "
  curl -s http://localhost:$port/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])"
done

# Auditar pagos en BD
docker compose exec postgres-pagos psql -U postgres pagos_db \
  -c "SELECT id, pedido_id, estado, monto FROM pagos ORDER BY id DESC LIMIT 5;"

# Auditar sagas en BD
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
  -c "SELECT saga_id, estado, pedido_id, envio_id FROM saga_estado ORDER BY actualizado_en DESC LIMIT 5;"

# Detener todo
docker compose down

# Detener y eliminar volúmenes (resetea las BDs)
docker compose down --volumes
```

---

## Documentación adicional

| Archivo | Descripción |
|---------|-------------|
| `docs/SmartLogix_Diagrama_Arquitectura.png` | Diagrama de arquitectura completo |
| `docs/SmartLogix_Persistencia_de_Datos.pdf` | Descripción de persistencia y recursos JPA |
| `docs/SmartLogix_Postman_Collection.json` | Colección Postman con los 29 endpoints |
| `frontend/smartlogix-app/README.md` | Instrucciones del componente frontend |
| `run_tests.fish` | Script de prueba integración Flow (end-to-end) |

---

