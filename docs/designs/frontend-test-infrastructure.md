# Frontend Test Infrastructure (Vitest + React Testing Library)

## 1. Summary

`frontend/smartlogix-app` (React 19, Vite 8) currently has zero test tooling: no runner, no
component-testing library, no `test` script in `package.json`, and CI (`frontend-ci.yml`) only
runs `lint` and `build`. The last dev-cycle's QA had to verify frontend behavior by running the
full docker-compose stack and `curl`ing the gateway, or grepping the production bundle — slow,
flaky, and unable to catch component-level logic regressions (state transitions, conditional
rendering, form validation) before they reach a human. This task adds a real, CI-enforced,
component-level test runner and a documented testing convention, plus a baseline of real tests for
three representative components, so that the upcoming frontend visual revamp has an actual
regression net to run during its QA phase instead of an empty config. This is infrastructure, not
a feature — no new user-facing behavior is introduced, and several of the usual design-doc sections
(OWASP, Chilean compliance) are intentionally thin, as scoped by the requester.

## 2. Affected services

- **Frontend only** (`frontend/smartlogix-app`). No backend service changes.
- CI: `.github/workflows/frontend-ci.yml` gets a new blocking `test` step.
- This is **independent, self-contained tooling work** — not a Saga, not a Facade. No
  coordination with backend services is needed; tests run against mocked API modules, never
  against a live backend.

## 3. Current-state findings (read before designing)

Confirmed by reading the actual source:

- `package.json` has `dev`/`build`/`lint`/`preview` scripts only — **no `test` script, no test
  dependencies at all**.
- `vite.config.js` is minimal: `@vitejs/plugin-react` + a dev-server proxy of `/api` to the
  gateway. No existing `test` block.
