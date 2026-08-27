import { useState, useEffect } from "react";
import { usuariosAPI, formatCLP, formatFechaChile } from "../services/api";
import { AlertTriangle, RefreshCw } from "lucide-react";

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

const PEDIDO_STATUS_COLORS = {
  PENDIENTE:  "bg-yellow-100 text-yellow-800",
  CONFIRMADO: "bg-teal-100 text-teal-800",
  APROBADO:   "bg-green-100 text-green-800",
  EN_ENVIO:   "bg-blue-100 text-blue-800",
  ENTREGADO:  "bg-gray-100 text-gray-600",
  CANCELADO:  "bg-red-100 text-red-700",
};

const ENVIO_STATUS_LABELS = {
  CREADO:    "Creado",
  ASIGNADO:  "Asignado",
  EN_RUTA:   "En ruta",
  ENTREGADO: "Entregado",
};

const ENVIO_STATUS_COLORS = {
  CREADO:    "bg-yellow-100 text-yellow-800",
  ASIGNADO:  "bg-blue-100 text-blue-800",
  EN_RUTA:   "bg-purple-100 text-purple-800",
  ENTREGADO: "bg-green-100 text-green-800",
};

const PARCIAL_TEXT =
  "Algunos de tus datos no se pudieron cargar en este momento porque uno de nuestros sistemas no respondió. La información que ves aquí puede estar incompleta — no significa que no tengas pedidos o envíos registrados. Intenta recargar la página en unos minutos.";

const ERROR_TEXT =
  "No pudimos obtener tus datos en este momento porque nuestros sistemas no están respondiendo. Esto es un problema temporal, no una confirmación de que no tienes información registrada. Intenta nuevamente más tarde.";

const CUENTA_AUSENTE_TEXT =
  "No pudimos confirmar los datos de tu cuenta en este momento. Esto puede ocurrir si tu cuenta fue eliminada recientemente pero tu sesión sigue activa. Si esto no es lo esperado, cierra sesión e inicia sesión nuevamente, o contáctanos.";

const StatusBadge = ({ estado }) => (
  <div className={`text-xs px-3 py-1 rounded-full font-medium ${
    estado === "OK"      ? "bg-green-100 text-green-700"
  : estado === "PARCIAL" ? "bg-yellow-100 text-yellow-700"
  : "bg-red-100 text-red-700"
  }`}>
    Estado de tus datos: {estado}
  </div>
);

const EstadoBanner = ({ estado }) => {
  if (estado === "OK") return null;
  const isError = estado === "ERROR";
  return (
    <div className={`flex items-start gap-3 rounded-xl border p-4 mb-6 ${
      isError ? "bg-red-50 border-red-200 text-red-800" : "bg-amber-50 border-amber-200 text-amber-800"
    }`}>
      <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
      <p className="text-sm">{isError ? ERROR_TEXT : PARCIAL_TEXT}</p>
    </div>
  );
};

const CuentaCard = ({ cuenta }) => (
  <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 mb-6">
    <h2 className="font-semibold text-gray-800 mb-3">Tu cuenta</h2>
    <table className="w-full text-sm">
      <tbody className="divide-y divide-gray-50">
        <tr>
          <td className="py-1.5 text-gray-500 pr-4 w-40">Correo</td>
          <td className="py-1.5 font-medium text-gray-800">{cuenta.email}</td>
        </tr>
        <tr>
          <td className="py-1.5 text-gray-500 pr-4">Rol</td>
          <td className="py-1.5 text-gray-800">{ROLE_LABELS[cuenta.role] ?? cuenta.role ?? "—"}</td>
        </tr>
        <tr>
          <td className="py-1.5 text-gray-500 pr-4">Cuenta creada</td>
          <td className="py-1.5 text-gray-800">{formatFechaChile(cuenta.cuentaCreadaEn)}</td>
        </tr>
      </tbody>
    </table>
  </div>
);

const CuentaAusenteCard = () => (
  <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-xl p-5 mb-6">
    <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5 text-amber-700" />
    <p className="text-sm text-amber-800">{CUENTA_AUSENTE_TEXT}</p>
  </div>
);

