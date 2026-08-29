import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Inventario from "./Inventario";
import { inventarioAPI } from "../services/api";

vi.mock("../services/api", () => ({
  inventarioAPI: { getAll: vi.fn(), create: vi.fn() },
}));

const PRODUCTOS = [
  { id: 1, sku: "SKU-1", nombre: "Caja grande", bodega: "Central", precioUnitario: 5000, stockActual: 20, umbralMinimo: 5 },
  { id: 2, sku: "SKU-2", nombre: "Caja chica", bodega: "Norte", precioUnitario: 2000, stockActual: 1, umbralMinimo: 10 },
];

beforeEach(() => {
  vi.clearAllMocks();
  inventarioAPI.getAll.mockResolvedValue({ data: PRODUCTOS });
});

// Inventario.jsx's "Agregar producto" <label> elements are not associated
// to their inputs via htmlFor/id (same pre-existing gap as Login.jsx had —
// out of scope to fix here, only Login.jsx's gap was in scope). Select by
// role + DOM order (matches the fixed FIELDS array order) instead.
const getFormInputs = () => ({
  sku:            screen.getAllByRole("textbox")[0],
  nombre:         screen.getAllByRole("textbox")[1],
  precioUnitario: screen.getAllByRole("spinbutton")[0],
  stockActual:    screen.getAllByRole("spinbutton")[1],
  umbralMinimo:   screen.getAllByRole("spinbutton")[2],
});

describe("Inventario", () => {
  it("renders the loaded products in the table", async () => {
    render(<Inventario />);

    expect(await screen.findByText("Caja grande")).toBeInTheDocument();
    expect(screen.getByText("Caja chica")).toBeInTheDocument();
  });

  it("shows the empty-state message when there are no products", async () => {
    inventarioAPI.getAll.mockResolvedValue({ data: [] });
    render(<Inventario />);

    expect(await screen.findByText("Sin productos aún")).toBeInTheDocument();
  });

  it("marks products below their minimum threshold as low stock", async () => {
    render(<Inventario />);

    await screen.findByText("Caja chica");
    expect(screen.getAllByText("⚠ Bajo")).toHaveLength(1);
    expect(screen.getAllByText("✓ OK")).toHaveLength(1);
  });

  it("filters the table by search text across nombre, sku and bodega", async () => {
    render(<Inventario />);
    await screen.findByText("Caja grande");

    await userEvent.type(screen.getByPlaceholderText(/buscar por nombre/i), "norte");

    expect(screen.queryByText("Caja grande")).not.toBeInTheDocument();
    expect(screen.getByText("Caja chica")).toBeInTheDocument();
  });

  it("shows a no-results message when the search matches nothing", async () => {
    render(<Inventario />);
    await screen.findByText("Caja grande");

    await userEvent.type(screen.getByPlaceholderText(/buscar por nombre/i), "no-existe");

    expect(await screen.findByText("Sin resultados para la búsqueda")).toBeInTheDocument();
  });

  it("creates a product with numeric fields and reloads the list", async () => {
    inventarioAPI.create.mockResolvedValueOnce({ data: {} });
    render(<Inventario />);
    await screen.findByText("Caja grande");

    const inputs = getFormInputs();
    await userEvent.type(inputs.sku, "SKU-3");
    await userEvent.type(inputs.nombre, "Pallet");
    await userEvent.type(inputs.precioUnitario, "1000");
    await userEvent.type(inputs.stockActual, "50");
    await userEvent.type(inputs.umbralMinimo, "5");
    await userEvent.click(screen.getByRole("button", { name: /agregar producto/i }));

    expect(inventarioAPI.create).toHaveBeenCalledWith(
      expect.objectContaining({ sku: "SKU-3", nombre: "Pallet", precioUnitario: 1000, stockActual: 50, umbralMinimo: 5 })
    );
    expect(inventarioAPI.getAll).toHaveBeenCalledTimes(2);
  });

  it("shows the backend error message when creating a product fails", async () => {
    inventarioAPI.create.mockRejectedValueOnce({ response: { data: { message: "SKU duplicado" } } });
    render(<Inventario />);
    await screen.findByText("Caja grande");

    const inputs = getFormInputs();
    await userEvent.type(inputs.sku, "SKU-1");
    await userEvent.type(inputs.nombre, "Caja repetida");
    await userEvent.type(inputs.precioUnitario, "1000");
    await userEvent.type(inputs.stockActual, "50");
    await userEvent.type(inputs.umbralMinimo, "5");
    await userEvent.click(screen.getByRole("button", { name: /agregar producto/i }));

    expect(await screen.findByText("SKU duplicado")).toBeInTheDocument();
  });
});
