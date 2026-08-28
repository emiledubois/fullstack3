import { render, screen } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import App from "./App";
import { sessionAPI } from "./services/api";

// The default page ("dashboard") mounts Dashboard.jsx, which imports
// dashboardAPI from this same module — it must be present in the mock
// (returning a never-resolving promise) even though this suite doesn't
// assert on Dashboard's own behavior, otherwise Vitest's strict mock
// throws on the missing export as soon as Dashboard's effect runs.
vi.mock("./services/api", () => ({
  authAPI: { logout: vi.fn() },
  sessionAPI: { get: vi.fn() },
  dashboardAPI: { get: vi.fn(() => new Promise(() => {})) },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("location", { ...window.location, reload: vi.fn(), pathname: "/" });
});

afterEach(() => vi.unstubAllGlobals());

describe("App", () => {
  it("renders the Cargando... state before sessionAPI.get() settles", () => {
    sessionAPI.get.mockReturnValue(new Promise(() => {}));

    render(<App />);

    expect(screen.getByText(/cargando/i)).toBeInTheDocument();
  });

  it("renders the authenticated shell when sessionAPI.get() resolves", async () => {
    sessionAPI.get.mockResolvedValueOnce({ data: {} });

    render(<App />);

    expect(await screen.findByRole("button", { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
  });

  it("renders Login when sessionAPI.get() rejects (no valid cookie)", async () => {
    sessionAPI.get.mockRejectedValueOnce(new Error("401"));

    render(<App />);

    expect(await screen.findByRole("heading", { name: /smartlogix/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ingresar/i })).toBeInTheDocument();
  });

  it("renders PagoResultado and never calls sessionAPI.get() on the /pago-resultado path", async () => {
    vi.stubGlobal("location", { ...window.location, reload: vi.fn(), pathname: "/pago-resultado", search: "" });

    render(<App />);

    expect(await screen.findByText(/pago exitoso|verificando pago/i)).toBeInTheDocument();
    expect(sessionAPI.get).not.toHaveBeenCalled();
  });
});
