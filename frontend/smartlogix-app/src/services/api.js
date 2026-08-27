import axios from "axios";

// URL base del gateway — relativa para que funcione el proxy de vite
const API_BASE = "/api";

const api = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
  // El JWT ahora vive en una cookie httpOnly (sl_jwt) fijada por auth-service
  // — el navegador la adjunta solo si withCredentials está activo. JS nunca
  // la lee ni la fija manualmente.
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || "";
    // /auth/* y /session manejan su propio 401 esperado (login fallido,
    // "todavía no hay sesión" respectivamente) — no forzar redirección ahí.
    const isAuthEndpoint = url.includes("/auth/login") || url.includes("/auth/register");
    const isSessionEndpoint = url.includes("/session");

    if (error.response?.status === 401 && !isAuthEndpoint && !isSessionEndpoint) {
      // Cookie ausente/expirada en cualquier otra llamada — forzar re-login
      window.location.href = "/";
    }
    return Promise.reject(error);
  }
);

// APIs por módulo

export const authAPI = {
  register: (data) => api.post("/auth/register", data),
  login:    (data) => api.post("/auth/login", data),
  logout:   ()     => api.post("/auth/logout"),
};

export const sessionAPI = {
  get: () => api.get("/session"),
};

export const inventarioAPI = {
  getAll:     ()     => api.get("/inventario"),
  getById:    (id)   => api.get(`/inventario/${id}`),
  create:     (data) => api.post("/inventario", data),
  getAlertas: ()     => api.get("/inventario/alertas"),
  getAlertasEstrategia: (e) => api.get(`/inventario/alertas/estrategia?estrategia=${e}`),
  getEstrategias: ()  => api.get("/inventario/alertas/estrategias"),
};

export const pedidosAPI = {
  getAll:  ()     => api.get("/pedidos"),
  getById: (id)   => api.get(`/pedidos/${id}`),
  create:  (data) => api.post("/pedidos", data),
};

export const enviosAPI = {
  getAll:       ()          => api.get("/envios"),
  getById:      (id)        => api.get(`/envios/${id}`),
  create:       (data)      => api.post("/envios", data),
  updateStatus: (id, status)=> api.patch(`/envios/${id}/status?status=${status}`),
};

export const sagaAPI = {
  crearPedido: (data) => api.post("/sagas/pedido", data),
};

export const dashboardAPI = {
  get: () => api.get("/dashboard"),
};

export const pagoAPI = {
  crearPago: (data) => api.post("/pagos/crear", data),
  consultarPago: (id) => api.get(`/pagos/${id}`),
};

// Formato moneda chilena: $12.500
export const formatCLP = (monto) =>
  new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0
  }).format(monto ?? 0);

// Formato fecha en zona horaria Chile
export const formatFechaChile = (fecha) =>
  fecha ? new Date(fecha).toLocaleString("es-CL", {
    timeZone: "America/Santiago",
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  }) : "—";


export default api;