- `eslint.config.js` is flat-config ESLint 9, browser globals, no test-specific globals/plugin
  (e.g. no `eslint-plugin-vitest` or `vitest-globals` yet — will need `globals.vitest` or an
  override once tests exist, otherwise ESLint will flag `describe`/`it`/`expect` as undefined in
  CI's `npm run lint` step).
- **`react-router-dom` is a dependency but is genuinely unused** — confirmed via `grep` across
  `src/`: zero references to `BrowserRouter`, `Routes`, `Route`, or `useNavigate`. Routing is done
  by hand in `App.jsx` via `useState("dashboard")` + conditional rendering, and a single hardcoded
  path check (`window.location.pathname === "/pago-resultado"`) for the Flow payment-redirect
  page. Tests must target this actual mechanism, not an assumed router.
- `src/services/api.js` exports a single configured axios instance (`withCredentials: true`, JWT
  lives in an httpOnly cookie `sl_jwt` — never read/written by JS) plus **named, per-module API
  object groups** (`authAPI`, `sessionAPI`, `inventarioAPI`, `pedidosAPI`, `enviosAPI`, `sagaAPI`,
  `dashboardAPI`, `usuariosAPI`, `pagoAPI`) and two formatting helpers (`formatCLP`,
  `formatFechaChile`). Pages import these named objects directly — they never touch `axios` or
  `api` directly except `PagoResultado.jsx`, which imports the default `api` instance directly for
  one ad-hoc call (`api.get('/pagos/por-token/:token')`).
- A global response interceptor in `api.js` forces `window.location.href = "/"` on any 401 that
  isn't `/auth/login`, `/auth/register`, or `/session` — this is a real navigation side effect that
  tests must be careful not to trigger accidentally (jsdom does not implement navigation and will
  throw "Not implemented" errors if untreated).
- `App.jsx` is the **security-relevant gate**: on mount it calls `sessionAPI.get()` (which hits
  `/api/session`, relying on the httpOnly cookie being sent automatically by the browser) and
  renders `<Login/>` on rejection, the authenticated shell on resolution, and bypasses the check
  entirely for `/pago-resultado`. It never reads `document.cookie` — the cookie is opaque to JS by
  design, so **tests must mock at the `sessionAPI` boundary, not by faking cookies in jsdom.**
- `Login.jsx` is a small, self-contained form component: local `useState` for
  email/password/isReg/msg/loading, calls `authAPI.login`/`authAPI.register`, shows a `msg`
  banner on error, and calls `window.location.reload()` on successful login.
- `MisDatos.jsx` is the most intricate state machine in the app: `loading` → `error` (with a retry
  button that re-fetches) → success, and on success an `estadoAgregacion` field (`OK` / `PARCIAL`
  / `ERROR`) drives three different conditional UI branches (`StatusBadge`, `EstadoBanner`,
  empty-state copy in `PedidosSection`/`EnviosSection`), plus a `CuentaAusenteCard` fallback when
  `data.cuenta` is null. It also renders the Ley 21.719 access-right disclaimer text — a
  compliance-relevant string, not just decoration.
- `Dashboard.jsx` fetches `dashboardAPI.get()` but on failure only does `.catch(console.error)` —
  **there is no error UI at all**, the page just stays in `loading` forever if the call rejects.
  This is a pre-existing gap, not something this task should silently paper over (see Open
  Questions).
- `Envios.jsx` (116 lines) and `Inventario.jsx` (137 lines) are simpler CRUD/kanban pages; `Pedidos.jsx`
  is the largest page (532 lines) and the most form-heavy. None of the three are in this cycle's
  baseline (see Scope).
- `.github/workflows/frontend-ci.yml` is a single job (`lint-and-build`) triggered on
  `frontend/**` changes: checkout → setup Node 20 → `npm ci` → `npm run lint` → `npm run build` →
  upload `dist/` artifact. No test step exists.
- `docs/COMPLIANCE_CL.md` confirms SmartLogix is single-tenant per PYME (no `tenant_id`/IDOR model
  today) and explicitly calls out that PII must never be logged in plaintext — relevant only
  insofar as test code/fixtures must not hardcode real-looking PII or credentials (see Security §A02/A09).

## 4. Chosen stack and justification

| Concern | Choice | Why |
|---|---|---|
| Test runner | **Vitest** (`vitest`) | Same Vite 8 pipeline as `npm run dev`/`build` (same plugins, same esbuild transform, same aliasing) — zero duplicate config, near-instant watch mode, native ESM/JSX support without a Babel/Jest transform layer. This is the standard, low-friction choice for a Vite project and avoids running two separate JS toolchains (Vite for the app, Jest+Babel for tests). |
| DOM environment | **jsdom** (not happy-dom) | `@testing-library/react` + `@testing-library/user-event` are developed and tested primarily against jsdom; happy-dom is faster but has known gaps in form-submission event sequencing, focus handling, and some layout/`getComputedStyle` behavior that this app's tests actually exercise (native `<form onSubmit>` in `Login.jsx`, `<button disabled>` states, `<select>` in `Envios.jsx`). At the current/near-term test volume (single digits of files), the jsdom performance penalty vs happy-dom is not material. Revisit happy-dom only if suite runtime becomes a real bottleneck as the revamp adds many more tests. |
| Component testing | **@testing-library/react** + **@testing-library/user-event** | Encourages testing via accessible queries (role/label/text) rather than implementation details (internal state, class names) — a good fit for a codebase about to go through a full visual (Tailwind/markup) revamp, since tests written against roles/text survive a markup rewrite that tests against DOM structure or CSS classes would not. |
| Assertions | **@testing-library/jest-dom** (via `@testing-library/jest-dom/vitest` entrypoint) | Adds `toBeInTheDocument()`, `toBeDisabled()`, `toHaveTextContent()` etc. — standard pairing, no Jest dependency required (the `/vitest` subpath registers matchers on Vitest's `expect` directly). |
| Coverage | **@vitest/coverage-v8** | Uses V8's native coverage counters (fast, no extra Istanbul instrumentation/transform step), consistent with Vite's esbuild-based pipeline. Reporting only this cycle, no threshold gate (see §6). |
| API/network mocking | **`vi.mock('../services/api')`** (module-level mock), **not MSW** | The app's entire API surface is a thin, already-abstracted module (`api.js`) exporting named function groups — components never construct raw axios calls or URLs inline. Mocking at that existing module boundary is simpler, faster, and requires no request-handler modeling (MSW would add a service-worker/interception layer with no material benefit here, since there is no shared retry/interceptor logic under test in components — the one interceptor that exists, the 401→redirect handler, is not exercised by any of the three baseline components and is called out as deferred). **Equally important operationally:** CI runners do not have docker-compose or any backend running — module-level mocking makes every test hermetic by construction (no accidental real network calls, no CI hangs waiting on a nonexistent gateway). Revisit MSW only if a future cycle needs to test cross-cutting request/response behavior (e.g. the interceptor itself, or if the revamp introduces a data-fetching library like react-query that expects network-level mocking). |
| Config location | Extend the **existing `vite.config.js`**, importing `defineConfig` from `vitest/config` instead of `vite`, adding a `test: {...}` block | Avoids a second config file duplicating the `plugins: [react()]` array and risking drift between dev/build config and test config. This is Vitest's own recommended pattern for Vite projects. |

## 5. Scope for this cycle vs deferred

### In scope now

1. Add devDependencies: `vitest`, `@vitest/coverage-v8`, `jsdom`, `@testing-library/react`,
   `@testing-library/user-event`, `@testing-library/jest-dom`.
2. Extend `vite.config.js` (import `defineConfig` from `vitest/config`) with:
   ```js
   test: {
     environment: "jsdom",
     globals: true,               // exposes describe/it/expect/vi as globals — see ESLint note
     setupFiles: "./src/test/setup.js",
     css: false,                  // Tailwind/PostCSS output isn't asserted on; skip CSS processing in tests
   }
   ```
3. `src/test/setup.js`: `import '@testing-library/jest-dom/vitest'` (registers matchers; with
   `globals: true`, React Testing Library's automatic `afterEach(cleanup)` self-registers because
   it detects a global `afterEach`).
4. `package.json` scripts: `"test": "vitest run"`, `"test:watch": "vitest"`,
   `"test:coverage": "vitest run --coverage"`.
5. ESLint: add a `test`-scoped override in `eslint.config.js` (files: `**/*.test.jsx`) that adds
   `globals.vitest` (or equivalent) so `npm run lint` doesn't fail on `describe`/`it`/`expect`/`vi`
   once test files exist — otherwise the existing (unmodified) lint step in CI breaks the moment
   the first test file is added.
6. Baseline real tests for **three components**, chosen to establish the pattern for the three
   distinct shapes of logic in this app, not for page-by-page coverage:
   - **`Login.jsx`** — simplest page; establishes the form-interaction + success/error pattern
     (fill fields, submit, assert `authAPI.login` called with correct payload, assert error banner
     on rejection, assert register/login toggle).
   - **`MisDatos.jsx`** — most intricate state machine in the app today (loading → error+retry →
     success, `estadoAgregacion` OK/PARCIAL/ERROR branching, `cuenta` null fallback); establishes
     the pattern for testing multi-state async UIs and asserts the Ley 21.719 disclaimer text
     renders (see §7).
   - **`App.jsx`** — the security-relevant session gate; establishes the pattern for mocking
     `sessionAPI.get()` (not cookies) and covers: checking-session spinner, authenticated →
     renders nav shell, unauthenticated → renders `<Login/>`, and the `/pago-resultado` bypass
     rendering `<PagoResultado/>` without ever calling `sessionAPI.get()`.
7. CI: add a blocking `Test` step to `frontend-ci.yml` (see §CI Integration) plus non-gating
   coverage reporting.
8. This design doc itself documents the conventions (§Conventions) that future tests (for
   `Dashboard`, `Inventario`, `Pedidos`, `Envios`, `PagoResultado`, and any revamped components)
   must follow — enforced by code review, not by a coverage gate, this cycle.

### Explicitly deferred (with reasoning)

- **Tests for `Dashboard.jsx`, `Inventario.jsx`, `Pedidos.jsx`, `Envios.jsx`, `PagoResultado.jsx`.**
  Writing exhaustive tests for all 7 pages in one cycle risks a huge, hard-to-review diff and
  delays getting *any* CI-enforced test net in place. The convention established by the three
  baseline components (§Conventions) is deliberately meant to be followed incrementally — the
  recommended practice is "when you touch a page during the revamp, add its tests in the same PR."
  This still delivers a genuinely usable baseline for the revamp's QA phase: the three chosen
  components are the highest-risk ones to regress silently (auth gate, personal-data page, entry
  form), and the pattern for the rest is fully specified.
- **MSW / network-level mocking.** Deferred until there's a concrated need (e.g. testing the
  401-redirect interceptor itself, or a future data-fetching library). Introducing it speculatively
  now would add setup cost with no test currently needing it.
- **Coverage threshold/gating in CI.** Reporting only. A hard percentage gate this cycle would
  either be trivially low (meaningless) or would block unrelated future PRs for touching the 4
  untested pages before their turn comes in the revamp. Revisit once baseline coverage is broader.
- **Testing the `api.js` 401-response interceptor in isolation.** None of the three baseline
  components exercise it. Worth a small follow-up once axios-mock-adapter or MSW is introduced,
  since testing it via `vi.mock('../services/api')` (which replaces the whole module, interceptor
  included) doesn't actually exercise the interceptor logic.
- **Visual regression / snapshot testing (e.g. Chromatic, Playwright screenshots).** A different
  testing concern (pixel-level UI), explicitly not needed to catch the logic regressions this task
  targets, and actively counterproductive to introduce right before a full visual revamp (it would
  be red on day one of the revamp by design).
- **End-to-end tests against the full docker-compose stack (Playwright/Cypress).** Out of scope —
  this task replaces the *component-level* gap ("no real component tests"), not the E2E gap. The
  docker-compose+curl approach QA used previously may still have standalone value for full-stack
  smoke tests, but that's a separate initiative.

## 6. CI integration

Add a **blocking** `Test` step to the existing `lint-and-build` job in
`.github/workflows/frontend-ci.yml`, positioned after `Lint` and before `Build` (fail fast — no
reason to spend build minutes if tests already fail):

```yaml
      - name: Lint
        run: npm run lint

      - name: Test
        run: npm run test:coverage

      - name: Build
        run: npm run build
```

- **Blocking: yes.** A failing test must fail the job, the same as lint/build failures do today.
  Since this job's steps run sequentially with default `shell: bash` semantics, any non-zero exit
  from `vitest run` fails the step and the job, which is the existing pattern for lint/build — no
  new CI mechanism needed.
- **Coverage: report now, don't gate.** Run via `npm run test:coverage` (`vitest run --coverage`)
  so a coverage summary appears in the job log/step summary every run, giving visibility as the
  revamp adds tests — but do not add a `coverage.thresholds` failure gate this cycle (see §5,
  deferred). No separate artifact upload for coverage is needed yet; add one later if the team
  wants historical coverage tracking (e.g. Codecov) — not requested here.
- **Branch protection note (open question, not something this doc can verify):** if `main` has a
  required-status-check rule keyed to the job name `lint-and-build`, adding a step inside that same
  job requires no branch-protection change (the job name is unchanged). If a separate `test` job is
  preferred instead for parallelism, someone with repo-admin access needs to add it to the required
  checks list — flagged in Open Questions.

## 7. Conventions

**File location/naming:** co-located `ComponentName.test.jsx` next to the component it tests
(e.g. `src/pages/Login.test.jsx`, `src/App.test.jsx`), **not** a separate `__tests__/` directory.
This is the idiomatic convention for Vite/Vitest + React Testing Library projects (keeps the test
next to what it verifies, easiest to find, easiest to keep in sync when a component moves/renames).
This does not need to mirror the backend's Maven-mandated `src/test/java` split-tree convention —
that split exists because Maven's build lifecycle requires it, not because it's a project-wide
rule; the frontend has no such constraint and should follow its own ecosystem's norm.

**What to test:** behavior, state transitions, and conditionally-rendered content, reached via
accessible queries — `getByRole`, `getByLabelText`, `getByText`, `findBy*` for async transitions.
Concretely: loading → success/error transitions; form validation and both submission outcomes
(success and failure, including the exact error text shown); which of several mutually-exclusive
UI branches renders for a given piece of state (e.g. `estadoAgregacion` OK vs PARCIAL vs ERROR in
`MisDatos.jsx`); disabled/enabled states of interactive elements during `loading`; presence of
compliance-relevant static text (e.g. the Ley 21.719 disclaimer in `MisDatos.jsx`) so it can't
silently disappear in a future edit.

**What NOT to test:** Tailwind class names or any other styling/pixel-level detail (e.g. do not
assert `expect(el).toHaveClass("bg-red-50")`) — this both couples tests to implementation details
and would make every baseline test fail on day one of the upcoming visual revamp, defeating the
purpose of building this net *for* that revamp. Do not snapshot full rendered HTML/markup for the
same reason. Do not assert on icon internals (`lucide-react` SVG paths) — assert on visible text or
`aria-label`s instead.

**Mocking pattern — axios/API calls** (module-level mock of `services/api.js`):

```jsx
// Login.test.jsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Login from "./Login";
import { authAPI } from "../services/api";

vi.mock("../services/api", () => ({
  authAPI: { login: vi.fn(), register: vi.fn(), logout: vi.fn() },
}));

beforeEach(() => vi.clearAllMocks());

it("shows the backend error message when login fails", async () => {
  authAPI.login.mockRejectedValueOnce({ response: { data: { message: "Credenciales inválidas" } } });
  render(<Login />);
  await userEvent.type(screen.getByLabelText(/email/i), "user@smartlogix.cl");
  await userEvent.type(screen.getByLabelText(/contraseña/i), "wrong-pass");
  await userEvent.click(screen.getByRole("button", { name: /ingresar/i }));
  expect(await screen.findByText("Credenciales inválidas")).toBeInTheDocument();
});
```

**Mocking pattern — cookie-based session check (`App.jsx`):** `App.jsx` never reads
`document.cookie` directly — it awaits `sessionAPI.get()` and reacts to resolve/reject. **Do not**
attempt to fake cookies in jsdom; mock `sessionAPI.get` instead, exactly like any other API call:

```jsx
// App.test.jsx
vi.mock("./services/api", () => ({
  authAPI: { logout: vi.fn() },
  sessionAPI: { get: vi.fn() },
}));
import { sessionAPI } from "./services/api";

it("renders Login when the session check rejects (no valid cookie)", async () => {
  sessionAPI.get.mockRejectedValueOnce(new Error("401"));
  render(<App />);
  expect(await screen.findByRole("heading", { name: /smartlogix/i })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: /ingresar/i })).toBeInTheDocument();
});
```

**`window.location` handling:** `Login.jsx` calls `window.location.reload()` on successful login,
and `App.jsx` reads `window.location.pathname` for the `/pago-resultado` bypass. jsdom's `location`
does not implement navigation (`reload()`/`href` assignment logs a "Not implemented" error/throws).
Stub it per-test with `vi.stubGlobal` (or `Object.defineProperty(window, "location", ...)`) and
restore in `afterEach` — do not let this leak between test files:

```jsx
beforeEach(() => vi.stubGlobal("location", { ...window.location, reload: vi.fn(), pathname: "/" }));
afterEach(() => vi.unstubAllGlobals());
```

## 8. Design pattern fit

No new design pattern is introduced, and none is needed — this is test tooling, not a feature.
Per the ground rule against speculative abstraction, the existing pattern catalogue (Repository,
Factory Method, Circuit Breaker, Observer, Strategy, Facade, Saga — see `README.md` §"Patrones de
diseño implementados") is entirely a backend/GoF classification; there is no frontend entry in that
table today, and this task doesn't add one. The one relevant convention this task *does* establish
— mocking at the `services/api.js` module boundary rather than the transport layer — mirrors the
same spirit as the backend's Repository pattern (test against the seam the codebase already
abstracts data access behind, not against the underlying implementation), but calling it a
"pattern" would be overstating a straightforward test-double choice.

## 9. Security requirements (OWASP Top 10 2021)

This is dev-tooling with no production runtime component, so most categories are N/A. Noted
concretely where relevant:

- **A01 Broken Access Control:** N/A — no new endpoint, no new access-controlled resource. The
  `App.jsx` baseline test *verifies* existing access-gating behavior (unauthenticated → Login) but
  does not change it.
- **A02 Cryptographic Failures:** N/A — no secrets, keys, or payment data touched. Test fixtures
  must not use real-looking credentials/PII (e.g. use obviously-fake emails like
  `user@smartlogix.cl` / placeholder passwords, never anything resembling a real customer record),
  consistent with `docs/COMPLIANCE_CL.md`'s minimization guidance, even though these are just test
  literals with no persistence.
- **A03 Injection:** N/A — no SQL, no dynamic query/shell construction anywhere in this change.
- **A04 Insecure Design (abuse cases):** N/A for this specific task in the sense that no new
  double-submit/race-condition-prone flow is introduced. Note for future test authors: if/when
  tests are added for `Pedidos.jsx`/`Envios.jsx`/the Saga-triggering flows, double-submit
  (double-click on a submit button while `loading` is true) is a real, testable abuse case worth
  covering given this codebase's Saga/webhook patterns — flagged for those future tests, not
  addressed by this baseline.
- **A05 Security Misconfiguration:** No new env vars, no new exposed ports, no actuator changes.
  One operationally-relevant point: because tests mock the API module boundary (§4), they cannot
  accidentally make real network calls to a live backend — this is itself a hardening of the CI
  environment (previous QA process required a running docker-compose stack; this one requires
  nothing but Node). New devDependencies should be installed via `npm install` (not manually edited
  into `package.json`) so `package-lock.json` stays consistent with what `npm ci` installs in CI,
  and it's worth a routine `npm audit` pass on the new devDependency tree before merge (not a
  blocking gate here, just hygiene for supply-chain integrity — see A08).
