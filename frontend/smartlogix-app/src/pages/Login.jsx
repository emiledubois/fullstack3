import { useState } from "react";
import { authAPI } from "../services/api";

export default function Login() {
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [isReg,    setIsReg]    = useState(false);
  const [error,    setError]    = useState(null);
  const [loading,  setLoading]  = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true); setError(null);
    try {
      if (isReg) {
        await authAPI.register({ email, password });
        setIsReg(false);
        setError("Cuenta creada. Ahora inicia sesión.");
      } else {
        const res = await authAPI.login({ email, password });
        localStorage.setItem("token", res.data);
        window.location.reload();
      }
    } catch (err) {
      setError(err.response?.data || "Error de autenticación");
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-blue-950 flex items-center justify-center">
      <div className="bg-white rounded-2xl shadow-xl p-8 w-full max-w-sm">
        <h1 className="text-3xl font-bold text-blue-900 mb-1">SmartLogix</h1>
        <p className="text-gray-500 text-sm mb-6">Plataforma Logística</p>
        {error && <div className="bg-blue-50 text-blue-700 rounded p-3 mb-4 text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <input className="border rounded-lg p-3" type="email" placeholder="Email"
                 value={email} onChange={e => setEmail(e.target.value)} required />
          <input className="border rounded-lg p-3" type="password" placeholder="Contraseña"
                 value={password} onChange={e => setPassword(e.target.value)} required />
          <button type="submit" disabled={loading}
                  className="bg-blue-700 text-white rounded-lg p-3 font-semibold hover:bg-blue-800 disabled:opacity-50">
            {loading ? "..." : isReg ? "Crear cuenta" : "Ingresar"}
          </button>
          <button type="button" onClick={() => setIsReg(!isReg)}
                  className="text-blue-600 text-sm hover:underline">
            {isReg ? "Ya tengo cuenta — Iniciar sesión" : "Crear cuenta nueva"}
          </button>
        </form>
      </div>
    </div>
  );
}
