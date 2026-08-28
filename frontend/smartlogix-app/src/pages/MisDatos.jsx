import { useState, useEffect } from "react";
import { usuariosAPI, formatCLP, formatFechaChile } from "../services/api";
import { AlertTriangle, RefreshCw } from "lucide-react";
import StatusBadge from "../components/StatusBadge";

const ROLE_LABELS = {
  ROLE_USER:  "Usuario",
  ROLE_ADMIN: "Administrador",
};

const PEDIDO_STATUS_LABELS = {
  PENDIENTE:  "Pendiente",
  CONFIRMADO: "Confirmado",
  APROBADO:   "Aprobado",
  EN_ENVIO:   "En camino",
  ENTREGADO:  "Entregado",
  CANCELADO:  "Cancelado",
};

const PEDIDO_STATUS_TONE = {
  PENDIENTE:  "warning",
  CONFIRMADO: "chip",
  APROBADO:   "success",
  EN_ENVIO:   "info",
  ENTREGADO:  "chip",
  CANCELADO:  "danger",
};

const ENVIO_STATUS_LABELS = {
  CREADO:    "Creado",
  ASIGNADO:  "Asignado",
  EN_RUTA:   "En ruta",
  ENTREGADO: "Entregado",
};

const ENVIO_STATUS_TONE = {
  CREADO:    "warning",
  ASIGNADO:  "info",
  EN_RUTA:   "accent",
  ENTREGADO: "success",
};

const ESTADO_TONE = { OK: "success", PARCIAL: "warning" };

const PARCIAL_TEXT =
  "Algunos de tus datos no se pudieron cargar en este momento porque uno de nuestros sistemas no respondió. La información que ves aquí puede estar incompleta — no significa que no tengas pedidos o envíos registrados. Intenta recargar la página en unos minutos.";

const ERROR_TEXT =
  "No pudimos obtener tus datos en este momento porque nuestros sistemas no están respondiendo. Esto es un problema temporal, no una confirmación de que no tienes información registrada. Intenta nuevamente más tarde.";

const CUENTA_AUSENTE_TEXT =
  "No pudimos confirmar los datos de tu cuenta en este momento. Esto puede ocurrir si tu cuenta fue eliminada recientemente pero tu sesión sigue activa. Si esto no es lo esperado, cierra sesión e inicia sesión nuevamente, o contáctanos.";

const EstadoBanner = ({ estado }) => {
  if (estado === "OK") return null;
  const isError = estado === "ERROR";
  return (
    <div className={`flex items-start gap-3 rounded-card border p-4 mb-6 ${
      isError ? "bg-danger-bg border-danger-border text-danger-text" : "bg-warning-bg border-warning-text/20 text-warning-text"
    }`}>
      <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
      <p className="text-sm">{isError ? ERROR_TEXT : PARCIAL_TEXT}</p>
    </div>
  );
};

const CuentaCard = ({ cuenta }) => (
  <div className="bg-white rounded-card border border-line-200 p-5 mb-6">
    <h2 className="font-semibold text-ink-800 mb-3">Tu cuenta</h2>
    <table className="w-full text-sm">
      <tbody className="divide-y divide-line-100">
        <tr>
          <td className="py-1.5 text-ink-400 pr-4 w-40">Correo</td>
          <td className="py-1.5 font-medium text-ink-800">{cuenta.email}</td>
        </tr>
        <tr>
          <td className="py-1.5 text-ink-400 pr-4">Rol</td>
          <td className="py-1.5 text-ink-800">{ROLE_LABELS[cuenta.role] ?? cuenta.role ?? "—"}</td>
        </tr>
        <tr>
          <td className="py-1.5 text-ink-400 pr-4">Cuenta creada</td>
          <td className="py-1.5 text-ink-800">{formatFechaChile(cuenta.cuentaCreadaEn)}</td>
        </tr>
      </tbody>
    </table>
  </div>
);

const CuentaAusenteCard = () => (
  <div className="flex items-start gap-3 bg-warning-bg border border-warning-text/20 rounded-card p-5 mb-6">
    <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5 text-warning-text" />
    <p className="text-sm text-warning-text">{CUENTA_AUSENTE_TEXT}</p>
  </div>
);

