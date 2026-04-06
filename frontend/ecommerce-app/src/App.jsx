import { useState } from "react";
import { CartProvider } from "./context/CartContext";
import ProductList from "./containers/ProductList";
import Cart from "./pages/Cart";
import Login from "./pages/Login";

export default function App() {
  const [page, setPage] = useState("home");
  const token = localStorage.getItem("token");

  if (!token) return <Login />;

  return (
    <CartProvider>
      <nav className="bg-blue-700 text-white px-6 py-3 flex gap-6 items-center">
        <span className="font-bold text-lg">eCommerce-Micro</span>
        <button onClick={() => setPage("home")} className="hover:underline">Productos</button>
        <button onClick={() => setPage("cart")} className="hover:underline">Carrito</button>
        <button onClick={() => { localStorage.removeItem("token"); window.location.reload(); }}
                className="ml-auto hover:underline">Salir</button>
      </nav>
      <main className="bg-gray-50 min-h-screen">
        {page === "home" && <ProductList />}
        {page === "cart" && <Cart />}
      </main>
    </CartProvider>
  );
}
