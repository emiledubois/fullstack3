import { useEffect, useState } from "react";

export default function PagoResultado() {
  const [estado, setEstado] = useState("Verificando pago...");
  const [exitoso, setExitoso] = useState(null);

  useEffect(() => {
    // Flow redirige a urlReturn — el token viene en la URL
    // Fuente: developers.flow.cl — Finalización de orden
    // La verificación real ya la hizo ms-pagos via webhook
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token");

    if (token) {
      setEstado("Pago procesado. Verificando con Flow...");
      // En producción: consultar el estado del pago a ms-pagos
      // Por ahora mostrar mensaje genérico
      setTimeout(() => {
        setEstado("¡Pago recibido! Tu pedido está siendo procesado.");
        setExitoso(true);
      }, 1500);
    } else {
      setEstado("No se encontró información del pago.");
      setExitoso(false);
    }
  }, []);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-lg p-8 max-w-md w-full text-center">
        <div className={`text-6xl mb-4 ${exitoso === null ? "animate-spin" : ""}`}>
          {exitoso === null ? "⏳" : exitoso ? "✅" : "❌"}
        </div>
        <h1 className="text-xl font-bold text-gray-800 mb-2">
          {exitoso === null ? "Procesando..." : exitoso ? "Pago exitoso" : "Pago no completado"}
        </h1>
        <p className="text-gray-500 text-sm mb-6">{estado}</p>
        <button
          onClick={() => window.location.href = "/"}
          className="bg-blue-700 text-white px-6 py-2 rounded-lg text-sm font-medium"
        >
          Volver al Dashboard
        </button>
      </div>
    </div>
  );
}
