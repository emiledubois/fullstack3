import axios from "axios";

// URL base del gateway — relativa para que funcione el proxy de vite
const API_BASE = "/api";

const api = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
});

// ── Interceptor de REQUEST: agrega el token JWT a todas las peticiones ──
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || "";
    const isAuthEndpoint = url.includes("/auth/login") || url.includes("/auth/register");

    if (error.response?.status === 401 && !isAuthEndpoint) {
      // Solo redirigir al login si NO estamos en un endpoint de autenticación
      localStorage.removeItem("token");
      window.location.href = "/";
    }
    // Para /auth/* dejar que el catch del componente maneje el error
    return Promise.reject(error);
  }
);

// APIs por módulo

export const authAPI = {
  register: (data) => api.post("/auth/register", data),
  login:    (data) => api.post("/auth/login", data),
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

export default api;
