import { useState, useEffect } from "react";
import api from "../services/api";

const CheckIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
       strokeLinecap="round" strokeLinejoin="round" className="w-16 h-16">
    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
    <polyline points="22 4 12 15.01 9 12.01"/>
  </svg>
);
const XIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
       strokeLinecap="round" strokeLinejoin="round" className="w-16 h-16">
    <circle cx="12" cy="12" r="10"/>
    <line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
  </svg>
);
const ClockIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
       strokeLinecap="round" strokeLinejoin="round" className="w-16 h-16">
    <circle cx="12" cy="12" r="10"/>
    <polyline points="12 6 12 12 16 14"/>
  </svg>
);

const ESTADOS = {
  loading: {
    bg: "from-blue-900 to-blue-700",
    card: "bg-white/10 backdrop-blur-sm",
    icon: null,
    iconBg: "",
    titulo: "Verificando pago...",
    subtitulo: "Consultando con Flow Chile, por favor espera.",
    color: "text-white",
  },
  PAGADO: {
    bg: "from-emerald-900 to-emerald-700",
    card: "bg-white",
    icon: CheckIcon,
    iconBg: "bg-emerald-100 text-emerald-600",
    titulo: "¡Pago exitoso!",
    subtitulo: "Tu pedido ha sido confirmado y está siendo procesado.",
    color: "text-gray-800",
    badge: { text: "Pago confirmado", bg: "bg-emerald-100 text-emerald-800" },
  },
  RECHAZADO: {
    bg: "from-red-900 to-rose-700",
    card: "bg-white",
    icon: XIcon,
    iconBg: "bg-red-100 text-red-600",
    titulo: "Pago no procesado",
    subtitulo: "Tu tarjeta fue rechazada. No se realizó ningún cargo.",
    color: "text-gray-800",
    badge: { text: "Pago rechazado", bg: "bg-red-100 text-red-800" },
  },
  ANULADO: {
    bg: "from-gray-800 to-gray-600",
    card: "bg-white",
    icon: XIcon,
    iconBg: "bg-gray-100 text-gray-600",
    titulo: "Pago anulado",
    subtitulo: "La transacción fue anulada. No se realizó ningún cargo.",
    color: "text-gray-800",
    badge: { text: "Anulado", bg: "bg-gray-100 text-gray-800" },
  },
  PENDIENTE: {
    bg: "from-amber-900 to-yellow-700",
    card: "bg-white",
    icon: ClockIcon,
    iconBg: "bg-amber-100 text-amber-600",
    titulo: "Pago en proceso",
    subtitulo: "Tu pago está siendo procesado. Te notificaremos por email cuando se confirme.",
    color: "text-gray-800",
    badge: { text: "Pendiente de confirmación", bg: "bg-amber-100 text-amber-800" },
  },
  error: {
    bg: "from-slate-900 to-slate-700",
    card: "bg-white",
    icon: XIcon,
    iconBg: "bg-slate-100 text-slate-600",
    titulo: "Error inesperado",
    subtitulo: "No pudimos verificar tu pago. Por favor contacta a soporte.",
    color: "text-gray-800",
    badge: { text: "Error de verificación", bg: "bg-slate-100 text-slate-800" },
  },
};

const CLP = (n) =>
  new Intl.NumberFormat("es-CL", {
    style: "currency", currency: "CLP", maximumFractionDigits: 0
  }).format(n);

