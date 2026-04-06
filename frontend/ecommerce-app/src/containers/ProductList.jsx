import { useProducts } from "../hooks/useProducts";
import ProductCard from "../components/ProductCard";
import { useCart } from "../context/CartContext";

// Container Pattern: maneja lógica y estado, delega vista a ProductCard
export default function ProductList() {
  const { products, loading, error } = useProducts();
  const { addItem } = useCart();

  if (loading) return <div className="text-center p-8">Cargando productos...</div>;
  if (error)   return <div className="text-red-500 p-4">Error: {error}</div>;

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 p-6">
      {products.map(p => (
        <ProductCard key={p.id} product={p} onAddToCart={() => addItem(p)} />
      ))}
    </div>
  );
}
