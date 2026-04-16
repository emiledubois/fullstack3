import { useState, useEffect } from "react";
import { pedidosAPI } from "../services/api";

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([]);
  const [form, setForm] = useState({
    userId: "1", userEmail: "", clienteNombre: "",
    total: "", tipoPedido: "NACIONAL", destino: "", productoId: "", cantidad: "1"
  });
  const [error, setError] = useState(null);

  const loadPedidos = () => pedidosAPI.getAll().then(r => setPedidos(r.data)).catch(console.error);
  useEffect(() => { loadPedidos(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault(); setError(null);
    try {
      await pedidosAPI.create({
        ...form,
        userId: +form.userId, total: +form.total,
        cantidad: +form.cantidad,
        productoId: form.productoId ? +form.productoId : null
      });
      loadPedidos();
      setForm({ userId:"1", userEmail:"", clienteNombre:"", total:"", tipoPedido:"NACIONAL", destino:"", productoId:"", cantidad:"1" });
    } catch (err) {
      setError(err.response?.data?.message || err.message);
    }
  };

  const statusColor = s => ({
    PENDIENTE:"bg-yellow-100 text-yellow-800", APROBADO:"bg-green-100 text-green-800",
    EN_ENVIO:"bg-blue-100 text-blue-800", ENTREGADO:"bg-gray-100 text-gray-800",
    CANCELADO:"bg-red-100 text-red-800"
  })[s] || "bg-gray-100 text-gray-600";

  return (
    <div className="p-6">
      <h2 className="text-2xl font-bold text-blue-900 mb-4">Gestión de Pedidos</h2>
      {error && <div className="bg-red-50 text-red-700 rounded p-3 mb-4">{error}</div>}
      <form onSubmit={handleCreate} className="grid grid-cols-4 gap-3 mb-6 bg-white p-4 rounded-xl shadow">
        <input className="border rounded p-2" placeholder="Email cliente" value={form.userEmail} onChange={e=>setForm({...form,userEmail:e.target.value})} />
        <input className="border rounded p-2" placeholder="Nombre cliente" value={form.clienteNombre} onChange={e=>setForm({...form,clienteNombre:e.target.value})} />
        <input className="border rounded p-2" placeholder="Destino" value={form.destino} onChange={e=>setForm({...form,destino:e.target.value})} />
        <select className="border rounded p-2" value={form.tipoPedido} onChange={e=>setForm({...form,tipoPedido:e.target.value})}>
          <option>NACIONAL</option><option>INTERNACIONAL</option>
        </select>
        <input className="border rounded p-2" type="number" placeholder="Total ($)" value={form.total} onChange={e=>setForm({...form,total:e.target.value})} />
        <input className="border rounded p-2" type="number" placeholder="ID Producto (opcional)" value={form.productoId} onChange={e=>setForm({...form,productoId:e.target.value})} />
        <input className="border rounded p-2" type="number" placeholder="Cantidad" value={form.cantidad} onChange={e=>setForm({...form,cantidad:e.target.value})} />
        <button type="submit" className="bg-green-700 text-white rounded p-2 font-semibold">Crear Pedido</button>
      </form>
      <table className="w-full bg-white rounded-xl shadow text-sm">
        <thead className="bg-blue-800 text-white">
          <tr>{["ID","Cliente","Email","Total","Tipo","Destino","Estado","Fecha"].map(h=><th key={h} className="p-3 text-left">{h}</th>)}</tr>
        </thead>
        <tbody>
          {pedidos.map(p=>(
            <tr key={p.id} className="border-b hover:bg-blue-50">
              <td className="p-3">{p.id}</td>
              <td className="p-3">{p.clienteNombre}</td>
              <td className="p-3">{p.userEmail}</td>
              <td className="p-3 font-bold">${p.total?.toLocaleString()}</td>
              <td className="p-3">{p.tipoPedido}</td>
              <td className="p-3">{p.destino}</td>
              <td className="p-3"><span className={`px-2 py-1 rounded-full text-xs ${statusColor(p.status)}`}>{p.status}</span></td>
              <td className="p-3 text-xs text-gray-500">{p.creadoEn?.substring(0,10)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
