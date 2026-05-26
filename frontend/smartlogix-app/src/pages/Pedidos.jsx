import { useState, useEffect } from "react";
import { pedidosAPI, inventarioAPI } from "../services/api";

const EMPTY = {
  userId:        "1",
  userEmail:     "",
  clienteNombre: "",
  tipoPedido:    "NACIONAL",
  destino:       "",
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
  const [productos,  setProductos]  = useState([]);
  const [form,       setForm]       = useState(EMPTY);
  const [lineas,     setLineas]     = useState([]);
  const [skuInput,   setSkuInput]   = useState("");
  const [skuMatches, setSkuMatches] = useState([]);
  const [error,      setError]      = useState(null);
  const [loading,    setLoading]    = useState(false);
  const [filtro,     setFiltro]     = useState("TODOS");
  const [expanded,   setExpanded]   = useState(null);

  const loadPedidos   = () => pedidosAPI.getAll().then(r => setPedidos(r.data)).catch(console.error);
  const loadProductos = () => inventarioAPI.getAll().then(r => setProductos(r.data)).catch(console.error);

  useEffect(() => { loadPedidos(); loadProductos(); }, []);

  // Total calculado automaticamente desde las lineas del carrito
  const totalCalculado = lineas.reduce(
    (sum, l) => sum + l.precioUnitario * l.cantidad, 0
  );

  // SKU autocomplete
  const handleSkuChange = (val) => {
    setSkuInput(val);
    if (!val.trim()) { setSkuMatches([]); return; }
    const q = val.toLowerCase();
    setSkuMatches(
      productos
        .filter(p => p.sku?.toLowerCase().includes(q) || p.nombre?.toLowerCase().includes(q))
        .slice(0, 6)
    );
  };

  // Agregar producto al carrito — si ya existe incrementa cantidad
  const pickProducto = (prod) => {
    const existe = lineas.find(l => l.productoId === prod.id);
    if (existe) {
      setLineas(lineas.map(l =>
        l.productoId === prod.id ? { ...l, cantidad: l.cantidad + 1 } : l
      ));
    } else {
      setLineas([...lineas, {
        productoId:     prod.id,
        sku:            prod.sku,
        nombre:         prod.nombre,
        precioUnitario: prod.precioUnitario,
        cantidad:       1,
      }]);
    }
    setSkuInput("");
    setSkuMatches([]);
  };

  const actualizarCantidad = (productoId, cantidad) => {
    if (cantidad < 1) return;
    setLineas(lineas.map(l =>
      l.productoId === productoId ? { ...l, cantidad } : l
    ));
  };

  const quitarLinea = (productoId) => {
    setLineas(lineas.filter(l => l.productoId !== productoId));
  };

  const getProducto = (id) => productos.find(p => String(p.id) === String(id));

  // CAMBIO 1: guardar lineas del carrito en localStorage al crear el pedido
  const handleCreate = async (e) => {
    e.preventDefault();
    if (lineas.length === 0) {
      setError("Agrega al menos un producto al pedido.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const principal = lineas[0];
      const res = await pedidosAPI.create({
        userId:        +form.userId,
        userEmail:     form.userEmail,
        clienteNombre: form.clienteNombre,
        total:         totalCalculado,
        tipoPedido:    form.tipoPedido,
        destino:       form.destino,
        productoId:    principal.productoId,
        cantidad:      principal.cantidad,
      });

      // Guardar todas las lineas asociadas al ID del pedido creado
      const pedidoId = res.data?.id;
      if (pedidoId && lineas.length > 1) {
        localStorage.setItem(
          `pedido_lineas_${pedidoId}`,
          JSON.stringify(lineas)
        );
      }

      setForm(EMPTY);
      setLineas([]);
      await loadPedidos();
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Error al crear pedido");
    } finally {
      setLoading(false);
    }
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

      {/* Formulario */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
        <h2 className="font-semibold text-gray-700 mb-4 text-sm uppercase tracking-wide">
          Nuevo pedido
        </h2>
        {error && (
          <div className="bg-red-50 text-red-700 rounded-lg p-3 mb-3 text-sm">{error}</div>
        )}

        <form onSubmit={handleCreate}>

          {/* Buscador de productos */}
          <div className="mb-3">
            <label className="block text-xs text-gray-500 mb-1">
              Agregar producto (SKU o nombre)
            </label>
            <div className="relative w-full md:w-1/2">
              <input
                type="text"
                value={skuInput}
                onChange={e => handleSkuChange(e.target.value)}
                placeholder="Escribe SKU o nombre y selecciona..."
                autoComplete="off"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
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
                        stock {prod.stockActual} · ${prod.precioUnitario?.toLocaleString()}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Tabla del carrito */}
          {lineas.length > 0 && (
            <div className="mb-4 rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-100 text-xs text-gray-500 uppercase">
                    <th className="px-3 py-2 text-left">SKU</th>
                    <th className="px-3 py-2 text-left">Producto</th>
                    <th className="px-3 py-2 text-right">Precio unit.</th>
                    <th className="px-3 py-2 text-center w-32">Cantidad</th>
                    <th className="px-3 py-2 text-right">Subtotal</th>
                    <th className="px-3 py-2 w-8"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {lineas.map(l => (
                    <tr key={l.productoId} className="bg-white">
                      <td className="px-3 py-2 font-mono text-blue-700 font-semibold text-xs">
                        {l.sku}
                      </td>
                      <td className="px-3 py-2">{l.nombre}</td>
                      <td className="px-3 py-2 text-right text-gray-500">
                        ${l.precioUnitario?.toLocaleString()}
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={() => actualizarCantidad(l.productoId, l.cantidad - 1)}
                            className="w-6 h-6 rounded bg-gray-200 hover:bg-gray-300
                                       text-sm font-bold leading-none"
                          >-</button>
                          <span className="w-8 text-center font-semibold">{l.cantidad}</span>
                          <button
                            type="button"
                            onClick={() => actualizarCantidad(l.productoId, l.cantidad + 1)}
                            className="w-6 h-6 rounded bg-gray-200 hover:bg-gray-300
                                       text-sm font-bold leading-none"
                          >+</button>
                        </div>
                      </td>
                      <td className="px-3 py-2 text-right font-semibold">
                        ${(l.precioUnitario * l.cantidad).toLocaleString()}
                      </td>
                      <td className="px-3 py-2 text-center">
                        <button
                          type="button"
                          onClick={() => quitarLinea(l.productoId)}
                          className="text-red-400 hover:text-red-600 text-xs"
                        >x</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="bg-gray-50 border-t-2 border-gray-300">
                    <td colSpan={4} className="px-3 py-2 text-right font-semibold text-gray-600 text-sm">
                      TOTAL
                    </td>
                    <td className="px-3 py-2 text-right font-bold text-base text-gray-900">
                      ${totalCalculado.toLocaleString()}
                    </td>
                    <td></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}

          {/* Datos del pedido */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 mb-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Email cliente</label>
              <input type="email" value={form.userEmail}
                onChange={e => setForm({ ...form, userEmail: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Nombre cliente</label>
              <input type="text" value={form.clienteNombre}
                onChange={e => setForm({ ...form, clienteNombre: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Destino</label>
              <input type="text" value={form.destino}
                onChange={e => setForm({ ...form, destino: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
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

          <button
            type="submit"
            disabled={loading || lineas.length === 0}
            className="bg-green-700 hover:bg-green-800 text-white text-sm font-medium
                       px-5 py-2 rounded-lg transition-colors disabled:opacity-50"
          >
            {loading
              ? "Creando..."
              : lineas.length === 0
                ? "+ Crear pedido"
                : `+ Crear pedido - $${totalCalculado.toLocaleString()}`}
          </button>
        </form>
      </div>

      {/* Filtros */}
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

      {/* Tabla con detalle expandible */}
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
              const isExpanded = expanded === ped.id;
              return (
                <>
                  {/* Fila principal */}
                  <tr
                    key={ped.id}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => setExpanded(isExpanded ? null : ped.id)}
                  >
                    <td className="px-3 py-3 text-gray-400 text-xs w-6">
                      {isExpanded ? "v" : ">"}
                    </td>
                    <td className="px-3 py-3 text-gray-400 text-xs">#{ped.id}</td>
                    <td className="px-3 py-3 font-medium">{ped.clienteNombre || "-"}</td>
                    <td className="px-3 py-3 text-gray-500">{ped.userEmail}</td>
                    <td className="px-3 py-3 font-bold">${ped.total?.toLocaleString()}</td>
                    <td className="px-3 py-3">
                      <span className="text-xs bg-gray-100 px-2 py-0.5 rounded">
                        {ped.tipoPedido}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-gray-500">{ped.destino || "-"}</td>
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

                  {/* Panel de detalle */}
                  {isExpanded && (
                    <tr key={`detail-${ped.id}`} className="bg-blue-50">
                      <td colSpan={9} className="px-6 py-4">
                        <p className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-3">
                          Detalle del pedido #{ped.id}
                        </p>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

                          {/* CAMBIO 2: mostrar todos los productos del pedido */}
                          <div className="bg-white rounded-lg border border-blue-100 p-4">
                            <p className="text-xs font-semibold text-gray-500 uppercase mb-2">
                              Producto(s)
                            </p>
                            {(() => {
                              // Leer lineas guardadas en localStorage para este pedido
                              const guardadas = localStorage.getItem(`pedido_lineas_${ped.id}`);
                              const lineasDetalle = guardadas
                                ? JSON.parse(guardadas)
                                : ped.productoId
                                  ? [{
                                      productoId:     ped.productoId,
                                      sku:            getProducto(ped.productoId)?.sku,
                                      nombre:         getProducto(ped.productoId)?.nombre,
                                      precioUnitario: getProducto(ped.productoId)?.precioUnitario,
                                      cantidad:       ped.cantidad,
                                    }]
                                  : [];

                              if (lineasDetalle.length === 0) {
                                return <p className="text-gray-400 text-sm">Sin producto asociado.</p>;
                              }

                              return (
                                <table className="w-full text-sm">
                                  <thead>
                                    <tr className="text-xs text-gray-400 uppercase border-b border-gray-100">
                                      <th className="pb-1 text-left pr-3">SKU</th>
                                      <th className="pb-1 text-left pr-3">Nombre</th>
                                      <th className="pb-1 text-right pr-3">Precio unit.</th>
                                      <th className="pb-1 text-center pr-3">Cant.</th>
                                      <th className="pb-1 text-right">Subtotal</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-gray-50">
                                    {lineasDetalle.map((l, i) => {
                                      const p      = getProducto(l.productoId);
                                      const precio = l.precioUnitario ?? p?.precioUnitario ?? 0;
                                      const cant   = l.cantidad ?? 1;
                                      return (
                                        <tr key={i}>
                                          <td className="py-1.5 pr-3 font-mono text-blue-700 font-semibold text-xs">
                                            {l.sku ?? p?.sku ?? `id=${l.productoId}`}
                                          </td>
                                          <td className="py-1.5 pr-3">
                                            {l.nombre ?? p?.nombre ?? "-"}
                                          </td>
                                          <td className="py-1.5 pr-3 text-right text-gray-500">
                                            ${precio.toLocaleString()}
                                          </td>
                                          <td className="py-1.5 pr-3 text-center font-semibold">
                                            {cant}
                                          </td>
                                          <td className="py-1.5 text-right font-semibold">
                                            ${(precio * cant).toLocaleString()}
                                          </td>
                                        </tr>
                                      );
                                    })}
                                  </tbody>
                                  <tfoot>
                                    <tr className="border-t-2 border-gray-200">
                                      <td colSpan={4} className="pt-1.5 text-right text-xs font-semibold text-gray-500 pr-3">
                                        TOTAL
                                      </td>
                                      <td className="pt-1.5 text-right font-bold text-gray-900">
                                        ${ped.total?.toLocaleString()}
                                      </td>
                                    </tr>
                                  </tfoot>
                                </table>
                              );
                            })()}
                          </div>

                          {/* Info del pedido */}
                          <div className="bg-white rounded-lg border border-blue-100 p-4">
                            <p className="text-xs font-semibold text-gray-500 uppercase mb-2">
                              Informacion del pedido
                            </p>
                            <table className="w-full text-sm">
                              <tbody className="divide-y divide-gray-50">
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4 w-28">ID pedido</td>
                                  <td className="py-1 font-mono">#{ped.id}</td>
                                </tr>
                                <tr>
                                  <td className="py-1 text-gray-500 pr-4">Cliente</td>
                                  <td className="py-1">{ped.clienteNombre || "-"}</td>
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
                                  <td className="py-1">{ped.destino || "-"}</td>
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
