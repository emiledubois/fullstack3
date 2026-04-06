import { createContext, useContext, useReducer } from "react";

// Observer Pattern: cualquier componente suscrito recibe cambios del carrito
const CartContext = createContext();

function cartReducer(state, action) {
  switch (action.type) {
    case "ADD_ITEM": {
      const existing = state.find(i => i.id === action.payload.id);
      if (existing)
        return state.map(i => i.id === action.payload.id ? {...i, qty: i.qty+1} : i);
      return [...state, { ...action.payload, qty: 1 }];
    }
    case "REMOVE_ITEM": return state.filter(i => i.id !== action.payload);
    case "CLEAR":       return [];
    default:            return state;
  }
}

export function CartProvider({ children }) {
  const [cart, dispatch] = useReducer(cartReducer, []);
  const addItem    = item => dispatch({ type:"ADD_ITEM",    payload:item });
  const removeItem = id   => dispatch({ type:"REMOVE_ITEM", payload:id });
  const clearCart  = ()   => dispatch({ type:"CLEAR" });
  const total = cart.reduce((sum, i) => sum + i.price * i.qty, 0);
  return (
    <CartContext.Provider value={{ cart, addItem, removeItem, clearCart, total }}>
      {children}
    </CartContext.Provider>
  );
}

export const useCart = () => useContext(CartContext);
