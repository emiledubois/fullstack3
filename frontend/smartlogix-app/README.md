# SmartLogix — Componente Frontend

Interfaz web de SmartLogix, plataforma de gestión logística para PYMEs. Construida con React 18, Vite y Tailwind CSS. Consume la API REST expuesta por el `api-gateway` del backend (puerto 8080), incluyendo el endpoint agregador `/api/dashboard` (patrón BFF).

---

## Tecnologías

| Tecnología | Versión | Uso |
|---|---|---|
| React | 18.x | Librería de componentes UI |
| Vite | 5.x | Dev server y build tool |
| Tailwind CSS | 3.x | Estilos utilitarios |
| Axios | 1.x | Cliente HTTP con interceptor de JWT |
| Nginx | 1.25-alpine | Servidor de producción / proxy reverso |

---

## Estructura del proyecto

```
smartlogix-app/
├── package.json
├── vite.config.js
├── tailwind.config.js
├── nginx.conf              # Proxy /api/* → api-gateway (solo producción)
├── Dockerfile
├── index.html
├── public/
└── src/
    ├── main.jsx             # Punto de entrada
    ├── App.jsx               # Layout principal, navegación y rutas
    ├── pages/
    │   ├── Login.jsx
    │   ├── Dashboard.jsx      # Consume /api/dashboard (BFF)
    │   ├── Inventario.jsx
    │   ├── Pedidos.jsx
    │   ├── Envios.jsx
    │   └── PagoResultado.jsx  # Redirección de Flow tras el pago
    └── services/
        └── api.js             # Instancia de Axios + interceptor JWT
```

---

## Requisitos previos

- Node.js 18 o superior
- npm 9 o superior
- El backend (`api-gateway` y microservicios) corriendo en `http://localhost:8080` — ver el README del repositorio principal para levantar el backend con Docker Compose.

Verificar versiones instaladas:

```bash
node -v
npm -v
```

---

## Instalación

```bash
# Desde la carpeta del frontend
cd frontend/smartlogix-app

# Instalar dependencias
npm install
```

---

## Variables de entorno

El frontend en modo desarrollo asume que el backend está disponible en `http://localhost:8080`. Si se necesita apuntar a otra URL, crear un archivo `.env`:

```bash
# .env (opcional, solo si el backend no está en localhost:8080)
VITE_API_URL=http://localhost:8080
```

En producción (contenedor Docker), Nginx se encarga de redirigir `/api/*` hacia `api-gateway:8080` dentro de la red interna de Docker — no se necesita configurar URL alguna en el build.

---

## Ejecución en modo desarrollo

```bash
npm run dev
```

La aplicación queda disponible en `http://localhost:5173` con recarga instantánea (HMR) ante cualquier cambio en el código fuente.

**Importante:** el backend debe estar corriendo antes de iniciar el frontend, o el login y la carga de datos fallarán. Para levantar el backend completo:

```bash
# Desde la raíz del repositorio principal
docker compose up -d
```

---

## Build de producción

```bash
npm run build
```

Genera los archivos estáticos optimizados en `dist/`. Para construir y ejecutar el contenedor Docker completo (incluye Nginx):

```bash
# Desde la raíz del proyecto
docker compose build frontend
docker compose up -d frontend
```

---

## Cómo probar el frontend

El proyecto no incluye aún pruebas automatizadas de frontend (unit/E2E) — la validación se realiza de forma manual siguiendo este flujo:

1. **Login:** registrar un usuario vía `POST /api/auth/register` (con curl o desde la misma pantalla de login) y luego iniciar sesión.
2. **Dashboard:** verificar que las métricas y los paneles de pedidos/alertas carguen correctamente desde una sola petición a `/api/dashboard` (revisar la pestaña Network de DevTools — debe verse una sola llamada en lugar de varias).
3. **Inventario:** crear un producto y comprobar que aparece en la tabla y en las alertas de stock si su cantidad está bajo el umbral mínimo.
4. **Pedidos:** crear un pedido seleccionando productos por SKU, confirmar que el carrito calcula el total correctamente, y verificar el detalle del pedido creado.
5. **Pago (Flow):** crear un pedido con método de pago Flow, confirmar la redirección al checkout de Flow Sandbox, y verificar que `/pago-resultado` muestra el estado final tras completar el pago con una tarjeta de prueba.
6. **Envíos:** verificar que el envío asociado a un pedido cambia de estado correctamente.

Para una guía detallada de pruebas de extremo a extremo del sistema completo (incluyendo backend), ver `test_smartlogix_v2.fish` en la raíz del repositorio principal.

---

## Notas de diseño

- No se usa React Router: la navegación entre páginas internas (`Dashboard`, `Inventario`, `Pedidos`, `Envios`) se maneja con un estado local (`page`) en `App.jsx`. La única excepción es la ruta `/pago-resultado`, detectada mediante `window.location.pathname` para interceptar la redirección que hace Flow tras el pago.
- El interceptor de Axios en `services/api.js` adjunta automáticamente el header `Authorization: Bearer <token>` en cada petición saliente, leyendo el JWT desde `localStorage`.
- Las llamadas a productos en `Pedidos.jsx` permiten búsqueda por SKU o nombre antes de agregar al carrito, evitando que el usuario deba conocer IDs numéricos internos.
