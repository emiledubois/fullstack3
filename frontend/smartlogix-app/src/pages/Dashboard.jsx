import { useState, useEffect } from "react";
import { dashboardAPI } from "../services/api";
import { Package, ShoppingCart, Truck, Bell, Clock, Route, DollarSign, AlertTriangle, RefreshCw } from "lucide-react";
import StatusBadge from "../components/StatusBadge";

const TONE_CLASSES = {
  brand:   { chip: "bg-brand-50 text-brand-700",     card: "border-line-200" },
  success: { chip: "bg-success-bg text-success-text", card: "border-line-200" },
  accent:  { chip: "bg-accent-bg text-accent-text",   card: "border-line-200" },
  warning: { chip: "bg-warning-bg text-warning-text", card: "border-line-200" },
  info:    { chip: "bg-info-bg text-info-text",       card: "border-line-200" },
  danger:  { chip: "bg-danger-bg text-danger-text",   card: "border-danger-border" },
};

const StatCard = ({ label, value, icon: Icon, tone, sub }) => {
  const t = TONE_CLASSES[tone] || TONE_CLASSES.brand;
  const isDanger = tone === "danger";
  return (
    <div className={`rounded-card bg-white border ${t.card} p-5 flex items-start justify-between`}>
      <div>
        <p className={`text-sm font-medium ${isDanger ? "text-danger-strongText" : "text-ink-400"}`}>{label}</p>
        <p className={`font-heading text-4xl font-bold mt-1 ${isDanger ? "text-danger-strong" : "text-ink-900"}`}>{value}</p>
        {sub && <p className="text-xs text-ink-400 mt-1">{sub}</p>}
      </div>
      <div className={`w-10 h-10 rounded-nav flex items-center justify-center shrink-0 ${t.chip}`}>
        <Icon className="w-5 h-5" strokeWidth={1.5} />
      </div>
    </div>
  );
};

const PEDIDO_TONE = {
  PENDIENTE:  "warning",
  CONFIRMADO: "chip",
  APROBADO:   "success",
  EN_ENVIO:   "info",
  ENTREGADO:  "chip",
  CANCELADO:  "danger",
};

const ESTADO_TONE = { OK: "success", PARCIAL: "warning" };

export default function Dashboard({ onNavigate }) {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(false);
  const [estado,  setEstado]  = useState("OK");

  const fetchData = () => {
    dashboardAPI.get()
      .then(res => {
        setData(res.data);
        setEstado(res.data.estadoServicios ?? "OK");
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, []);

  const retry = () => {
    setLoading(true);
    setError(false);
    fetchData();
  };

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <div className="text-center">
        <div className="w-10 h-10 border-4 border-brand-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"/>
        <p className="text-ink-400 text-sm">Cargando datos...</p>
      </div>
    </div>
  );

  if (error) return (
    <div className="p-8 px-9 max-w-2xl mx-auto">
      <div className="bg-white rounded-card border border-line-200 p-8 text-center">
        <AlertTriangle className="w-8 h-8 text-danger-text mx-auto mb-3" />
        <p className="text-ink-700 mb-4">No pudimos cargar el dashboard en este momento. Intenta nuevamente en unos minutos.</p>
        <button onClick={retry}
                className="inline-flex items-center gap-2 bg-brand-600 hover:bg-brand-700 text-white text-sm
                           font-medium px-4 py-2 rounded-nav transition-colors">
          <RefreshCw className="w-4 h-4" /> Reintentar
        </button>
      </div>
    </div>
  );

  const m = data?.metricas ?? {};

  return (
    <div className="p-8 px-9 max-w-6xl mx-auto">
      {/* Header con estado del BFF */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink-900">Dashboard</h1>
          <p className="text-ink-400 text-sm">Resumen operacional de SmartLogix</p>
        </div>
        <StatusBadge tone={ESTADO_TONE[estado] || "danger"}>
          Servicios: {estado}
          {data?.generadoEn && (
            <span className="ml-2 opacity-60">
              {new Date(data.generadoEn).toLocaleTimeString()}
            </span>
          )}
        </StatusBadge>
      </div>

      {/* Stats cards — iconos profesionales */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Productos"     value={m.totalProductos ?? 0}        icon={Package} tone="brand" />
        <StatCard label="Pedidos"       value={m.totalPedidos ?? 0}          icon={ShoppingCart} tone="success" />
        <StatCard label="Envíos"        value={m.totalEnvios ?? 0}           icon={Truck} tone="accent" />
        <StatCard label="⚠ Bajo Stock" value={m.productosConAlertaStock ?? 0} icon={Bell} tone="danger"
          sub={m.productosConAlertaStock > 0 ? "Revisar inventario" : "Todo en orden"} />
      </div>

      {/* Segunda fila de métricas */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <StatCard label="Pendientes"    value={m.pedidosPendientes ?? 0}  icon={Clock} tone="warning" />
        <StatCard label="Envíos en Ruta" value={m.enviosEnRuta ?? 0}      icon={Route} tone="info" />
        <StatCard label="Valor Inventario" icon={DollarSign} tone="accent"
          value={`$${(m.valorTotalInventario ?? 0).toLocaleString()}`}
          sub="precio × stock de todos los productos" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Últimos pedidos */}
        <div className="bg-white rounded-card border border-line-200">
          <div className="p-4 border-b border-line-150 flex justify-between items-center">
            <h2 className="font-semibold text-ink-800">Últimos pedidos</h2>
            <button onClick={() => onNavigate?.("pedidos")} className="text-xs text-brand-600 hover:underline">Ver todos →</button>
          </div>
          <div className="divide-y divide-line-100">
            {(data?.ultimosPedidos ?? []).length === 0 && (
              <p className="text-center text-ink-300 text-sm py-6">Sin pedidos aún</p>
            )}
            {(data?.ultimosPedidos ?? []).map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-700">{p.clienteNombre || "Sin nombre"}</p>
                  <p className="text-xs text-ink-400">{p.creadoEn?.substring(0,10)} · {p.tipoPedido}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold text-ink-700">${p.total?.toLocaleString()}</p>
                  <StatusBadge tone={PEDIDO_TONE[p.status] || "chip"}>{p.status}</StatusBadge>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Alertas de stock */}
        <div className="bg-white rounded-card border border-line-200">
          <div className="p-4 border-b border-line-150 flex justify-between items-center">
            <h2 className="font-semibold text-ink-800">Alertas de stock bajo</h2>
            <button onClick={() => onNavigate?.("inventario")} className="text-xs text-brand-600 hover:underline">Ir al inventario →</button>
          </div>
          <div className="divide-y divide-line-100">
            {(data?.alertasStock ?? []).length === 0 && (
              <p className="text-center text-ink-300 text-sm py-6">✅ Sin alertas de stock</p>
            )}
            {(data?.alertasStock ?? []).map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-700">{p.nombre}</p>
                  <p className="text-xs text-ink-400">SKU: {p.sku} · {p.bodega}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-danger-strong">{p.stockActual} unid.</p>
                  <p className="text-xs text-ink-400">mín: {p.umbralMinimo}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
