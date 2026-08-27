import { useState, useEffect } from "react";
import Dashboard     from "./pages/Dashboard";
import Inventario    from "./pages/Inventario";
import Pedidos       from "./pages/Pedidos";
import Envios        from "./pages/Envios";
import Login         from "./pages/Login";
import PagoResultado from "./pages/PagoResultado";
import { authAPI, sessionAPI } from "./services/api";

const NAV = [
  { key:"dashboard",  label:"Dashboard",  icon:"" },
  { key:"inventario", label:"Inventario", icon:"" },
  { key:"pedidos",    label:"Pedidos",    icon:"" },
  { key:"envios",     label:"Envíos",     icon:"" },
];

export default function App() {
  const [page, setPage] = useState("dashboard");
  const [checkingSession, setCheckingSession] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);

  const esPagoResultado = window.location.pathname === "/pago-resultado";

  useEffect(() => {
    // El JWT vive en una cookie httpOnly — ya no es legible desde JS
    // (localStorage.getItem("token")), así que le preguntamos al servidor.
    if (esPagoResultado) {
      setCheckingSession(false);
      return;
    }
    sessionAPI.get()
      .then(() => setAuthenticated(true))
      .catch(() => setAuthenticated(false))
      .finally(() => setCheckingSession(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Interceptar redirección de Flow ANTES de cualquier otra lógica
  if (esPagoResultado) {
    return <PagoResultado />;
  }

  if (checkingSession) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100 text-gray-500 text-sm">
        Cargando...
      </div>
    );
  }

  if (!authenticated) return <Login />;

  const logout = async () => {
    try {
      await authAPI.logout();
    } finally {
      window.location.reload();
    }
  };

  return (
    <div className="flex h-screen bg-gray-100 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-blue-900 text-white flex flex-col shrink-0">
        {/* Logo */}
        <div className="p-5 border-b border-blue-800">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center text-sm"></div>
            <div>
              <div className="font-bold text-sm">SmartLogix</div>
              <div className="text-blue-300 text-xs">Plataforma Logística</div>
            </div>
          </div>
        </div>
        {/* Nav items */}
        <nav className="flex-1 p-3 space-y-1">
          {NAV.map(n => (
            <button
              key={n.key}
              onClick={() => setPage(n.key)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
                          font-medium transition-colors text-left ${
                page === n.key
                  ? "bg-blue-700 text-white"
                  : "text-blue-200 hover:bg-blue-800 hover:text-white"
              }`}
            >
              <span>{n.icon}</span>
              {n.label}
            </button>
          ))}
        </nav>
        {/* User / logout */}
        <div className="p-3 border-t border-blue-800">
          <button onClick={logout}
                  className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm
                             text-blue-300 hover:bg-blue-800 hover:text-white transition-colors">
            <span></span> Cerrar sesión
          </button>
        </div>
      </aside>
      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        {page === "dashboard"  && <Dashboard  onNavigate={setPage} />}
        {page === "inventario" && <Inventario />}
        {page === "pedidos"    && <Pedidos />}
        {page === "envios"     && <Envios />}
      </main>
    </div>
  );
}
