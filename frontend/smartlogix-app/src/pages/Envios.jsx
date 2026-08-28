import { useState, useEffect } from "react";
import { enviosAPI } from "../services/api";
import StatusBadge from "../components/StatusBadge";

const EMPTY = { pedidoId:"", tipoEnvio:"TERRESTRE", transportista:"", destino:"" };

const STATUS = ["CREADO","ASIGNADO","EN_RUTA","ENTREGADO"];
const STATUS_TONE = {
  CREADO:    "warning",
  ASIGNADO:  "info",
  EN_RUTA:   "accent",
  ENTREGADO: "success",
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
    <div className="p-8 px-9 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="font-heading text-2xl font-bold text-ink-900">Coordinación de Envíos</h1>
        <p className="text-ink-400 text-sm">{envios.length} envíos registrados</p>
      </div>

      {/* Formulario */}
      <div className="bg-white rounded-card border border-line-200 p-5 mb-6">
        <h2 className="font-semibold text-ink-600 mb-4 text-sm uppercase tracking-wide">Nuevo envío</h2>
        {error && <div className="bg-danger-bg text-danger-text border border-danger-border rounded-input p-3 mb-3 text-sm">{error}</div>}
        <form onSubmit={handleCreate} className="flex flex-wrap gap-3 items-end">
          {[["pedidoId","ID Pedido","number"],["transportista","Transportista","text"],["destino","Destino","text"]].map(([k,l,t]) => (
            <div key={k} className="flex-1 min-w-32">
              <label className="block text-xs text-ink-400 mb-1">{l}</label>
              <input type={t} required={k==="pedidoId"} value={form[k]}
                     onChange={e => setForm({...form,[k]:e.target.value})}
                     className="w-full border border-line-500 rounded-field px-2 py-2 text-sm
                                focus:outline-none focus:ring-2 focus:ring-accent-text" />
            </div>
          ))}
          <div className="flex-1 min-w-32">
            <label className="block text-xs text-ink-400 mb-1">Tipo envío</label>
            <select value={form.tipoEnvio} onChange={e => setForm({...form,tipoEnvio:e.target.value})}
                    className="w-full border border-line-500 rounded-field px-2 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-accent-text">
              <option>TERRESTRE</option><option>EXPRESS</option>
            </select>
          </div>
          <button type="submit" disabled={loading}
                  className="bg-accent-text hover:opacity-90 text-white text-sm font-medium
                             px-5 py-2 rounded-nav transition-colors disabled:opacity-50">
            {loading ? "..." : "+ Crear envío"}
          </button>
        </form>
      </div>

      {/* Pipeline Kanban */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {STATUS.map(st => (
          <div key={st} className="bg-white rounded-card border border-line-200 overflow-hidden">
            <div className="p-3 border-b border-line-150 flex items-center justify-between">
              <StatusBadge tone={STATUS_TONE[st]}>{st}</StatusBadge>
              <span className="text-xs text-ink-300">{byStatus(st).length}</span>
            </div>
            <div className="p-3 space-y-2 min-h-24">
              {byStatus(st).map(e => (
                <div key={e.id} className="border border-line-200 rounded-input p-3 hover:border-line-500 transition-colors">
                  <div className="flex justify-between items-start mb-1">
                    <span className="text-xs font-semibold text-ink-700">#{e.id}</span>
                    <span className="text-xs bg-chip-bg text-chip-text px-1 rounded-chip">{e.tipoEnvio}</span>
                  </div>
                  <p className="text-xs text-ink-600">Pedido #{e.pedidoId}</p>
                  <p className="text-xs text-ink-400">{e.transportista}</p>
                  <p className="text-xs text-ink-300">{e.destino}</p>
                  {nextStatus(e.status) && (
                    <button onClick={() => handleAdvance(e.id, nextStatus(e.status))}
                            className="mt-2 w-full text-xs bg-brand-50 hover:bg-brand-100 text-brand-700
                                       py-1 rounded-input transition-colors">
                      → {nextStatus(e.status)}
                    </button>
                  )}
                </div>
              ))}
              {byStatus(st).length === 0 && (
                <p className="text-center text-ink-200 text-xs py-4">Vacío</p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
