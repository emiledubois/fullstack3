import { useState, useEffect } from "react";
import { enviosAPI } from "../services/api";

const EMPTY = { pedidoId:"", tipoEnvio:"TERRESTRE", transportista:"", destino:"" };

const STATUS = ["CREADO","ASIGNADO","EN_RUTA","ENTREGADO"];
const STATUS_COLORS = {
  CREADO:    "bg-yellow-100 text-yellow-800",
  ASIGNADO:  "bg-blue-100 text-blue-800",
  EN_RUTA:   "bg-purple-100 text-purple-800",
  ENTREGADO: "bg-green-100 text-green-800",
};

const nextStatus = s => STATUS[STATUS.indexOf(s) + 1] || null;

export default function Envios() {
  const [envios,  setEnvios]  = useState([]);
  const [form,    setForm]    = useState(EMPTY);
  const [error,   setError]   = useState(null);
  const [loading, setLoading] = useState(false);

  const load = () => enviosAPI.getAll().then(r => setEnvios(r.data)).catch(console.error);
  useEffect(() => { load(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault(); setError(null); setLoading(true);
    try {
      await enviosAPI.create({ ...form, pedidoId: +form.pedidoId });
      setForm(EMPTY);
      await load();
    } catch (err) {
      setError(err.response?.data?.message || "Error al crear envío");
    } finally { setLoading(false); }
  };

  const handleAdvance = async (id, status) => {
    try { await enviosAPI.updateStatus(id, status); await load(); }
    catch (err) { setError(err.message); }
  };

  const byStatus = st => envios.filter(e => e.status === st);

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Coordinación de Envíos</h1>
        <p className="text-gray-500 text-sm">{envios.length} envíos registrados</p>
      </div>

      {/* Formulario */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
        <h2 className="font-semibold text-gray-700 mb-4 text-sm uppercase tracking-wide">Nuevo envío</h2>
        {error && <div className="bg-red-50 text-red-700 rounded-lg p-3 mb-3 text-sm">{error}</div>}
        <form onSubmit={handleCreate} className="flex flex-wrap gap-3 items-end">
          {[["pedidoId","ID Pedido","number"],["transportista","Transportista","text"],["destino","Destino","text"]].map(([k,l,t]) => (
            <div key={k} className="flex-1 min-w-32">
              <label className="block text-xs text-gray-500 mb-1">{l}</label>
              <input type={t} required={k==="pedidoId"} value={form[k]}
                     onChange={e => setForm({...form,[k]:e.target.value})}
                     className="w-full border border-gray-300 rounded-lg px-2 py-2 text-sm
                                focus:outline-none focus:ring-2 focus:ring-purple-500" />
            </div>
          ))}
          <div className="flex-1 min-w-32">
            <label className="block text-xs text-gray-500 mb-1">Tipo envío</label>
            <select value={form.tipoEnvio} onChange={e => setForm({...form,tipoEnvio:e.target.value})}
                    className="w-full border border-gray-300 rounded-lg px-2 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-purple-500">
              <option>TERRESTRE</option><option>EXPRESS</option>
            </select>
          </div>
          <button type="submit" disabled={loading}
                  className="bg-purple-700 hover:bg-purple-800 text-white text-sm font-medium
                             px-5 py-2 rounded-lg transition-colors disabled:opacity-50">
            {loading ? "..." : "+ Crear envío"}
          </button>
        </form>
      </div>

      {/* Pipeline Kanban */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {STATUS.map(st => (
          <div key={st} className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className={`p-3 border-b border-gray-100 flex items-center justify-between`}>
              <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${STATUS_COLORS[st]}`}>{st}</span>
              <span className="text-xs text-gray-400">{byStatus(st).length}</span>
            </div>
            <div className="p-3 space-y-2 min-h-24">
              {byStatus(st).map(e => (
                <div key={e.id} className="border border-gray-100 rounded-lg p-3 hover:border-gray-200 transition-colors">
                  <div className="flex justify-between items-start mb-1">
                    <span className="text-xs font-semibold text-gray-700">#{e.id}</span>
                    <span className="text-xs bg-gray-100 text-gray-500 px-1 rounded">{e.tipoEnvio}</span>
                  </div>
                  <p className="text-xs text-gray-600">Pedido #{e.pedidoId}</p>
                  <p className="text-xs text-gray-500">{e.transportista}</p>
                  <p className="text-xs text-gray-400">{e.destino}</p>
                  {nextStatus(e.status) && (
                    <button onClick={() => handleAdvance(e.id, nextStatus(e.status))}
                            className="mt-2 w-full text-xs bg-blue-50 hover:bg-blue-100 text-blue-700
                                       py-1 rounded transition-colors">
                      → {nextStatus(e.status)}
                    </button>
                  )}
                </div>
              ))}
              {byStatus(st).length === 0 && (
                <p className="text-center text-gray-300 text-xs py-4">Vacío</p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
