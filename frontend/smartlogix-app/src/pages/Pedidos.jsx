import { useState, useEffect } from "react";
import { pedidosAPI } from "../services/api";

const EMPTY = { userId:"1", userEmail:"", clienteNombre:"",
                total:"", tipoPedido:"NACIONAL", destino:"", productoId:"", cantidad:"1" };

const STATUS_COLORS = {
  PENDIENTE: "bg-yellow-100 text-yellow-800",
  APROBADO:  "bg-green-100 text-green-800",
  EN_ENVIO:  "bg-blue-100 text-blue-800",
  ENTREGADO: "bg-gray-100 text-gray-600",
  CANCELADO: "bg-red-100 text-red-700",
};

export default function Pedidos() {
  const [pedidos,  setPedidos]  = useState([]);
  const [form,     setForm]     = useState(EMPTY);
  const [error,    setError]    = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [filtro,   setFiltro]   = useState("TODOS");

  const load = () => pedidosAPI.getAll().then(r => setPedidos(r.data)).catch(console.error);
  useEffect(() => { load(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault(); setError(null); setLoading(true);
    try {
      await pedidosAPI.create({
        ...form,
        userId:    +form.userId,
        total:     +form.total,
        cantidad:  +form.cantidad,
        productoId: form.productoId ? +form.productoId : null,
      });
      setForm(EMPTY);
      await load();
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Error al crear pedido");
    } finally { setLoading(false); }
  };

  const ESTADOS = ["TODOS","PENDIENTE","APROBADO","EN_ENVIO","ENTREGADO","CANCELADO"];
  const visible = filtro === "TODOS" ? pedidos : pedidos.filter(p => p.status === filtro);

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Gestión de Pedidos</h1>
        <p className="text-gray-500 text-sm">{pedidos.length} pedidos totales</p>
      </div>

      {/* Formulario */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
        <h2 className="font-semibold text-gray-700 mb-4 text-sm uppercase tracking-wide">Nuevo pedido</h2>
        {error && <div className="bg-red-50 text-red-700 rounded-lg p-3 mb-3 text-sm">{error}</div>}
        <form onSubmit={handleCreate}
              className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {[
            ["userEmail","Email cliente","text"],
            ["clienteNombre","Nombre cliente","text"],
            ["destino","Destino","text"],
            ["total","Total ($)","number"],
            ["productoId","ID Producto","number"],
            ["cantidad","Cantidad","number"],
          ].map(([k,l,t]) => (
            <div key={k}>
              <label className="block text-xs text-gray-500 mb-1">{l}</label>
              <input type={t} value={form[k]} onChange={e => setForm({...form,[k]:e.target.value})}
                     className="w-full border border-gray-300 rounded-lg px-2 py-2 text-sm
                                focus:outline-none focus:ring-2 focus:ring-green-500" />
            </div>
          ))}
          <div>
            <label className="block text-xs text-gray-500 mb-1">Tipo</label>
            <select value={form.tipoPedido} onChange={e => setForm({...form,tipoPedido:e.target.value})}
                    className="w-full border border-gray-300 rounded-lg px-2 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-green-500">
              <option>NACIONAL</option><option>INTERNACIONAL</option>
            </select>
          </div>
          <div className="flex items-end">
            <button type="submit" disabled={loading}
                    className="w-full bg-green-700 hover:bg-green-800 text-white text-sm font-medium
                               px-4 py-2 rounded-lg transition-colors disabled:opacity-50">
              {loading ? "..." : "+ Crear pedido"}
            </button>
          </div>
        </form>
      </div>

      {/* Filtros */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {ESTADOS.map(s => (
          <button key={s} onClick={() => setFiltro(s)}
                  className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${ 
                    filtro === s ? "bg-blue-700 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                  }`}>
            {s === "TODOS" ? `Todos (${pedidos.length})` : s}
          </button>
        ))}
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200">
              {["ID","Cliente","Email","Total","Tipo","Destino","Estado","Fecha"].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {visible.length === 0 && (
              <tr><td colSpan={8} className="text-center py-8 text-gray-400">Sin pedidos</td></tr>
            )}
            {visible.map(p => (
              <tr key={p.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-gray-400 text-xs">#{p.id}</td>
                <td className="px-4 py-3 font-medium">{p.clienteNombre}</td>
                <td className="px-4 py-3 text-gray-500">{p.userEmail}</td>
                <td className="px-4 py-3 font-bold">${p.total?.toLocaleString()}</td>
                <td className="px-4 py-3"><span className="text-xs bg-gray-100 px-2 py-0.5 rounded">{p.tipoPedido}</span></td>
                <td className="px-4 py-3 text-gray-500">{p.destino}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[p.status] || ""}`}>
                    {p.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-400 text-xs">{p.creadoEn?.substring(0,10)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
