import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import MisDatos from "./MisDatos";
import { usuariosAPI } from "../services/api";

vi.mock("../services/api", () => ({
  usuariosAPI: { getMisDatos: vi.fn() },
  formatCLP: (monto) => `$${monto ?? 0}`,
  formatFechaChile: (fecha) => (fecha ? `fecha:${fecha}` : "—"),
}));

const cuenta = {
  email: "user@smartlogix.cl",
  role: "ROLE_USER",
  cuentaCreadaEn: "2026-01-01T00:00:00Z",
};

const baseDatos = {
  estadoAgregacion: "OK",
  cuenta,
  pedidos: [],
  envios: [],
  generadoEn: "2026-01-02T00:00:00Z",
};

beforeEach(() => vi.clearAllMocks());

describe("MisDatos", () => {
  it("shows the loading spinner before data resolves", () => {
    usuariosAPI.getMisDatos.mockReturnValue(new Promise(() => {}));

    render(<MisDatos />);

    expect(screen.getByText(/cargando/i)).toBeInTheDocument();
  });

  it("shows the generic error state with a working Reintentar button", async () => {
    usuariosAPI.getMisDatos.mockRejectedValueOnce(new Error("network"));

    render(<MisDatos />);

    expect(await screen.findByText(/no pudimos cargar tus datos/i)).toBeInTheDocument();
    const retryButton = screen.getByRole("button", { name: /reintentar/i });

    usuariosAPI.getMisDatos.mockResolvedValueOnce({ data: baseDatos });
    await userEvent.click(retryButton);

    expect(usuariosAPI.getMisDatos).toHaveBeenCalledTimes(2);
    expect(await screen.findByText("user@smartlogix.cl")).toBeInTheDocument();
  });

  it("renders no degradation banner when estadoAgregacion is OK", async () => {
    usuariosAPI.getMisDatos.mockResolvedValueOnce({ data: baseDatos });

    render(<MisDatos />);

    await screen.findByText("user@smartlogix.cl");
    expect(screen.queryByText(/algunos de tus datos no se pudieron cargar/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/no pudimos obtener tus datos en este momento/i)).not.toBeInTheDocument();
  });

  it("renders the PARCIAL degradation banner when estadoAgregacion is PARCIAL", async () => {
    usuariosAPI.getMisDatos.mockResolvedValueOnce({
      data: { ...baseDatos, estadoAgregacion: "PARCIAL" },
    });

    render(<MisDatos />);

    expect(await screen.findByText(/algunos de tus datos no se pudieron cargar/i)).toBeInTheDocument();
  });

  it("renders the ERROR degradation banner when estadoAgregacion is ERROR", async () => {
    usuariosAPI.getMisDatos.mockResolvedValueOnce({
      data: { ...baseDatos, estadoAgregacion: "ERROR" },
    });

    render(<MisDatos />);

    expect(await screen.findByText(/no pudimos obtener tus datos en este momento/i)).toBeInTheDocument();
  });

  it("renders CuentaAusenteCard instead of CuentaCard when cuenta is null", async () => {
    usuariosAPI.getMisDatos.mockResolvedValueOnce({ data: { ...baseDatos, cuenta: null } });

    render(<MisDatos />);

    expect(await screen.findByText(/no pudimos confirmar los datos de tu cuenta/i)).toBeInTheDocument();
    expect(screen.queryByText("user@smartlogix.cl")).not.toBeInTheDocument();
  });

  it("renders the Ley 21.719 access-right disclaimer on the success render", async () => {
    usuariosAPI.getMisDatos.mockResolvedValueOnce({ data: baseDatos });

    render(<MisDatos />);

    expect(await screen.findByText(/derecho de acceso/i)).toBeInTheDocument();
    expect(screen.getByText(/ley 21\.719/i)).toBeInTheDocument();
  });
});
