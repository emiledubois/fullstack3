import { Home, Package, ShoppingCart, Truck, User, LogOut } from "lucide-react";

const NAV = [
  { key: "dashboard",  label: "Dashboard",  icon: Home },
  { key: "inventario", label: "Inventario", icon: Package },
  { key: "pedidos",    label: "Pedidos",    icon: ShoppingCart },
  { key: "envios",     label: "Envíos",     icon: Truck },
  { key: "mis-datos",  label: "Mis datos",  icon: User },
];

export default function Sidebar({ active, onNavigate, onLogout }) {
  return (
    <aside className="w-[248px] bg-white border-r border-line-400 flex flex-col shrink-0">
      <div className="p-5 border-b border-line-300">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-nav bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-sm shrink-0" />
          <div>
            <div className="font-heading font-bold text-sm text-ink-900">SmartLogix</div>
            <div className="text-ink-400 text-xs">Plataforma Logística</div>
          </div>
        </div>
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {NAV.map(n => {
          const Icon = n.icon;
          const isActive = active === n.key;
          return (
            <button
              key={n.key}
              onClick={() => onNavigate?.(n.key)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-nav text-sm
                          transition-colors text-left ${
                isActive
                  ? "bg-brand-50 text-brand-700 font-semibold"
                  : "text-ink-500 font-medium hover:bg-app-bg"
              }`}
            >
              <Icon className="w-4 h-4" strokeWidth={2} />
              {n.label}
            </button>
          );
        })}
      </nav>
      <div className="p-3 border-t border-line-300">
        <button onClick={onLogout}
                className="w-full flex items-center gap-3 px-3 py-2 rounded-nav text-sm
                           font-medium text-ink-500 hover:bg-app-bg transition-colors">
          <LogOut className="w-4 h-4" strokeWidth={2} /> Cerrar sesión
        </button>
      </div>
    </aside>
  );
}