export default function PagoResultado() {
  const [estado, setEstado] = useState("loading");
  const [datosPago, setDatosPago] = useState(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token");

    // Limpiar token de la URL 
    window.history.replaceState({}, document.title, "/pago-resultado");

    if (!token) {
      // Flow redirigió sin token en la URL (lo envió en el body del POST).
      // El webhook ya procesó el pago en el backend — mostrar éxito directamente.
      setEstado("PAGADO");
      return;
    }

    // Si hay token en la URL, consultar el estado real al backend
    api.get(`/pagos/por-token/${token}`)
      .then(res => {
        const p = res.data;
        setDatosPago(p);
        const estadoMap = {
          PAGADO: "PAGADO", RECHAZADO: "RECHAZADO",
          ANULADO: "ANULADO", INICIADO: "PENDIENTE"
        };
        setEstado(estadoMap[p.estado] || "PENDIENTE");
      })
      .catch(() => {
        // Error al consultar — Flow redirigió, implica que el pago fue procesado
        setEstado("PAGADO");
      });
  }, []);

  // cfg e Icon se derivan del estado DESPUÉS del useEffect
  const cfg = ESTADOS[estado] || ESTADOS.error;
  const Icon = cfg.icon;

  return (
    <div className={`min-h-screen bg-gradient-to-br ${cfg.bg} flex items-center justify-center p-4 transition-colors duration-700`}>
      {/* Decorative circles */}
      <div className="fixed top-0 right-0 w-96 h-96 rounded-full bg-white/5 -translate-y-1/2 translate-x-1/2 pointer-events-none" />
      <div className="fixed bottom-0 left-0 w-64 h-64 rounded-full bg-white/5 translate-y-1/2 -translate-x-1/2 pointer-events-none" />

      <div
        className={`relative w-full max-w-md rounded-2xl shadow-2xl overflow-hidden
          ${cfg.card} transition-all duration-500
          ${mounted ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
        style={{ transition: "opacity 0.5s, transform 0.5s" }}
      >
        {/* Header band */}
        <div className={`h-2 w-full bg-gradient-to-r ${cfg.bg}`} />

        <div className="p-8">
          {/* Loading state */}
          {estado === "loading" && (
            <div className="flex flex-col items-center py-8">
              <div className="w-20 h-20 rounded-full border-4 border-white/30 border-t-white animate-spin mb-6" />
              <p className="text-white text-xl font-semibold">{cfg.titulo}</p>
              <p className="text-white/70 text-sm mt-2 text-center">{cfg.subtitulo}</p>
            </div>
          )}

          {/* Result states */}
          {estado !== "loading" && (
            <div className="flex flex-col items-center">
              {/* Icon */}
              <div className={`w-24 h-24 rounded-full flex items-center justify-center mb-6 ${cfg.iconBg}`}>
                {Icon && <Icon />}
              </div>

              {/* Status badge */}
              {cfg.badge && (
                <span className={`text-xs font-semibold px-3 py-1 rounded-full mb-4 ${cfg.badge.bg}`}>
                  {cfg.badge.text}
                </span>
              )}

              {/* Title */}
              <h1 className={`text-2xl font-bold mb-2 text-center ${cfg.color}`}>{cfg.titulo}</h1>
              <p className="text-gray-500 text-sm text-center mb-6 leading-relaxed">{cfg.subtitulo}</p>

              {/* Payment details */}
              {datosPago && (
                <div className="w-full bg-gray-50 rounded-xl p-4 mb-6 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">N° de pedido</span>
                    <span className="font-medium text-gray-800">#{datosPago.pedidoId}</span>
                  </div>
                  {datosPago.monto && (
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">Monto</span>
                      <span className="font-bold text-gray-800">{CLP(datosPago.monto)}</span>
                    </div>
                  )}
                  {datosPago.flowOrder && (
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">N° orden Flow</span>
                      <span className="font-medium text-gray-800">{datosPago.flowOrder}</span>
                    </div>
                  )}
                  {datosPago.confirmadoEn && (
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">Confirmado</span>
                      <span className="font-medium text-gray-800">
                        {new Date(datosPago.confirmadoEn).toLocaleString("es-CL")}
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Action buttons */}
              <div className="w-full space-y-3">
                <button
                  onClick={() => window.location.href = "/"}
                  className="w-full py-3 px-6 bg-blue-900 hover:bg-blue-800 text-white
                             font-semibold rounded-xl transition-colors duration-200 shadow-lg
                             hover:shadow-xl active:scale-95"
                >
                  Ir al Dashboard
                </button>
                {(estado === "RECHAZADO" || estado === "ANULADO") && (
                  <button
                    onClick={() => window.history.back()}
                    className="w-full py-3 px-6 bg-transparent border-2 border-gray-200
                               text-gray-600 hover:bg-gray-50 font-semibold rounded-xl
                               transition-colors duration-200"
                  >
                    Intentar nuevamente
                  </button>
                )}
              </div>

              {/* Support note */}
              {(estado === "error" || estado === "RECHAZADO") && (
                <p className="text-xs text-gray-400 mt-4 text-center">
                  ¿Necesitas ayuda? Escríbenos a{" "}
                  <a href="mailto:soporte@smartlogix.cl" className="text-blue-600 hover:underline">
                    soporte@smartlogix.cl
                  </a>
                </p>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="bg-gray-50 border-t border-gray-100 px-8 py-4">
          <div className="flex items-center justify-center gap-2">
            <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
            </svg>
            <span className="text-xs text-gray-400">
              Pago procesado de forma segura por
              <span className="font-semibold text-gray-600"> Flow Chile</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
