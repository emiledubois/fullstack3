import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" }
});

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem("token");
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

api.interceptors.response.use(r => r, err => {
  if (err.response?.status === 401) { localStorage.removeItem("token"); window.location.href = "/login"; }
  return Promise.reject(err);
});

export default api;
export const authAPI      = { login: d => api.post("/auth/login", d), register: d => api.post("/auth/register", d) };
export const inventarioAPI = { getAll: () => api.get("/inventario"), create: d => api.post("/inventario", d), getAlertas: () => api.get("/inventario/alertas") };
export const pedidosAPI    = { getAll: () => api.get("/pedidos"), create: d => api.post("/pedidos", d) };
export const enviosAPI     = { getAll: () => api.get("/envios"), create: d => api.post("/envios", d), updateStatus: (id, s) => api.patch(`/envios/${id}/status?status=${s}`) };
