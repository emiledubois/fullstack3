import { useCart } from "../context/CartContext";
import api from "../services/api";
import { useState } from "react";

export default function Cart() {
  const { cart, removeItem, clearCart, total } = useCart();
  const [msg, setMsg] = useState(null);

  const handleCheckout = async () => {
    try {
      const userId = 1; // En producción: extraer del JWT decodificado
      await api.post("/orders", { userId, userEmail: "user@test.com", total });
      clearCart();
      setMsg("¡Pedido realizado con éxito!");
    } catch (e) {
      setMsg("Error al procesar el pedido");
    }
  };

  if (cart.length === 0)
    return <div className="p-8 text-center text-gray-500">El carrito está vacío</div>;

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h2 className="text-2xl font-bold mb-4">Tu Carrito</h2>
      {msg && <p className="text-green-600 mb-4">{msg}</p>}
      {cart.map(item => (
        <div key={item.id} className="flex justify-between items-center border-b py-3">
          <div>
            <p className="font-medium">{item.name}</p>
            <p className="text-sm text-gray-500">Cantidad: {item.qty}</p>
          </div>
          <div className="flex gap-3 items-center">
            <p>${(item.price * item.qty).toLocaleString()}</p>
            <button onClick={() => removeItem(item.id)}
                    className="text-red-500 hover:text-red-700">✕</button>
          </div>
        </div>
      ))}
      <div className="mt-4 flex justify-between items-center">
        <p className="text-xl font-bold">Total: ${total.toLocaleString()}</p>
        <button onClick={handleCheckout}
                className="bg-green-600 text-white px-6 py-2 rounded-lg">
          Confirmar Pedido
        </button>
      </div>
    </div>
  );
}
