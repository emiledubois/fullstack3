import { useState } from "react";
import Dashboard from "./pages/Dashboard";
import Inventario from "./pages/Inventario";
import Pedidos from "./pages/Pedidos";    // Crear similar a Inventario
import Envios from "./pages/Envios";      // Crear similar a Inventario
import Login from "./pages/Login";

export default function App() {
  const [page, setPage] = useState("dashboard");
  const token = localStorage.getItem("token");
  if (!token) return <Login />;

  const nav = [
    { key:"dashboard", label:"Dashboard" },
    { key:"inventario", label:"Inventario" },
    { key:"pedidos",    label:"Pedidos" },
    { key:"envios",     label:"Envíos" },
  ];

  return (
    <div className="min-h-screen bg-gray-50 flex">
      <aside className="w-56 bg-blue-900 text-white flex flex-col p-4 gap-2">
        <div className="text-xl font-bold mb-6">SmartLogix</div>
        {nav.map(n => (
          <button key={n.key} onClick={() => setPage(n.key)}
                  className={`text-left px-3 py-2 rounded ${page===n.key ? "bg-blue-700" : "hover:bg-blue-800"}`}>
            {n.label}
          </button>
        ))}
        <button onClick={() => { localStorage.removeItem("token"); window.location.reload(); }}
                className="mt-auto text-left px-3 py-2 text-gray-300 hover:text-white">
          Cerrar sesión
        </button>
      </aside>
      <main className="flex-1 overflow-auto">
        {page === "dashboard"  && <Dashboard />}
        {page === "inventario" && <Inventario />}
        {page === "pedidos"    && <Pedidos />}
        {page === "envios"     && <Envios />}
      </main>
    </div>
  );
}
