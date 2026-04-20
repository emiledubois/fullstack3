import { useState, useEffect } from "react";
import { inventarioAPI, pedidosAPI, enviosAPI } from "../services/api";

const StatCard = ({ label, value, icon, color, sub }) => (
  <div className={`rounded-xl p-5 ${color} flex items-start justify-between`}>
    <div>
      <p className="text-sm font-medium opacity-75">{label}</p>
      <p className="text-4xl font-bold mt-1">{value}</p>
      {sub && <p className="text-xs opacity-60 mt-1">{sub}</p>}
    </div>
    <span className="text-3xl opacity-80">{icon}</span>
  </div>
);

export default function Dashboard({ onNavigate }) {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      inventarioAPI.getAll(),
      pedidosAPI.getAll(),
      enviosAPI.getAll(),
      inventarioAPI.getAlertas(),
    ])
    .then(([inv, ped, env, alertas]) => setData({
      productos: inv.data,
      pedidos:   ped.data,
      envios:    env.data,
      alertas:   alertas.data,
    }))
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

  const stats = {
    productos: data?.productos?.length ?? 0,
    pedidos:   data?.pedidos?.length   ?? 0,
    envios:    data?.envios?.length     ?? 0,
    alertas:   data?.alertas?.length    ?? 0,
  };

  const ultimosPedidos = (data?.pedidos ?? []).slice(-5).reverse();
  const ultimosEnvios  = (data?.envios  ?? []).slice(-3).reverse();

  const statusColorPed = s => ({
    PENDIENTE:"bg-yellow-100 text-yellow-700",
    APROBADO:"bg-green-100 text-green-700",
    EN_ENVIO:"bg-blue-100 text-blue-700",
    ENTREGADO:"bg-gray-100 text-gray-600",
    CANCELADO:"bg-red-100 text-red-700"
  })[s] || "bg-gray-100 text-gray-600";

  return (
    <div className="p-6 max-w-6xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 text-sm mt-1">Resumen operacional de SmartLogix</p>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Productos"      value={stats.productos} icon="" color="bg-blue-50 text-blue-800"   />
        <StatCard label="Pedidos"        value={stats.pedidos}   icon="" color="bg-green-50 text-green-800"  />
        <StatCard label="Envíos"         value={stats.envios}    icon="" color="bg-purple-50 text-purple-800"/>
        <StatCard label="⚠ Bajo Stock"  value={stats.alertas}   icon="" color="bg-red-50 text-red-800"
                  sub={stats.alertas > 0 ? "Revisar inventario" : "Todo en orden"} />
      </div>

      {/* Two-column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

        {/* Últimos pedidos */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100">
          <div className="p-4 border-b border-gray-100 flex justify-between items-center">
            <h2 className="font-semibold text-gray-800">Últimos pedidos</h2>
            <button onClick={() => onNavigate?.("pedidos")}
                    className="text-xs text-blue-600 hover:underline">Ver todos →</button>
          </div>
          <div className="divide-y divide-gray-50">
            {ultimosPedidos.length === 0 && (
              <p className="text-center text-gray-400 text-sm py-6">Sin pedidos aún</p>
            )}
            {ultimosPedidos.map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-800">{p.clienteNombre || "Sin nombre"}</p>
                  <p className="text-xs text-gray-400">{p.creadoEn?.substring(0,10)} · {p.tipoPedido}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold text-gray-700">${p.total?.toLocaleString()}</p>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${statusColorPed(p.status)}`}>
                    {p.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Alertas de stock */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100">
          <div className="p-4 border-b border-gray-100 flex justify-between items-center">
            <h2 className="font-semibold text-gray-800">Alertas de stock bajo</h2>
            <button onClick={() => onNavigate?.("inventario")}
                    className="text-xs text-blue-600 hover:underline">Ir a inventario →</button>
          </div>
          <div className="divide-y divide-gray-50">
            {data?.alertas?.length === 0 && (
              <p className="text-center text-gray-400 text-sm py-6"> Sin alertas de stock</p>
            )}
            {data?.alertas?.map(p => (
              <div key={p.id} className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-800">{p.nombre}</p>
                  <p className="text-xs text-gray-400">SKU: {p.sku} · Bodega: {p.bodega}</p>
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
