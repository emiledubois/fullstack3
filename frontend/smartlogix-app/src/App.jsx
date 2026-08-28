import { useState, useEffect } from "react";
import Dashboard     from "./pages/Dashboard";
import Inventario    from "./pages/Inventario";
import Pedidos       from "./pages/Pedidos";
import Envios        from "./pages/Envios";
import MisDatos      from "./pages/MisDatos";
import Login         from "./pages/Login";
import PagoResultado from "./pages/PagoResultado";
import Sidebar       from "./components/Sidebar";
import { authAPI, sessionAPI } from "./services/api";

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
      <div className="min-h-screen flex items-center justify-center bg-app-bg text-ink-400 text-sm">
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
    <div className="flex h-screen bg-app-bg overflow-hidden">
      <Sidebar active={page} onNavigate={setPage} onLogout={logout} />
      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        {page === "dashboard"  && <Dashboard  onNavigate={setPage} />}
        {page === "inventario" && <Inventario />}
        {page === "pedidos"    && <Pedidos />}
        {page === "envios"     && <Envios />}
        {page === "mis-datos"  && <MisDatos />}
      </main>
    </div>
  );
}