- **A07 Auth Failures:** N/A — no change to JWT/session/cookie handling. Tests *verify* the
  existing httpOnly-cookie-driven session check via `sessionAPI` mocking (§Conventions); they do
  not touch real cookies, tokens, or auth logic.
- **A08 Software/Data Integrity:** No webhook/HMAC/deserialization code is touched. The only
  integrity-relevant consideration is supply chain: five new devDependencies
  (`vitest`, `@vitest/coverage-v8`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`,
  `@testing-library/jest-dom`) are added, all mainstream/high-download packages from the existing
  npm registry the project already trusts (same registry `vite`/`react`/`axios` come from);
  `package-lock.json` must be committed alongside so `npm ci` in CI is reproducible.
- **A09 Logging/Monitoring:** N/A for runtime logging (no new logs are produced by this change).
  Test output itself must not print PII/secrets — since all baseline test fixtures use fake data
  (§A02), this is satisfied by construction; call this out in code review for future test authors
  per `docs/COMPLIANCE_CL.md`'s "never log plaintext PII/credentials" guidance, since Vitest's
  default reporter prints assertion failure values (including fixture data) to CI logs on failure.
- **A10 SSRF:** N/A — no outbound HTTP call is constructed from user input anywhere in this change
  (tests don't make real HTTP calls at all, by design — §4).

## 10. Chilean compliance touchpoints

No new personal data or payment data is created, stored, exposed, or deleted by this change — it
is test tooling only, so most of `docs/COMPLIANCE_CL.md` doesn't directly apply. One deliberate
touchpoint: the `MisDatos.jsx` baseline test asserts that the Ley 21.719 access-right disclaimer
paragraph ("Esta página corresponde al derecho de acceso (Ley 21.719)...") renders on the success
path. This isn't a new compliance obligation — the ARCO-Acceso feature already exists (see
`docs/designs/arco-acceso-personal-data.md`) — but it means the *only* automated regression
protection for that legally-relevant UI copy comes from this task, which is worth stating
explicitly rather than leaving implicit. No retention, breach-logging, or data-subject-request
handling is affected by this task.

## 11. Acceptance criteria

1. `npm test` (mapped to `vitest run`) exists in `frontend/smartlogix-app/package.json` and, run
   from a clean `npm ci`, executes in non-watch mode and exits 0 with all baseline tests passing.
2. `npm run lint` continues to pass with test files present (no `no-undef` errors on
   `describe`/`it`/`expect`/`vi` from the new ESLint test override).
3. Baseline suite includes at minimum `src/pages/Login.test.jsx`, `src/pages/MisDatos.test.jsx`,
   and `src/App.test.jsx`.
4. `Login.test.jsx` covers: rendering the login form; submitting with empty fields shows the
   client-side "Completa todos los campos" message without calling `authAPI.login`; successful
   login calls `authAPI.login` with `{email, password}`; a rejected login shows the backend error
   text extracted from `err.response.data`; toggling to "Crear cuenta" and submitting calls
   `authAPI.register` and shows the success message.
5. `MisDatos.test.jsx` covers: the loading spinner renders before data resolves; a rejected
   `usuariosAPI.getMisDatos()` call renders the generic error state with a working "Reintentar"
   button that re-triggers the fetch; `estadoAgregacion: "OK"` renders no degradation banner;
   `estadoAgregacion: "PARCIAL"` and `"ERROR"` each render their respective banner text; a null
   `cuenta` field renders `CuentaAusenteCard` instead of `CuentaCard`; the Ley 21.719 disclaimer
   text is present on the success render.
6. `App.test.jsx` covers: the "Cargando..." state renders before `sessionAPI.get()` settles; a
   resolved `sessionAPI.get()` renders the authenticated shell (sidebar nav with "Dashboard" link);
   a rejected `sessionAPI.get()` renders `Login`; when `window.location.pathname` is
   `/pago-resultado`, `PagoResultado` renders and `sessionAPI.get()` is **not** called (negative
   assertion — `expect(sessionAPI.get).not.toHaveBeenCalled()`).
7. **Negative/regression-catching check (per component), to be verified by the implementer/reviewer
   before merge:** deliberately invert one conditional in each baseline component and confirm its
   test fails — e.g. flip `estado === "OK"` to `!==` in `MisDatos.jsx`'s `EstadoBanner`, remove the
   `msg` banner render in `Login.jsx`, or invert `if (!authenticated)` in `App.jsx` — then run
   `npm test` and confirm a red run, then revert. This is a checked-and-reverted validation step,
   not a permanent test-of-a-test.
8. `.github/workflows/frontend-ci.yml` runs `npm run test:coverage` (or equivalent) as a step that
   fails the job (and therefore the pipeline) on a failing test, positioned before the `Build`
   step.
9. No test file reads/writes `localStorage` for auth tokens (there is none in this app — a test
   doing so would indicate a stale/incorrect assumption).
10. No test asserts on Tailwind utility class strings as its pass/fail condition (reviewer
    spot-check per §Conventions).
11. `vitest run --coverage` produces a coverage summary in CI logs without failing the build on any
    threshold.
12. Running `npm test` twice in a row (or with `--no-file-parallelism` toggled) produces identical
    pass/fail results — no order-dependent flakiness from shared mock state (verifies
    `vi.clearAllMocks()`/`afterEach` hygiene in the baseline tests).

## 12. Open questions

1. **CI job structure:** should the `Test` step live inside the existing single `lint-and-build`
   job (this doc's recommendation, for simplicity and fewer runner-minutes), or become its own
   parallel job? If `main`'s branch protection has a required-status-check rule keyed to a specific
   job name, that's a repo-admin-level setting this doc cannot verify or change — flag to whoever
   owns branch protection if a separate job is preferred later.
2. **`Dashboard.jsx`'s missing error state** was discovered while reading the code (a failed
   `dashboardAPI.get()` call is swallowed by `.catch(console.error)` with no user-facing error UI —
   the page stays on the loading spinner forever). This is a pre-existing UX/reliability gap, not
   something this test-infra task should fix incidentally. Recommend a separate follow-up ticket;
   flagging here so it isn't lost.
3. **happy-dom re-evaluation:** jsdom was chosen for compatibility (§4). If the revamp adds many
   more component tests and suite runtime becomes a real friction point, re-benchmarking happy-dom
   is a reasonable future optimization — not needed at today's scale (3 files).
4. **MSW timing:** this doc defers MSW in favor of `vi.mock`. If the revamp is expected to
   introduce a data-fetching library (react-query/SWR) or more complex request orchestration on the
   frontend, it may be worth introducing MSW *during* the revamp rather than after — the human
   should weigh in if that's already planned, since it would change the recommended mocking
   approach for tests written during the revamp itself.
5. **Coverage tool version pinning:** exact `vitest`/`@testing-library/react` versions should be
   whatever's current-stable and compatible with React 19 + Vite 8 at implementation time; this doc
   intentionally doesn't pin exact semver numbers so the implementer runs `npm install <pkg>` fresh
   rather than copying possibly-stale versions from this doc.
