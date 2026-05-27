import { useState, useEffect } from "react";
import { dashboardAPI } from "../services/api";
import { Package, Box, ShoppingCart, Truck, Bell, Clock, Route, DollarSign } from "lucide-react";

const StatCard = ({ label, value, icon: Icon, color, sub }) => (
  <div className={`rounded-xl p-5 ${color} flex items-start justify-between`}>
    <div>
      <p className="text-sm font-medium opacity-75">{label}</p>
      <p className="text-4xl font-bold mt-1">{value}</p>
      {sub && <p className="text-xs opacity-60 mt-1">{sub}</p>}
    </div>
    <Icon className="w-7 h-7 opacity-80" strokeWidth={1.5} />
  </div>
);

export default function Dashboard({ onNavigate }) {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);
  const [estado,  setEstado]  = useState("OK");

  useEffect(() => {
    dashboardAPI.get()
      .then(res => {
        setData(res.data);
        setEstado(res.data.estadoServicios ?? "OK");
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <div className="text-center">
        <div className="w-10 h-10 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"/>
        <p className="text-gray-500 text-sm">Cargando datos...</p>
      </div>
    </div>
  );

  const m = data?.metricas ?? {};

  const statusColorPed = s => ({
    PENDIENTE:"bg-yellow-100 text-yellow-700",
    CONFIRMADO:"bg-teal-100 text-teal-700",
    APROBADO:"bg-green-100 text-green-700",
    EN_ENVIO:"bg-blue-100 text-blue-700",
    ENTREGADO:"bg-gray-100 text-gray-600",
    CANCELADO:"bg-red-100 text-red-700"
  })[s] || "bg-gray-100 text-gray-600";

  return (
    <div className="p-6 max-w-6xl mx-auto">
      {/* Header con estado del BFF */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 text-sm">Resumen operacional de SmartLogix</p>
        </div>
        <div className={`text-xs px-3 py-1 rounded-full font-medium ${
          estado === "OK"      ? "bg-green-100 text-green-700"
        : estado === "PARCIAL" ? "bg-yellow-100 text-yellow-700"
        : "bg-red-100 text-red-700"
        }`}>
          Servicios: {estado}
          {data?.generadoEn && (
            <span className="ml-2 opacity-60">
              {new Date(data.generadoEn).toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {/* Stats cards — iconos profesionales */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Productos"     value={m.totalProductos ?? 0}        icon={Package} color="bg-blue-50 text-blue-800" />
        <StatCard label="Pedidos"       value={m.totalPedidos ?? 0}          icon={ShoppingCart} color="bg-green-50 text-green-800" />
        <StatCard label="Envíos"        value={m.totalEnvios ?? 0}           icon={Truck} color="bg-purple-50 text-purple-800" />
        <StatCard label="⚠ Bajo Stock" value={m.productosConAlertaStock ?? 0} icon={Bell} color="bg-red-50 text-red-800"
          sub={m.productosConAlertaStock > 0 ? "Revisar inventario" : "Todo en orden"} />
      </div>

      {/* Segunda fila de métricas */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <StatCard label="Pendientes"    value={m.pedidosPendientes ?? 0}  icon={Clock} color="bg-yellow-50 text-yellow-800" />
        <StatCard label="Envíos en Ruta" value={m.enviosEnRuta ?? 0}      icon={Route} color="bg-indigo-50 text-indigo-800" />
        <StatCard label="Valor Inventario" icon={DollarSign} color="bg-emerald-50 text-emerald-800"
          value={`$${(m.valorTotalInventario ?? 0).toLocaleString()}`}
          sub="precio × stock de todos los productos" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Últimos pedidos */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100">
          <div className="p-4 border-b border-gray-100 flex justify-between items-center">
            <h2 className="font-semibold text-gray-800">Últimos pedidos</h2>
            <button onClick={() => onNavigate?.("pedidos")} className="text-xs text-blue-600 hover:underline">Ver todos →</button>
          </div>
          <div className="divide-y divide-gray-50">
            {(data?.ultimosPedidos ?? []).length === 0 && (
              <p className="text-center text-gray-400 text-sm py-6">Sin pedidos aún</p>
            )}
            {(data?.ultimosPedidos ?? []).map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-800">{p.clienteNombre || "Sin nombre"}</p>
                  <p className="text-xs text-gray-400">{p.creadoEn?.substring(0,10)} · {p.tipoPedido}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold text-gray-700">${p.total?.toLocaleString()}</p>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${statusColorPed(p.status)}`}>{p.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Alertas de stock */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100">
          <div className="p-4 border-b border-gray-100 flex justify-between items-center">
            <h2 className="font-semibold text-gray-800">Alertas de stock bajo</h2>
            <button onClick={() => onNavigate?.("inventario")} className="text-xs text-blue-600 hover:underline">Ir al inventario →</button>
          </div>
          <div className="divide-y divide-gray-50">
            {(data?.alertasStock ?? []).length === 0 && (
              <p className="text-center text-gray-400 text-sm py-6">✅ Sin alertas de stock</p>
            )}
            {(data?.alertasStock ?? []).map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-800">{p.nombre}</p>
                  <p className="text-xs text-gray-400">SKU: {p.sku} · {p.bodega}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-red-600">{p.stockActual} unid.</p>
                  <p className="text-xs text-gray-400">mín: {p.umbralMinimo}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
