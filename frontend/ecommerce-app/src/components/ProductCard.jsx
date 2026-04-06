// Componente Presentacional: solo recibe props y renderiza
// No conoce la API ni el estado global — fácil de testear y reutilizar
export default function ProductCard({ product, onAddToCart }) {
  return (
    <div className="bg-white rounded-xl shadow p-4 flex flex-col gap-3">
      <h3 className="font-bold text-lg">{product.name}</h3>
      <p className="text-gray-500 text-sm">{product.description}</p>
      <div className="flex justify-between items-center mt-auto">
        <span className="text-xl font-bold text-blue-600">
          ${product.price?.toLocaleString()}
        </span>
        <span className={`text-xs px-2 py-1 rounded-full ${
          product.stock > 10 ? "bg-green-100 text-green-700"
                             : "bg-red-100 text-red-700"
        }`}>
          Stock: {product.stock}
        </span>
      </div>
      <button
        onClick={onAddToCart}
        disabled={product.stock === 0}
        className="bg-blue-600 text-white rounded-lg py-2 hover:bg-blue-700 disabled:bg-gray-300"
      >
        {product.stock === 0 ? "Sin stock" : "Agregar al carrito"}
      </button>
    </div>
  );
}
