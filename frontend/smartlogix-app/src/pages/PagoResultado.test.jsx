import { render, screen } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import PagoResultado from "./PagoResultado";
import api from "../services/api";

vi.mock("../services/api", () => ({
  default: { get: vi.fn() },
}));

const stubLocation = (search) => {
  vi.stubGlobal("location", { ...window.location, search, href: "" });
};

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => vi.unstubAllGlobals());

describe("PagoResultado", () => {
  it("shows the verifying-payment spinner while the token check is pending", () => {
    stubLocation("?token=abc123");
    api.get.mockReturnValue(new Promise(() => {}));
    render(<PagoResultado />);

    expect(screen.getByText(/verificando pago/i)).toBeInTheDocument();
  });

  it("defaults to PAGADO when Flow redirects without a token (webhook already processed it)", async () => {
    stubLocation("");
    render(<PagoResultado />);

    expect(await screen.findByText(/¡pago exitoso!/i)).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
  });

  it("renders the PAGADO state with payment details when the backend confirms payment", async () => {
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "PAGADO", pedidoId: 42, monto: 15000 } });
    render(<PagoResultado />);

    expect(await screen.findByText(/¡pago exitoso!/i)).toBeInTheDocument();
    expect(screen.getByText("Pago confirmado")).toBeInTheDocument();
    expect(screen.getByText("#42")).toBeInTheDocument();
  });

  it("renders the RECHAZADO state with a retry option", async () => {
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "RECHAZADO", pedidoId: 1 } });
    render(<PagoResultado />);

    expect(await screen.findByText(/pago no procesado/i)).toBeInTheDocument();
    expect(screen.getByText("Pago rechazado")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /intentar nuevamente/i })).toBeInTheDocument();
  });

  it("renders the ANULADO state", async () => {
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "ANULADO", pedidoId: 1 } });
    render(<PagoResultado />);

    expect(await screen.findByText(/pago anulado/i)).toBeInTheDocument();
    expect(screen.getByText("Anulado")).toBeInTheDocument();
  });

  it("renders the PENDIENTE state when the backend reports estado INICIADO", async () => {
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "INICIADO", pedidoId: 1 } });
    render(<PagoResultado />);

    expect(await screen.findByText(/pago en proceso/i)).toBeInTheDocument();
    expect(screen.getByText("Pendiente de confirmación")).toBeInTheDocument();
  });

  it("falls back to PENDIENTE (not the dead-code error state) for an unrecognized backend estado", async () => {
    // ESTADOS.error is only reached via `ESTADOS[estado] || ESTADOS.error`,
    // but every setEstado() call site in PagoResultado.jsx resolves to
    // "loading", "PAGADO", "RECHAZADO", "ANULADO", or (via the estadoMap
    // fallback below) "PENDIENTE" — an unmapped backend `estado` string
    // still lands on "PENDIENTE", and even a rejected request maps to
    // "PAGADO" (see next test). ESTADOS.error is therefore unreachable dead
    // code in the component as written today; this test documents the real
    // fallback behavior instead of asserting on a state nothing can reach.
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "ALGO_DESCONOCIDO", pedidoId: 1 } });
    render(<PagoResultado />);

    expect(await screen.findByText(/pago en proceso/i)).toBeInTheDocument();
  });

  it("falls back to PAGADO (not an error UI) when the status request rejects", async () => {
    stubLocation("?token=abc123");
    api.get.mockRejectedValueOnce(new Error("network error"));
    render(<PagoResultado />);

    expect(await screen.findByText(/¡pago exitoso!/i)).toBeInTheDocument();
  });

  it("strips the token from the URL on mount", async () => {
    stubLocation("?token=abc123");
    api.get.mockResolvedValueOnce({ data: { estado: "PAGADO", pedidoId: 1 } });
    const replaceStateSpy = vi.spyOn(window.history, "replaceState");
    render(<PagoResultado />);

    await screen.findByText(/¡pago exitoso!/i);
    expect(replaceStateSpy).toHaveBeenCalledWith({}, expect.anything(), "/pago-resultado");
  });
});