const PedidosSection = ({ pedidos, estadoAgregacion }) => {
  const isEmpty = pedidos.length === 0;
  const degraded = estadoAgregacion !== "OK";
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 mb-6">
      <div className="p-4 border-b border-gray-100">
        <h2 className="font-semibold text-gray-800">Tus pedidos</h2>
      </div>
      <div className="divide-y divide-gray-50">
        {isEmpty && degraded && (
          <p className="text-center text-gray-400 text-sm py-6">No pudimos verificar tus pedidos en este momento (ver aviso arriba)</p>
        )}
        {isEmpty && !degraded && (
          <p className="text-center text-gray-400 text-sm py-6">Aún no tienes pedidos registrados</p>
        )}
        {pedidos.map(p => (
          <div key={p.id} className="p-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-800">Pedido #{p.id} · {p.destino || "—"}</p>
              <p className="text-xs text-gray-400">{formatFechaChile(p.creadoEn)} · {p.tipoPedido}</p>
            </div>
            <div className="flex items-center gap-3">
              <p className="text-sm font-bold text-gray-700">{formatCLP(p.total)}</p>
              <span className={`text-xs px-2 py-0.5 rounded-full ${PEDIDO_STATUS_COLORS[p.status] || "bg-gray-100 text-gray-600"}`}>
                {PEDIDO_STATUS_LABELS[p.status] ?? p.status}
              </span>
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
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 mb-6">
      <div className="p-4 border-b border-gray-100">
        <h2 className="font-semibold text-gray-800">Tus envíos</h2>
      </div>
      <div className="divide-y divide-gray-50">
        {isEmpty && degraded && (
          <p className="text-center text-gray-400 text-sm py-6">No pudimos verificar tus envíos en este momento (ver aviso arriba)</p>
        )}
        {isEmpty && !degraded && (
          <p className="text-center text-gray-400 text-sm py-6">Aún no tienes envíos registrados</p>
        )}
        {envios.map(e => (
          <div key={e.id} className="p-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-800">Envío #{e.id} · Pedido #{e.pedidoId}</p>
              <p className="text-xs text-gray-400">{e.transportista || "Transportista sin asignar"} · Entrega estimada: {formatFechaChile(e.fechaEstimadaEntrega)}</p>
            </div>
            <span className={`text-xs px-2 py-0.5 rounded-full ${ENVIO_STATUS_COLORS[e.status] || "bg-gray-100 text-gray-600"}`}>
              {ENVIO_STATUS_LABELS[e.status] ?? e.status}
            </span>
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
        <div className="w-10 h-10 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"/>
        <p className="text-gray-500 text-sm">Cargando...</p>
      </div>
    </div>
  );

  if (error) return (
    <div className="p-6 max-w-2xl mx-auto">
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-8 text-center">
        <AlertTriangle className="w-8 h-8 text-red-500 mx-auto mb-3" />
        <p className="text-gray-700 mb-4">No pudimos cargar tus datos en este momento. Intenta nuevamente en unos minutos.</p>
        <button onClick={retry}
                className="inline-flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm
                           font-medium px-4 py-2 rounded-lg transition-colors">
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
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Mis datos personales</h1>
          <p className="text-gray-500 text-sm">Aquí puedes revisar la información que SmartLogix tiene registrada sobre ti.</p>
        </div>
        <StatusBadge estado={estadoAgregacion} />
      </div>

      <EstadoBanner estado={estadoAgregacion} />

      {cuenta ? <CuentaCard cuenta={cuenta} /> : <CuentaAusenteCard />}

      <PedidosSection pedidos={pedidos} estadoAgregacion={estadoAgregacion} />
      <EnviosSection envios={envios} estadoAgregacion={estadoAgregacion} />

      <p className="text-xs text-gray-400 mt-2">
        Generado el {formatFechaChile(data?.generadoEn)}. Esta página corresponde al derecho de acceso
        (Ley 21.719). Si necesitas rectificar, cancelar u oponerte al uso de tus datos, contáctanos.
      </p>
    </div>
  );
}
