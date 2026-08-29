import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Envios from "./Envios";
import { enviosAPI } from "../services/api";

vi.mock("../services/api", () => ({
  enviosAPI: { getAll: vi.fn(), create: vi.fn(), updateStatus: vi.fn() },
}));

const ENVIOS = [
  { id: 1, pedidoId: 10, tipoEnvio: "TERRESTRE", transportista: "Chilexpress", destino: "Santiago", status: "CREADO" },
  { id: 2, pedidoId: 11, tipoEnvio: "EXPRESS", transportista: "Starken", destino: "Valparaíso", status: "ENTREGADO" },
];

beforeEach(() => {
  vi.clearAllMocks();
  enviosAPI.getAll.mockResolvedValue({ data: ENVIOS });
});

// Envios.jsx's form <label> elements are not associated to their inputs via
// htmlFor/id (same pre-existing gap as Login.jsx had — out of scope to fix
// here, only Login.jsx's gap was in scope). Select by role + DOM order
// instead (matches the fixed [pedidoId, transportista, destino] array order).
const getFormInputs = () => ({
  pedidoId:      screen.getByRole("spinbutton"),
  transportista: screen.getAllByRole("textbox")[0],
  destino:       screen.getAllByRole("textbox")[1],
});

describe("Envios", () => {
  it("renders envíos grouped into their status columns", async () => {
    render(<Envios />);

    expect(await screen.findByText("Pedido #10")).toBeInTheDocument();
    expect(screen.getByText("Pedido #11")).toBeInTheDocument();
  });

  it("shows the empty column placeholder for statuses with no envíos", async () => {
    render(<Envios />);
    await screen.findByText("Pedido #10");

    // ASIGNADO and EN_RUTA have no envíos in the fixture
    expect(screen.getAllByText("Vacío")).toHaveLength(2);
  });

  it("creates a new envío and reloads the board", async () => {
    enviosAPI.create.mockResolvedValueOnce({ data: {} });
    render(<Envios />);
    await screen.findByText("Pedido #10");

    const inputs = getFormInputs();
    await userEvent.type(inputs.pedidoId, "20");
    await userEvent.type(inputs.transportista, "Correos de Chile");
    await userEvent.type(inputs.destino, "Concepción");
    await userEvent.click(screen.getByRole("button", { name: /crear envío/i }));

    expect(enviosAPI.create).toHaveBeenCalledWith(
      expect.objectContaining({ pedidoId: 20, transportista: "Correos de Chile", destino: "Concepción" })
    );
    expect(enviosAPI.getAll).toHaveBeenCalledTimes(2);
  });

  it("shows the backend error message when creating an envío fails", async () => {
    enviosAPI.create.mockRejectedValueOnce({ response: { data: { message: "Pedido no encontrado" } } });
    render(<Envios />);
    await screen.findByText("Pedido #10");

    await userEvent.type(getFormInputs().pedidoId, "999");
    await userEvent.click(screen.getByRole("button", { name: /crear envío/i }));

    expect(await screen.findByText("Pedido no encontrado")).toBeInTheDocument();
  });

  it("advances an envío to its next status and reloads the board", async () => {
    enviosAPI.updateStatus.mockResolvedValueOnce({ data: {} });
    render(<Envios />);
    await screen.findByText("Pedido #10");

    await userEvent.click(screen.getByRole("button", { name: /asignado/i }));

    expect(enviosAPI.updateStatus).toHaveBeenCalledWith(1, "ASIGNADO");
    expect(enviosAPI.getAll).toHaveBeenCalledTimes(2);
  });

  it("does not render an advance button for envíos already ENTREGADO", async () => {
    render(<Envios />);
    await screen.findByText("Pedido #11");

    const entregadoCard = screen.getByText("Pedido #11").closest("div");
    expect(entregadoCard.querySelector("button")).toBeNull();
  });
});
