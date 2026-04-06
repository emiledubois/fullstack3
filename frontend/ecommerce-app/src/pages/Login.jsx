import { useState } from "react";
import api from "../services/api";

export default function Login({ onLogin }) {
  const [email,    setEmail]    = useState("");
  const [password, setPassword] = useState("");
  const [error,    setError]    = useState(null);
  const [isReg,    setIsReg]    = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (isReg) {
        await api.post("/auth/register", { email, password });
        setIsReg(false);
      } else {
        const res = await api.post("/auth/login", { email, password });
        localStorage.setItem("token", res.data);
        onLogin && onLogin();
        window.location.reload();
      }
    } catch (err) {
      setError(err.response?.data?.error || "Error de autenticación");
    }
  };

  return (
    <div className="max-w-sm mx-auto mt-20 p-6 bg-white rounded-xl shadow">
      <h2 className="text-2xl font-bold mb-4">{isReg ? "Registro" : "Login"}</h2>
      {error && <p className="text-red-500 mb-3">{error}</p>}
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input className="border rounded p-2" placeholder="Email"
               value={email} onChange={e=>setEmail(e.target.value)} />
        <input className="border rounded p-2" type="password" placeholder="Password"
               value={password} onChange={e=>setPassword(e.target.value)} />
        <button className="bg-blue-600 text-white rounded p-2" type="submit">
          {isReg ? "Registrarse" : "Ingresar"}
        </button>
        <button type="button" className="text-blue-500 text-sm"
                onClick={() => setIsReg(!isReg)}>
          {isReg ? "Ya tengo cuenta" : "Crear cuenta"}
        </button>
      </form>
    </div>
  );
}
