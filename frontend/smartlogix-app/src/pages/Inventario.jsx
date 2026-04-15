import { useState, useEffect } from "react";
import { inventarioAPI } from "../services/api";

export default function Inventario() {
  const [productos, setProductos] = useState([]);
  const [form, setForm] = useState({ sku:"", nombre:"", precioUnitario:"", stockActual:"", umbralMinimo:"", bodega:"" });

  const loadProductos = () => inventarioAPI.getAll().then(r => setProductos(r.data));
  useEffect(() => { loadProductos(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    await inventarioAPI.create({...form, precioUnitario: +form.precioUnitario,
      stockActual: +form.stockActual, umbralMinimo: +form.umbralMinimo });
    loadProductos();
    setForm({ sku:"", nombre:"", precioUnitario:"", stockActual:"", umbralMinimo:"", bodega:"" });
  };

  return (
    <div className="p-6">
      <h2 className="text-2xl font-bold text-blue-900 mb-4">Gestión de Inventario</h2>
      <form onSubmit={handleCreate} className="grid grid-cols-3 gap-3 mb-6 bg-white p-4 rounded-xl shadow">
        {["sku","nombre","bodega"].map(f => (
          <input key={f} className="border rounded p-2 col-span-1" placeholder={f}
                 value={form[f]} onChange={e => setForm({...form, [f]: e.target.value})} />
        ))}
        {[["precioUnitario","Precio"],["stockActual","Stock"],["umbralMinimo","Umbral mín."]].map(([f,l]) => (
          <input key={f} type="number" className="border rounded p-2" placeholder={l}
                 value={form[f]} onChange={e => setForm({...form, [f]: e.target.value})} />
        ))}
        <button type="submit" className="col-span-3 bg-blue-700 text-white rounded p-2">Agregar Producto</button>
      </form>
      <table className="w-full bg-white rounded-xl shadow text-sm">
        <thead className="bg-blue-800 text-white"><tr>
          {["SKU","Nombre","Precio","Stock","Umbral","Bodega","Estado"].map(h => <th key={h} className="p-3 text-left">{h}</th>)}
        </tr></thead>
        <tbody>
          {productos.map(p => (
            <tr key={p.id} className="border-b hover:bg-blue-50">
              <td className="p-3">{p.sku}</td>
              <td className="p-3">{p.nombre}</td>
              <td className="p-3">${p.precioUnitario}</td>
              <td className="p-3">{p.stockActual}</td>
              <td className="p-3">{p.umbralMinimo}</td>
              <td className="p-3">{p.bodega}</td>
              <td className="p-3">
                <span className={`px-2 py-1 rounded-full text-xs ${p.stockActual < p.umbralMinimo ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}`}>
                  {p.stockActual < p.umbralMinimo ? "⚠ Bajo" : "OK"}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
