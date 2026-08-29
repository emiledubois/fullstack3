import { useState } from "react";
import { authAPI } from "../services/api";

export default function Login() {
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [isReg,    setIsReg]    = useState(false);
  const [msg,      setMsg]      = useState(null);
  const [loading,  setLoading]  = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setMsg({ text: "Completa todos los campos", type: "error" });
      return;
    }
    setLoading(true);
    setMsg(null);

    try {
      if (isReg) {
        // ── REGISTRO ──
        await authAPI.register({ email, password });
        setMsg({ text: "¡Cuenta creada! Ahora inicia sesión.", type: "success" });
        setIsReg(false);
        setPassword("");
      } else {
        // ── LOGIN ──
        // El JWT viaja en una cookie httpOnly (Set-Cookie: sl_jwt) que el
        // navegador fija solo; el body ya no lo contiene y JS nunca lo toca.
        await authAPI.login({ email, password });
        window.location.reload(); // recarga para que App.jsx consulte /api/session
      }
    } catch (err) {
      // El error puede venir del backend (err.response.data) o ser local
      const backendMsg = err.response?.data;
      const errorText =
        typeof backendMsg === "string" ? backendMsg
        : backendMsg?.message ? backendMsg.message
        : err.message || "Error de autenticación";
      setMsg({ text: errorText, type: "error" });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-brand-900 to-brand-700 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="font-heading text-3xl font-bold text-white">SmartLogix</h1>
          <p className="text-white/70 text-sm mt-1">Plataforma de Gestión Logística</p>
        </div>
        <div className="bg-white rounded-login-card shadow-2xl p-8">
          <h2 className="font-heading text-xl font-semibold text-ink-900 mb-6">
            {isReg ? "Crear cuenta" : "Iniciar sesión"}
          </h2>
          {msg && (
            <div className={`rounded-input p-3 mb-4 text-sm font-medium ${
              msg.type === "error"
                ? "bg-danger-bg text-danger-text border border-danger-border"
                : "bg-success-bg text-success-text border border-success-text/20"
            }`}>{msg.text}</div>
          )}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="login-email" className="block text-sm font-medium text-ink-600 mb-1">Email</label>
              <input id="login-email" type="email" value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="admin@smartlogix.cl"
                className="w-full border border-line-500 rounded-input px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-600"
                required />
            </div>
            <div>
              <label htmlFor="login-password" className="block text-sm font-medium text-ink-600 mb-1">Contraseña</label>
              <input id="login-password" type="password" value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full border border-line-500 rounded-input px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-600"
                required />
            </div>
            <button type="submit" disabled={loading}
              className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-nav transition-colors">
              {loading ? "Procesando..." : (isReg ? "Crear cuenta" : "Ingresar")}
            </button>
          </form>
          <div className="mt-4 text-center">
            <button onClick={() => { setIsReg(!isReg); setMsg(null); }}
              className="text-sm text-brand-600 hover:underline">
              {isReg ? "¿Ya tienes cuenta? Inicia sesión" : "¿No tienes cuenta? Crear una"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
