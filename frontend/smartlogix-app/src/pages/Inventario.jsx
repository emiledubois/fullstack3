import { useState, useEffect } from "react";
import { inventarioAPI } from "../services/api";
import StatusBadge from "../components/StatusBadge";

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
    <div className="p-8 px-9 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink-900">Gestión de Inventario</h1>
          <p className="text-ink-400 text-sm">{productos.length} productos · {bajosStock} con stock bajo</p>
        </div>
      </div>

      {/* Formulario */}
      <div className="bg-white rounded-card border border-line-200 p-5 mb-6">
        <h2 className="font-semibold text-ink-600 mb-4 text-sm uppercase tracking-wide">Agregar producto</h2>
        {error && <div className="bg-danger-bg text-danger-text border border-danger-border rounded-input p-3 mb-3 text-sm">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 mb-3">
            {FIELDS.map(f => (
              <div key={f.key}>
                <label className="block text-xs text-ink-400 mb-1">{f.label}</label>
                <input
                  type={f.type}
                  required={f.required}
                  value={form[f.key]}
                  onChange={e => setForm({...form, [f.key]: e.target.value})}
                  className="w-full border border-line-500 rounded-field px-2 py-2 text-sm
                             focus:outline-none focus:ring-2 focus:ring-brand-600"
                />
              </div>
            ))}
          </div>
          <button type="submit" disabled={loading}
                  className="bg-brand-600 hover:bg-brand-700 text-white text-sm font-medium
                             px-5 py-2 rounded-nav transition-colors disabled:opacity-50">
            {loading ? "Guardando..." : "+ Agregar producto"}
          </button>
        </form>
      </div>

      {/* Search */}
      <div className="mb-4">
        <input value={search} onChange={e => setSearch(e.target.value)}
               placeholder="Buscar por nombre, SKU o bodega..."
               className="w-full max-w-sm border border-line-500 rounded-input px-3 py-2 text-sm
                          focus:outline-none focus:ring-2 focus:ring-brand-600" />
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-card border border-line-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-app-bg border-b border-line-200">
              {["SKU","Nombre","Precio","Stock","Umbral","Bodega","Estado"].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-ink-400 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line-100">
            {filtrados.length === 0 && (
              <tr><td colSpan={7} className="text-center py-8 text-ink-300">
                {search ? "Sin resultados para la búsqueda" : "Sin productos aún"}</td></tr>
            )}
            {filtrados.map(p => {
              const bajo = p.stockActual < p.umbralMinimo;
              return (
                <tr key={p.id} className={`hover:bg-app-bg ${bajo ? "bg-danger-bg/30" : ""}`}>
                  <td className="px-4 py-3 font-mono text-xs text-ink-400">{p.sku}</td>
                  <td className="px-4 py-3 font-medium text-ink-700">{p.nombre}</td>
                  <td className="px-4 py-3 text-ink-700">${p.precioUnitario?.toLocaleString()}</td>
                  <td className="px-4 py-3 font-semibold text-ink-700">{p.stockActual}</td>
                  <td className="px-4 py-3 text-ink-400">{p.umbralMinimo}</td>
                  <td className="px-4 py-3 text-ink-400">{p.bodega}</td>
                  <td className="px-4 py-3">
                    <StatusBadge tone={bajo ? "danger" : "success"}>
                      {bajo ? "⚠ Bajo" : "✓ OK"}
                    </StatusBadge>
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
