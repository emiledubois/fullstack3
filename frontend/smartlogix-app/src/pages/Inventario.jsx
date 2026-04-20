import { useState, useEffect } from "react";
import { inventarioAPI } from "../services/api";

const FIELDS = [
  { key:"sku",           label:"SKU",         type:"text",   required:true  },
  { key:"nombre",        label:"Nombre",       type:"text",   required:true  },
  { key:"bodega",        label:"Bodega",       type:"text",   required:false },
  { key:"precioUnitario",label:"Precio ($)",   type:"number", required:true  },
  { key:"stockActual",   label:"Stock",        type:"number", required:true  },
  { key:"umbralMinimo",  label:"Umbral mín.", type:"number", required:true  },
];

const EMPTY = { sku:"", nombre:"", bodega:"", precioUnitario:"", stockActual:"", umbralMinimo:"" };

export default function Inventario() {
  const [productos, setProductos] = useState([]);
  const [form,      setForm]      = useState(EMPTY);
  const [loading,   setLoading]   = useState(false);
  const [error,     setError]     = useState(null);
  const [search,    setSearch]    = useState("");

  const loadProductos = () =>
    inventarioAPI.getAll().then(r => setProductos(r.data)).catch(console.error);

  useEffect(() => { loadProductos(); }, []);

  const handleSubmit = async (e) => {
    e.preventDefault(); setError(null); setLoading(true);
    try {
      await inventarioAPI.create({
        ...form,
        precioUnitario: +form.precioUnitario,
        stockActual:    +form.stockActual,
        umbralMinimo:   +form.umbralMinimo,
      });
      setForm(EMPTY);
      await loadProductos();
    } catch (err) {
      setError(err.response?.data?.message || "Error al crear producto");
    } finally { setLoading(false); }
  };

  const filtrados = productos.filter(p =>
    p.nombre?.toLowerCase().includes(search.toLowerCase()) ||
    p.sku?.toLowerCase().includes(search.toLowerCase()) ||
    p.bodega?.toLowerCase().includes(search.toLowerCase())
  );

  const bajosStock = productos.filter(p => p.stockActual < p.umbralMinimo).length;

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Gestión de Inventario</h1>
          <p className="text-gray-500 text-sm">{productos.length} productos · {bajosStock} con stock bajo</p>
        </div>
      </div>

      {/* Formulario */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
        <h2 className="font-semibold text-gray-700 mb-4 text-sm uppercase tracking-wide">Agregar producto</h2>
        {error && <div className="bg-red-50 text-red-700 rounded-lg p-3 mb-3 text-sm">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 mb-3">
            {FIELDS.map(f => (
              <div key={f.key}>
                <label className="block text-xs text-gray-500 mb-1">{f.label}</label>
                <input
                  type={f.type}
                  required={f.required}
                  value={form[f.key]}
                  onChange={e => setForm({...form, [f.key]: e.target.value})}
                  className="w-full border border-gray-300 rounded-lg px-2 py-2 text-sm
                             focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            ))}
          </div>
          <button type="submit" disabled={loading}
                  className="bg-blue-700 hover:bg-blue-800 text-white text-sm font-medium
                             px-5 py-2 rounded-lg transition-colors disabled:opacity-50">
            {loading ? "Guardando..." : "+ Agregar producto"}
          </button>
        </form>
      </div>

      {/* Search */}
      <div className="mb-4">
        <input value={search} onChange={e => setSearch(e.target.value)}
               placeholder="Buscar por nombre, SKU o bodega..."
               className="w-full max-w-sm border border-gray-300 rounded-lg px-3 py-2 text-sm
                          focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200">
              {["SKU","Nombre","Precio","Stock","Umbral","Bodega","Estado"].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {filtrados.length === 0 && (
              <tr><td colSpan={7} className="text-center py-8 text-gray-400">
                {search ? "Sin resultados para la búsqueda" : "Sin productos aún"}</td></tr>
            )}
            {filtrados.map(p => {
              const bajo = p.stockActual < p.umbralMinimo;
              return (
                <tr key={p.id} className={`hover:bg-gray-50 ${bajo ? "bg-red-50/30" : ""}`}>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{p.sku}</td>
                  <td className="px-4 py-3 font-medium">{p.nombre}</td>
                  <td className="px-4 py-3">${p.precioUnitario?.toLocaleString()}</td>
                  <td className="px-4 py-3 font-semibold">{p.stockActual}</td>
                  <td className="px-4 py-3 text-gray-500">{p.umbralMinimo}</td>
                  <td className="px-4 py-3 text-gray-500">{p.bodega}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${
                      bajo ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"
                    }`}>
                      {bajo ? "⚠ Bajo" : "✓ OK"}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