const PedidosSection = ({ pedidos, estadoAgregacion }) => {
  const isEmpty = pedidos.length === 0;
  const degraded = estadoAgregacion !== "OK";
  return (
    <div className="bg-white rounded-card border border-line-200 mb-6">
      <div className="p-4 border-b border-line-150">
        <h2 className="font-semibold text-ink-800">Tus pedidos</h2>
      </div>
      <div className="divide-y divide-line-100">
        {isEmpty && degraded && (
          <p className="text-center text-ink-300 text-sm py-6">No pudimos verificar tus pedidos en este momento (ver aviso arriba)</p>
        )}
        {isEmpty && !degraded && (
          <p className="text-center text-ink-300 text-sm py-6">Aún no tienes pedidos registrados</p>
        )}
        {pedidos.map(p => (
          <div key={p.id} className="p-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-ink-800">Pedido #{p.id} · {p.destino || "—"}</p>
              <p className="text-xs text-ink-400">{formatFechaChile(p.creadoEn)} · {p.tipoPedido}</p>
            </div>
            <div className="flex items-center gap-3">
              <p className="text-sm font-bold text-ink-700">{formatCLP(p.total)}</p>
              <StatusBadge tone={PEDIDO_STATUS_TONE[p.status] || "chip"}>
                {PEDIDO_STATUS_LABELS[p.status] ?? p.status}
              </StatusBadge>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const EnviosSection = ({ envios, estadoAgregacion }) => {
  const isEmpty = envios.length === 0;
  const degraded = estadoAgregacion !== "OK";
  return (
    <div className="bg-white rounded-card border border-line-200 mb-6">
      <div className="p-4 border-b border-line-150">
        <h2 className="font-semibold text-ink-800">Tus envíos</h2>
      </div>
      <div className="divide-y divide-line-100">
        {isEmpty && degraded && (
          <p className="text-center text-ink-300 text-sm py-6">No pudimos verificar tus envíos en este momento (ver aviso arriba)</p>
        )}
        {isEmpty && !degraded && (
          <p className="text-center text-ink-300 text-sm py-6">Aún no tienes envíos registrados</p>
        )}
        {envios.map(e => (
          <div key={e.id} className="p-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-ink-800">Envío #{e.id} · Pedido #{e.pedidoId}</p>
              <p className="text-xs text-ink-400">{e.transportista || "Transportista sin asignar"} · Entrega estimada: {formatFechaChile(e.fechaEstimadaEntrega)}</p>
            </div>
            <StatusBadge tone={ENVIO_STATUS_TONE[e.status] || "chip"}>
              {ENVIO_STATUS_LABELS[e.status] ?? e.status}
            </StatusBadge>
          </div>
        ))}
      </div>
    </div>
  );
};

export default function MisDatos() {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(false);

  const fetchData = () => {
    usuariosAPI.getMisDatos()
      .then(res => setData(res.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, []);

  const retry = () => {
    setLoading(true);
    setError(false);
    fetchData();
  };

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <div className="text-center">
        <div className="w-10 h-10 border-4 border-brand-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"/>
        <p className="text-ink-400 text-sm">Cargando...</p>
      </div>
    </div>
  );

  if (error) return (
    <div className="p-8 px-9 max-w-2xl mx-auto">
      <div className="bg-white rounded-card border border-line-200 p-8 text-center">
        <AlertTriangle className="w-8 h-8 text-danger-text mx-auto mb-3" />
        <p className="text-ink-700 mb-4">No pudimos cargar tus datos en este momento. Intenta nuevamente en unos minutos.</p>
        <button onClick={retry}
                className="inline-flex items-center gap-2 bg-brand-600 hover:bg-brand-700 text-white text-sm
                           font-medium px-4 py-2 rounded-nav transition-colors">
          <RefreshCw className="w-4 h-4" /> Reintentar
        </button>
      </div>
    </div>
  );

  const estadoAgregacion = data?.estadoAgregacion ?? "OK";
  const cuenta  = data?.cuenta ?? null;
  const pedidos = data?.pedidos ?? [];
  const envios  = data?.envios ?? [];

  return (
    <div className="p-8 px-9 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink-900">Mis datos personales</h1>
          <p className="text-ink-400 text-sm">Aquí puedes revisar la información que SmartLogix tiene registrada sobre ti.</p>
        </div>
        <StatusBadge tone={ESTADO_TONE[estadoAgregacion] || "danger"}>
          Estado de tus datos: {estadoAgregacion}
        </StatusBadge>
      </div>

      <EstadoBanner estado={estadoAgregacion} />

      {cuenta ? <CuentaCard cuenta={cuenta} /> : <CuentaAusenteCard />}

      <PedidosSection pedidos={pedidos} estadoAgregacion={estadoAgregacion} />
      <EnviosSection envios={envios} estadoAgregacion={estadoAgregacion} />

      <p className="text-xs text-ink-300 mt-2">
        Generado el {formatFechaChile(data?.generadoEn)}. Esta página corresponde al derecho de acceso
        (Ley 21.719). Si necesitas rectificar, cancelar u oponerte al uso de tus datos, contáctanos.
      </p>
    </div>
  );
}
