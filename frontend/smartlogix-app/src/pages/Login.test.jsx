import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import Login from "./Login";
import { authAPI } from "../services/api";

// Login.jsx's <label> elements are not associated to their inputs via
// htmlFor/id (a pre-existing markup gap, out of scope to fix here), so
// getByLabelText can't resolve them — placeholder text is the next-best
// accessible query per testing-library's own query priority.
const getEmailInput = () => screen.getByPlaceholderText(/admin@smartlogix\.cl/i);
const getPasswordInput = () => screen.getByPlaceholderText("••••••••");

vi.mock("../services/api", () => ({
  authAPI: { login: vi.fn(), register: vi.fn(), logout: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("location", { ...window.location, reload: vi.fn() });
});

afterEach(() => vi.unstubAllGlobals());

describe("Login", () => {
  it("renders the login form", () => {
    render(<Login />);

    expect(screen.getByRole("heading", { name: /iniciar sesión/i })).toBeInTheDocument();
    expect(getEmailInput()).toBeInTheDocument();
    expect(getPasswordInput()).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ingresar/i })).toBeInTheDocument();
  });

  it("shows a client-side error and does not call authAPI.login when fields are empty", async () => {
    render(<Login />);

    const form = screen.getByRole("button", { name: /ingresar/i }).closest("form");
    fireEvent.submit(form);

    expect(await screen.findByText("Completa todos los campos")).toBeInTheDocument();
    expect(authAPI.login).not.toHaveBeenCalled();
  });

  it("calls authAPI.login with the entered credentials and reloads on success", async () => {
    authAPI.login.mockResolvedValueOnce({ data: {} });
    render(<Login />);

    await userEvent.type(getEmailInput(), "user@smartlogix.cl");
    await userEvent.type(getPasswordInput(), "correct-pass");
    await userEvent.click(screen.getByRole("button", { name: /ingresar/i }));

    expect(authAPI.login).toHaveBeenCalledWith({ email: "user@smartlogix.cl", password: "correct-pass" });
    await waitFor(() => expect(window.location.reload).toHaveBeenCalled());
  });

  it("shows the backend error message when login is rejected", async () => {
    authAPI.login.mockRejectedValueOnce({ response: { data: { message: "Credenciales inválidas" } } });
    render(<Login />);

    await userEvent.type(getEmailInput(), "user@smartlogix.cl");
    await userEvent.type(getPasswordInput(), "wrong-pass");
    await userEvent.click(screen.getByRole("button", { name: /ingresar/i }));

    expect(await screen.findByText("Credenciales inválidas")).toBeInTheDocument();
    expect(window.location.reload).not.toHaveBeenCalled();
  });

  it("toggles to register mode and shows the success message on registration", async () => {
    authAPI.register.mockResolvedValueOnce({ data: {} });
    render(<Login />);

    await userEvent.click(screen.getByRole("button", { name: /crear una/i }));
    expect(screen.getByRole("heading", { name: /crear cuenta/i })).toBeInTheDocument();

    await userEvent.type(getEmailInput(), "nuevo@smartlogix.cl");
    await userEvent.type(getPasswordInput(), "nueva-pass");
    await userEvent.click(screen.getByRole("button", { name: /crear cuenta/i }));

    expect(authAPI.register).toHaveBeenCalledWith({ email: "nuevo@smartlogix.cl", password: "nueva-pass" });
    expect(await screen.findByText("¡Cuenta creada! Ahora inicia sesión.")).toBeInTheDocument();
  });
});
