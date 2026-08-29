import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Dashboard from "./Dashboard";
import { dashboardAPI } from "../services/api";

vi.mock("../services/api", () => ({
  dashboardAPI: { get: vi.fn() },
}));

const BASE_DATA = {
  estadoServicios: "OK",
  generadoEn: "2026-01-01T12:00:00Z",
  metricas: {
    totalProductos: 10,
    totalPedidos: 5,
    totalEnvios: 3,
    productosConAlertaStock: 0,
    pedidosPendientes: 1,
    enviosEnRuta: 2,
    valorTotalInventario: 150000,
  },
  ultimosPedidos: [],
  alertasStock: [],
};

beforeEach(() => vi.clearAllMocks());

describe("Dashboard", () => {
  it("renders the loading spinner before data resolves", () => {
    dashboardAPI.get.mockReturnValue(new Promise(() => {}));
    render(<Dashboard />);

    expect(screen.getByText(/cargando datos/i)).toBeInTheDocument();
  });

  it("renders the stat cards once data resolves", async () => {
    dashboardAPI.get.mockResolvedValueOnce({ data: BASE_DATA });
    render(<Dashboard />);

    expect(await screen.findByText("Productos")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText(/servicios: ok/i)).toBeInTheDocument();
  });

  it("shows the empty-state copy when there are no recent orders or alerts", async () => {
    dashboardAPI.get.mockResolvedValueOnce({ data: BASE_DATA });
    render(<Dashboard />);

    expect(await screen.findByText("Sin pedidos aún")).toBeInTheDocument();
    expect(screen.getByText(/sin alertas de stock/i)).toBeInTheDocument();
  });

  it("renders recent orders and stock alerts when present", async () => {
    dashboardAPI.get.mockResolvedValueOnce({
      data: {
        ...BASE_DATA,
        ultimosPedidos: [{ id: 1, clienteNombre: "Juan Pérez", creadoEn: "2026-01-05T00:00:00Z", tipoPedido: "NACIONAL", total: 20000, status: "PENDIENTE" }],
        alertasStock: [{ id: 2, nombre: "Caja", sku: "SKU-1", bodega: "Central", stockActual: 2, umbralMinimo: 10 }],
      },
    });
    render(<Dashboard />);

    expect(await screen.findByText("Juan Pérez")).toBeInTheDocument();
    expect(screen.getByText("Caja")).toBeInTheDocument();
    expect(screen.getByText("2 unid.")).toBeInTheDocument();
  });

  it("calls onNavigate with the target page when the shortcut links are clicked", async () => {
    dashboardAPI.get.mockResolvedValueOnce({ data: BASE_DATA });
    const onNavigate = vi.fn();
    render(<Dashboard onNavigate={onNavigate} />);

    await screen.findByText("Productos");
    await userEvent.click(screen.getByText(/ver todos/i));
    await userEvent.click(screen.getByText(/ir al inventario/i));

    expect(onNavigate).toHaveBeenCalledWith("pedidos");
    expect(onNavigate).toHaveBeenCalledWith("inventario");
  });

  // Dashboard.jsx has no error UI: a rejected dashboardAPI.get() is swallowed
  // by .catch(console.error), but the .finally(() => setLoading(false)) still
  // clears the spinner — so the page silently renders the zero-value default
  // stats instead of either an error message or staying on the spinner
  // forever. This is a pre-existing UX gap (flagged in
  // docs/designs/frontend-test-infrastructure.md §Open Questions), not
  // introduced or fixed by this test; this test documents the actual
  // behavior so a future fix is a deliberate change, not a silent one.
  it("silently renders zero-value stats with no error UI when the fetch rejects (documented pre-existing gap)", async () => {
    dashboardAPI.get.mockRejectedValueOnce(new Error("network error"));
    render(<Dashboard />);

    await waitFor(() => expect(screen.queryByText(/cargando datos/i)).not.toBeInTheDocument());
    expect(screen.getByText("Productos")).toBeInTheDocument();
    expect(screen.getAllByText("0")).not.toHaveLength(0);
  });
});
