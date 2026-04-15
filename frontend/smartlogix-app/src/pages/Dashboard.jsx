import { useState, useEffect } from "react";
import { inventarioAPI, pedidosAPI, enviosAPI } from "../services/api";

export default function Dashboard() {
  const [stats, setStats] = useState({ productos: 0, pedidos: 0, envios: 0, alertas: 0 });

  useEffect(() => {
    Promise.all([
      inventarioAPI.getAll(),
      pedidosAPI.getAll(),
      enviosAPI.getAll(),
      inventarioAPI.getAlertas()
    ]).then(([inv, ped, env, alertas]) => setStats({
      productos: inv.data.length,
      pedidos: ped.data.length,
      envios: env.data.length,
      alertas: alertas.data.length
    })).catch(console.error);
  }, []);

  return (
    <div className="p-6">
      <h1 className="text-3xl font-bold text-blue-900 mb-6">SmartLogix Dashboard</h1>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {[
          { label: "Productos", val: stats.productos, color: "bg-blue-100 text-blue-800" },
          { label: "Pedidos",   val: stats.pedidos,   color: "bg-green-100 text-green-800" },
          { label: "Envíos",    val: stats.envios,    color: "bg-purple-100 text-purple-800" },
          { label: "⚠ Alertas Stock", val: stats.alertas, color: "bg-red-100 text-red-800" },
        ].map(s => (
          <div key={s.label} className={`rounded-xl p-5 ${s.color}`}>
            <p className="text-sm font-medium">{s.label}</p>
            <p className="text-3xl font-bold">{s.val}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
