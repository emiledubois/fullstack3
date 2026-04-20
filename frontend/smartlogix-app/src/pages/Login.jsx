import { useState } from "react";
import { authAPI } from "../services/api";

export default function Login() {
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [isReg,    setIsReg]    = useState(false);
  const [msg,      setMsg]      = useState(null);   // {text, type: "error"|"success"}
  const [loading,  setLoading]  = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) { setMsg({text:"Completa todos los campos",type:"error"}); return; }
    setLoading(true); setMsg(null);
    try {
      if (isReg) {
        await authAPI.register({ email, password });
        setMsg({ text: "¡Cuenta creada! Ahora inicia sesión.", type: "success" });
        setIsReg(false);
        setPassword("");
      } else {
        const res = await authAPI.login({ email, password });
        // res.data is the raw JWT string (Axios parses JSON automatically)
        const token = typeof res.data === "string" ? res.data.trim() : res.data;
        if (!token) throw new Error("Token vacío recibido");
        localStorage.setItem("token", token);
        window.location.reload();
      }
    } catch (err) {
      const errMsg = err.response?.data || err.message || "Error de autenticación";
      setMsg({ text: typeof errMsg === "string" ? errMsg : "Credenciales inválidas", type: "error" });
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-950 to-blue-800 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white/10 rounded-2xl mb-4">
            <svg className="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white">SmartLogix</h1>
          <p className="text-blue-200 text-sm mt-1">Plataforma de Gestión Logística</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          <h2 className="text-xl font-semibold text-gray-800 mb-6">
            {isReg ? "Crear cuenta nueva" : "Iniciar sesión"}
          </h2>

          {/* Message */}
          {msg && (
            <div className={`rounded-lg p-3 mb-4 text-sm font-medium ${ 
              msg.type === "error"
                ? "bg-red-50 text-red-700 border border-red-200"
                : "bg-green-50 text-green-700 border border-green-200"
            }`}>
              {msg.text}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="admin@smartlogix.cl"
                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Contraseña</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-700 hover:bg-blue-800 disabled:opacity-60
                         text-white font-semibold py-2.5 rounded-lg transition-colors"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"/>
                  Procesando...
                </span>
              ) : isReg ? "Crear cuenta" : "Ingresar"}
            </button>
          </form>

          <div className="mt-4 text-center">
            <button onClick={() => { setIsReg(!isReg); setMsg(null); }}
                    className="text-sm text-blue-600 hover:underline">
              {isReg ? "¿Ya tienes cuenta? Inicia sesión" : "¿No tienes cuenta? Crear una"}
            </button>
          </div>
        </div>
        <p className="text-center text-blue-300 text-xs mt-6">SmartLogix © 2025 — DSY1106</p>
      </div>
    </div>
  );
}
