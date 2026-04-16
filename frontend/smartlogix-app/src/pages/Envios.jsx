import { useState, useEffect } from "react";
import { enviosAPI } from "../services/api";

export default function Envios() {
  const [envios, setEnvios] = useState([]);
  const [form, setForm] = useState({ pedidoId:"", tipoEnvio:"TERRESTRE", transportista:"", destino:"" });
  const [error, setError] = useState(null);

  const loadEnvios = () => enviosAPI.getAll().then(r => setEnvios(r.data)).catch(console.error);
  useEffect(() => { loadEnvios(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault(); setError(null);
    try {
      await enviosAPI.create({ ...form, pedidoId: +form.pedidoId });
      loadEnvios();
      setForm({ pedidoId:"", tipoEnvio:"TERRESTRE", transportista:"", destino:"" });
    } catch (err) { setError(err.response?.data?.message || err.message); }
  };

  const handleUpdateStatus = async (id, status) => {
    try { await enviosAPI.updateStatus(id, status); loadEnvios(); }
    catch (err) { alert(err.message); }
  };

  const statusColor = s => ({
    CREADO:"bg-yellow-100 text-yellow-800", ASIGNADO:"bg-blue-100 text-blue-800",
    EN_RUTA:"bg-purple-100 text-purple-800", ENTREGADO:"bg-green-100 text-green-800"
  })[s] || "bg-gray-100 text-gray-600";

  const nextStatus = s => ({CREADO:"ASIGNADO",ASIGNADO:"EN_RUTA",EN_RUTA:"ENTREGADO"})[s];

  return (
    <div className="p-6">
      <h2 className="text-2xl font-bold text-blue-900 mb-4">Coordinación de Envíos</h2>
      {error && <div className="bg-red-50 text-red-700 rounded p-3 mb-4">{error}</div>}
      <form onSubmit={handleCreate} className="grid grid-cols-4 gap-3 mb-6 bg-white p-4 rounded-xl shadow">
        <input className="border rounded p-2" type="number" placeholder="ID Pedido" value={form.pedidoId} onChange={e=>setForm({...form,pedidoId:e.target.value})} required />
        <select className="border rounded p-2" value={form.tipoEnvio} onChange={e=>setForm({...form,tipoEnvio:e.target.value})}>
          <option>TERRESTRE</option><option>EXPRESS</option>
        </select>
        <input className="border rounded p-2" placeholder="Transportista" value={form.transportista} onChange={e=>setForm({...form,transportista:e.target.value})} />
        <input className="border rounded p-2" placeholder="Destino" value={form.destino} onChange={e=>setForm({...form,destino:e.target.value})} />
        <button type="submit" className="col-span-4 bg-purple-700 text-white rounded p-2 font-semibold">Crear Envío</button>
      </form>
      <table className="w-full bg-white rounded-xl shadow text-sm">
        <thead className="bg-blue-800 text-white">
          <tr>{["ID","Pedido","Tipo","Transportista","Destino","Ruta","Estado","Entrega est.","Acción"].map(h=><th key={h} className="p-3 text-left">{h}</th>)}</tr>
        </thead>
        <tbody>
          {envios.map(e=>(
            <tr key={e.id} className="border-b hover:bg-blue-50">
              <td className="p-3">{e.id}</td>
              <td className="p-3">{e.pedidoId}</td>
              <td className="p-3">{e.tipoEnvio}</td>
              <td className="p-3">{e.transportista}</td>
              <td className="p-3">{e.destino}</td>
              <td className="p-3 text-xs text-gray-500">{e.rutaDescripcion}</td>
              <td className="p-3"><span className={`px-2 py-1 rounded-full text-xs ${statusColor(e.status)}`}>{e.status}</span></td>
              <td className="p-3 text-xs">{e.fechaEstimadaEntrega?.substring(0,10)}</td>
              <td className="p-3">{nextStatus(e.status) && (
                <button onClick={()=>handleUpdateStatus(e.id, nextStatus(e.status))}
                        className="text-xs bg-blue-600 text-white px-2 py-1 rounded hover:bg-blue-700">
                  → {nextStatus(e.status)}
                </button>
              )}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
