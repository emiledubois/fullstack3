import { render, screen, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import Pedidos from "./Pedidos";
import { pedidosAPI, inventarioAPI } from "../services/api";

vi.mock("../services/api", () => ({
  pedidosAPI:    { getAll: vi.fn(), create: vi.fn() },
  inventarioAPI: { getAll: vi.fn() },
}));

const PRODUCTOS = [
  { id: 1, sku: "SKU-1", nombre: "Caja grande", precioUnitario: 5000, stockActual: 20 },
];

// Pedidos.jsx formats money with the runtime's default toLocaleString()
// (not the es-CL formatCLP helper), so compute expected strings the same
// way rather than hardcoding a thousands-separator that depends on locale.
const money = (n) => `$${n.toLocaleString()}`;

const PEDIDOS = [
  { id: 1, clienteNombre: "Juan Pérez", userEmail: "juan@smartlogix.cl", total: 7500, tipoPedido: "NACIONAL", destino: "Santiago", status: "PENDIENTE", creadoEn: "2026-01-01T00:00:00Z" },
  { id: 2, clienteNombre: "Ana López", userEmail: "ana@smartlogix.cl", total: 13500, tipoPedido: "INTERNACIONAL", destino: "Lima", status: "ENTREGADO", creadoEn: "2026-01-02T00:00:00Z" },
];

// Pedidos.jsx's form <label> elements are not associated to their inputs via
// htmlFor/id (same pre-existing gap as Login.jsx had — out of scope to fix
// here, only Login.jsx's gap was in scope). Select by role + DOM order
// instead: the SKU/nombre search box renders first, followed by
// Email/Nombre/Destino in the "Datos del pedido" grid.
const getFormInputs = () => ({
  skuSearch: screen.getByPlaceholderText(/escribe sku o nombre/i),
  email:     screen.getAllByRole("textbox")[1],
  nombre:    screen.getAllByRole("textbox")[2],
  destino:   screen.getAllByRole("textbox")[3],
});

beforeEach(() => {
  vi.clearAllMocks();
  pedidosAPI.getAll.mockResolvedValue({ data: PEDIDOS });
  inventarioAPI.getAll.mockResolvedValue({ data: PRODUCTOS });
  // Pedidos.jsx reads/writes localStorage directly (per-pedido cart line
  // detail) when a row is expanded or a multi-line pedido is created — stub
  // it explicitly rather than relying on the jsdom environment's default.
  vi.stubGlobal("localStorage", {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  });
});

afterEach(() => vi.unstubAllGlobals());

describe("Pedidos", () => {
  it("renders the loaded pedidos in the table", async () => {
    render(<Pedidos />);

    expect(await screen.findByText("Juan Pérez")).toBeInTheDocument();
    expect(screen.getByText("Ana López")).toBeInTheDocument();
  });

  it("shows product suggestions when typing in the SKU search box", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.type(getFormInputs().skuSearch, "caja");

    expect(await screen.findByText("Caja grande")).toBeInTheDocument();
  });

  it("adds a picked product to the cart and computes the total", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.type(getFormInputs().skuSearch, "caja");
    await userEvent.click(await screen.findByText("Caja grande"));

    expect(screen.getByText("TOTAL")).toBeInTheDocument();
    // qty=1, so unit price, line subtotal, and cart total all read the same amount
    expect(screen.getAllByText(money(5000))).toHaveLength(3);
  });

  it("increments the cart quantity and recalculates the subtotal", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.type(getFormInputs().skuSearch, "caja");
    await userEvent.click(await screen.findByText("Caja grande"));
    await userEvent.click(screen.getByRole("button", { name: "+" }));

    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getAllByText(money(10000))).toHaveLength(2); // line subtotal + cart total
  });

  it("removes a line from the cart", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.type(getFormInputs().skuSearch, "caja");
    await userEvent.click(await screen.findByText("Caja grande"));
    await userEvent.click(screen.getByRole("button", { name: "x" }));

    expect(screen.queryByText("TOTAL")).not.toBeInTheDocument();
  });

  it("disables the submit button while the cart is empty", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    expect(screen.getByRole("button", { name: /crear pedido/i })).toBeDisabled();
  });

  it("shows a client-side error and does not call the API when submitted with an empty cart", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    const form = screen.getByRole("button", { name: /crear pedido/i }).closest("form");
    fireEvent.submit(form);

    expect(await screen.findByText("Agrega al menos un producto al pedido.")).toBeInTheDocument();
    expect(pedidosAPI.create).not.toHaveBeenCalled();
  });

  it("creates a pedido with the cart data and reloads the list", async () => {
    pedidosAPI.create.mockResolvedValueOnce({ data: { id: 99 } });
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    const inputs = getFormInputs();
    await userEvent.type(inputs.skuSearch, "caja");
    await userEvent.click(await screen.findByText("Caja grande"));
    await userEvent.type(inputs.email, "cliente@smartlogix.cl");
    await userEvent.type(inputs.nombre, "Cliente Nuevo");
    await userEvent.type(inputs.destino, "Concepción");
    await userEvent.click(screen.getByRole("button", { name: /crear pedido/i }));

    expect(pedidosAPI.create).toHaveBeenCalledWith(
      expect.objectContaining({
        userEmail: "cliente@smartlogix.cl",
        clienteNombre: "Cliente Nuevo",
        destino: "Concepción",
        total: 5000,
        productoId: 1,
        cantidad: 1,
      })
    );
    expect(pedidosAPI.getAll).toHaveBeenCalledTimes(2);
  });

  it("shows the backend error message when creating a pedido fails", async () => {
    pedidosAPI.create.mockRejectedValueOnce({ response: { data: { message: "Stock insuficiente" } } });
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.type(getFormInputs().skuSearch, "caja");
    await userEvent.click(await screen.findByText("Caja grande"));
    await userEvent.click(screen.getByRole("button", { name: /crear pedido/i }));

    expect(await screen.findByText("Stock insuficiente")).toBeInTheDocument();
  });

  it("filters the pedidos list by status", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.click(screen.getByRole("button", { name: /^entregado$/i }));

    expect(screen.queryByText("Juan Pérez")).not.toBeInTheDocument();
    expect(screen.getByText("Ana López")).toBeInTheDocument();
  });

  it("expands a row to show its detail panel", async () => {
    render(<Pedidos />);
    await screen.findByText("Juan Pérez");

    await userEvent.click(screen.getByText("Juan Pérez"));

    const detail = await screen.findByText(/detalle del pedido #1/i);
    expect(detail).toBeInTheDocument();
    expect(within(detail.closest("td")).getByText("Cliente")).toBeInTheDocument();
  });
});
