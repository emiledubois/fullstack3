import { useState, useEffect } from "react";
import { pedidosAPI, inventarioAPI } from "../services/api";

const EMPTY = {
  userId: "1",
  userEmail: "",
  clienteNombre: "",
  total: "",
  tipoPedido: "NACIONAL",
  destino: "",
  productoId: "",   // numeric ID — resolved from SKU picker
  cantidad: "1",
};

const STATUS_COLORS = {
  PENDIENTE:  "bg-yellow-100 text-yellow-800",
  CONFIRMADO: "bg-teal-100 text-teal-800",
  APROBADO:   "bg-green-100 text-green-800",
  EN_ENVIO:   "bg-blue-100 text-blue-800",
  ENTREGADO:  "bg-gray-100 text-gray-600",
  CANCELADO:  "bg-red-100 text-red-700",
};

export default function Pedidos() {
  const [pedidos,    setPedidos]    = useState([]);
  const [productos,  setProductos]  = useState([]);   // inventory for lookup
  const [form,       setForm]       = useState(EMPTY);
  const [skuInput,   setSkuInput]   = useState("");   // display value for the picker
  const [skuMatches, setSkuMatches] = useState([]);   // autocomplete list
  const [error,      setError]      = useState(null);
  const [loading,    setLoading]    = useState(false);
  const [filtro,     setFiltro]     = useState("TODOS");
  const [expanded,   setExpanded]   = useState(null); // pedido id with open detail

  const loadPedidos   = () => pedidosAPI.getAll().then(r => setPedidos(r.data)).catch(console.error);
  const loadProductos = () => inventarioAPI.getAll().then(r => setProductos(r.data)).catch(console.error);

  useEffect(() => { loadPedidos(); loadProductos(); }, []);

  // ── SKU autocomplete ─────────────────────────────────────────────
  const handleSkuChange = (val) => {
    setSkuInput(val);
    setForm({ ...form, productoId: "" }); // clear until a match is picked
    if (!val.trim()) { setSkuMatches([]); return; }
    const q = val.toLowerCase();
    setSkuMatches(
      productos
        .filter(p => p.sku?.toLowerCase().includes(q) || p.nombre?.toLowerCase().includes(q))
        .slice(0, 6)
    );
  };

  const pickProducto = (prod) => {
    setSkuInput(`${prod.sku} — ${prod.nombre}`);
    setForm({
      ...form,
      productoId: String(prod.id),
      total: form.total || String(prod.precioUnitario ?? ""),
    });
    setSkuMatches([]);
  };

  // ── Helper: look up a product by id from local inventory cache ──
  const getProducto = (id) => productos.find(p => String(p.id) === String(id));

  // ── Form submit ─────────────────────────────────────────────────
  const handleCreate = async (e) => {
    e.preventDefault();
    if (!form.productoId) {
      setError("Selecciona un producto válido de la lista.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await pedidosAPI.create({
        userId:     +form.userId,
        userEmail:  form.userEmail,
        clienteNombre: form.clienteNombre,
        total:      +form.total,
        tipoPedido: form.tipoPedido,
        destino:    form.destino,
        productoId: +form.productoId,
        cantidad:   +form.cantidad,
      });
      setForm(EMPTY);
      setSkuInput("");
      await loadPedidos();
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Error al crear pedido");
    } finally {
      setLoading(false); }
  };

  const ESTADOS = ["TODOS","PENDIENTE","CONFIRMADO","APROBADO","EN_ENVIO","ENTREGADO","CANCELADO"];
  const visible  = filtro === "TODOS" ? pedidos : pedidos.filter(p => p.status === filtro);

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Gestión de Pedidos</h1>
        <p className="text-gray-500 text-sm">{pedidos.length} pedidos registrados</p>
      </div>

      {/* ── Formulario ─────────────────────────────────────────── */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
        <h2 className="font-semibold text-gray-700 mb-4 text-sm uppercase tracking-wide">
          Nuevo pedido
        </h2>
        {error && (
          <div className="bg-red-50 text-red-700 rounded-lg p-3 mb-3 text-sm">{error}</div>
        )}
        <form onSubmit={handleCreate}>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 mb-3">

            {/* Producto por SKU — autocomplete */}
            <div className="relative lg:col-span-2">
              <label className="block text-xs text-gray-500 mb-1">
                Producto (SKU o nombre)
                {form.productoId && (
                  <span className="ml-2 text-green-600 font-semibold">
                    ✓ id={form.productoId}
                  </span>
                )}
              </label>
              <input
                type="text"
                value={skuInput}
                onChange={e => handleSkuChange(e.target.value)}
                placeholder="Escribe SKU o nombre…"
                autoComplete="off"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {/* Dropdown sugerencias */}
              {skuMatches.length > 0 && (
                <ul className="absolute z-20 bg-white border border-gray-200 rounded-lg
                               shadow-lg mt-1 w-full max-h-48 overflow-y-auto">
                  {skuMatches.map(prod => (
                    <li key={prod.id}
                        onClick={() => pickProducto(prod)}
                        className="px-3 py-2 hover:bg-blue-50 cursor-pointer text-sm flex
                                   items-center justify-between">
                      <span>
                        <span className="font-mono font-semibold text-blue-700">{prod.sku}</span>
                        <span className="text-gray-600 ml-2">{prod.nombre}</span>
                      </span>
                      <span className="text-gray-400 text-xs ml-3">
                        stock {prod.stockActual} · ${prod.precioUnitario}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Cantidad */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Cantidad</label>
              <input type="number" min="1" value={form.cantidad}
                onChange={e => setForm({ ...form, cantidad: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            {/* Total */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Total ($)</label>
              <input type="number" value={form.total}
                onChange={e => setForm({ ...form, total: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            {/* Email */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Email cliente</label>
              <input type="email" value={form.userEmail}
                onChange={e => setForm({ ...form, userEmail: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            {/* Nombre cliente */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Nombre cliente</label>
              <input type="text" value={form.clienteNombre}
                onChange={e => setForm({ ...form, clienteNombre: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            {/* Destino */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Destino</label>
              <input type="text" value={form.destino}
                onChange={e => setForm({ ...form, destino: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            {/* Tipo */}
            <div>
              <label className="block text-xs text-gray-500 mb-1">Tipo</label>
              <select value={form.tipoPedido}
                onChange={e => setForm({ ...form, tipoPedido: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option>NACIONAL</option>
                <option>INTERNACIONAL</option>
              </select>
            </div>
          </div>

          <button type="submit" disabled={loading || !form.productoId}
            className="bg-green-700 hover:bg-green-800 text-white text-sm font-medium
                       px-5 py-2 rounded-lg transition-colors disabled:opacity-50">
            {loading ? "Creando..." : "+ Crear pedido"}
          </button>
        </form>
      </div>

      {/* ── Filtros ─────────────────────────────────────────────── */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {ESTADOS.map(s => (
          <button key={s} onClick={() => setFiltro(s)}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              filtro === s
                ? "bg-blue-700 text-white"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}>
            {s === "TODOS" ? `Todos (${pedidos.length})` : s}
          </button>
        ))}
      </div>

      {/* ── Tabla con detalle expandible ────────────────────────── */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200">
              {["","#","Cliente","Email","Total","Tipo","Destino","Estado","Fecha"].map(h => (
                <th key={h}
                    className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {visible.length === 0 && (
              <tr>
                <td colSpan={9} className="text-center py-8 text-gray-400">Sin pedidos</td>
              </tr>
            )}
            {visible.map(ped => {
              const prod       = getProducto(ped.productoId);
              const isExpanded = expanded === ped.id;

              return (
                <>
                  {/* ── Fila principal ─────────────────────────── */}
                  <tr key={ped.id}
                      className="hover:bg-gray-50 cursor-pointer"
                      onClick={() => setExpanded(isExpanded ? null : ped.id)}>
                    {/* Toggle */}
                    <td className="px-3 py-3 text-gray-400 text-xs w-6">
                      {isExpanded ? "▾" : "▸"}
                    </td>
                    <td className="px-3 py-3 text-gray-400 text-xs">#{ped.id}</td>
                    <td className="px-3 py-3 font-medium">{ped.clienteNombre || "—"}</td>
                    <td className="px-3 py-3 text-gray-500">{ped.userEmail}</td>
                    <td className="px-3 py-3 font-bold">${ped.total?.toLocaleString()}</td>
                    <td className="px-3 py-3">
                      <span className="text-xs bg-gray-100 px-2 py-0.5 rounded">
                        {ped.tipoPedido}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-gray-500">{ped.destino || "—"}</td>
                    <td className="px-3 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium
                                        ${STATUS_COLORS[ped.status] || "bg-gray-100 text-gray-600"}`}>
                        {ped.status}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-gray-400 text-xs">
                      {ped.creadoEn?.substring(0, 10)}
                    </td>
                  </tr>

                  {/* ── Panel de detalle expandible ────────────── */}
                  {isExpanded && (
                    <tr key={`detail-${ped.id}`} className="bg-blue-50">
                      <td colSpan={9} className="px-6 py-4">
                        <p className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-3">
                          Detalle del pedido #{ped.id}
                        </p>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

                          {/* Info del producto */}
                          <div className="bg-white rounded-lg border border-blue-100 p-4">
                            <p className="text-xs font-semibold text-gray-500 uppercase mb-2">
                              Producto
                            </p>
                            {prod ? (
                              <table className="w-full text-sm">
                                <tbody className="divide-y divide-gray-50">
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4 w-24">SKU</td>
                                    <td className="py-1 font-mono font-semibold text-blue-700">
                                      {prod.sku}
                                    </td>
                                  </tr>
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4">Nombre</td>
                                    <td className="py-1 font-medium">{prod.nombre}</td>
                                  </tr>
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4">Precio unit.</td>
                                    <td className="py-1">${prod.precioUnitario?.toLocaleString()}</td>
                                  </tr>
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4">Bodega</td>
                                    <td className="py-1">{prod.bodega || "—"}</td>
                                  </tr>
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4">Stock actual</td>
                                    <td className={`py-1 font-semibold ${
                                      prod.stockActual < prod.umbralMinimo
                                        ? "text-red-600" : "text-green-600"
                                    }`}>
                                      {prod.stockActual} unid.
                                    </td>
                                  </tr>
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4">Cantidad pedida</td>
                                    <td className="py-1 font-semibold">
                                      {/* cantidad no siempre viene en el DTO del pedido */}
                                      {ped.cantidad ?? "—"} unid.
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            ) : ped.productoId ? (
                              <p className="text-gray-400 text-sm">
                                Producto id={ped.productoId} — no encontrado en inventario local.
                                <button onClick={loadProductos}
                                        className="ml-2 text-blue-600 underline text-xs">
                                  Recargar inventario
                                </button>
                              </p>
                            ) : (
                              <p className="text-gray-400 text-sm">Sin producto asociado.</p>
                            )}
                          </div>

                          {/* Info del pedido */}
                          <div className="bg-white rounded-lg border border-blue-100 p-4">
                            <p className="text-xs font-semibold text-gray-500 uppercase mb-2">
                              Información del pedido
                            </p>
                            <table className="w-full text-sm">
                              <tbody className="divide-y divide-gray-50">
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4 w-28">ID pedido</td>
                                  <td className="py-1 font-mono">#{ped.id}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Cliente</td>
                                  <td className="py-1">{ped.clienteNombre || "—"}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Email</td>
                                  <td className="py-1">{ped.userEmail}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Total</td>
                                  <td className="py-1 font-bold text-gray-800">
                                    ${ped.total?.toLocaleString()}
                                  </td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Tipo</td>
                                  <td className="py-1">{ped.tipoPedido}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Destino</td>
                                  <td className="py-1">{ped.destino || "—"}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Estado</td>
                                  <td className="py-1">
                                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium
                                                      ${STATUS_COLORS[ped.status] || ""}`}>
                                      {ped.status}
                                    </span>
                                  </td>
                                </tr>
                                {ped.observaciones && (
                                  <tr>
                                    <td className="py-1 text-gray-500 pr-4 align-top">Observaciones</td>
                                    <td className="py-1 text-xs text-gray-600">
                                      {ped.observaciones}
                                    </td>
                                  </tr>
                                )}
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Creado</td>
                                  <td className="py-1 text-gray-400 text-xs">
                                    {ped.creadoEn?.replace("T", " ").substring(0, 16)}
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
