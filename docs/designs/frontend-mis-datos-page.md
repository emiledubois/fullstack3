# Frontend "Mis Datos" page — consuming `GET /api/usuarios/me/datos`

## 1. Summary

`docs/designs/arco-acceso-personal-data.md` shipped the backend BFF endpoint `GET /api/usuarios/me/datos` (`api-gateway`, commit `ca9080b`), which satisfies Ley 21.719's derecho de acceso *technically* but not yet *practically*: there is no UI route a logged-in user can navigate to and actually see their own data. `COMPLIANCE_CL.md` §4.2 still lists "Acceso" as pending a usable path for the data subject, and the backend design's own open question 5 flags this gap explicitly. This design adds a single new frontend page, "Mis datos", reachable from the existing sidebar nav, that calls the already-built endpoint and renders the response as a plain-language summary (cuenta, historial de pedidos, historial de envíos) instead of raw JSON — with correct, non-misleading handling of the two edge cases the backend contract requires the UI to not get wrong: `estadoAgregacion !== "OK"` (degraded backend, not "no data") and `cuenta: null` (dangling deleted-account cookie). No backend change is needed or proposed.

## 2. Affected services

Frontend only (`frontend/smartlogix-app`). No backend/API/database changes.

Confirmed nothing is missing from the response contract for this UI: the existing `UsuarioDatosDTO` (`generadoEn`, `estadoAgregacion`, `cuenta` nullable `{email, role, cuentaCreadaEn}`, `pedidos[]`, `envios[]` — verified directly against `services/api-gateway/src/main/java/com/ecommerce/api/gateway/dto/{UsuarioDatosDTO,CuentaDTO,PedidoDatosDTO,EnvioDatosDTO}.java`) carries every field this page needs: account identity/role/creation date, per-pedido `status`/`tipoPedido`/`destino`/`total`/`creadoEn`, and per-envío `status`/`tipoEnvio`/`transportista`/`fechaEstimadaEntrega` linked back to `pedidoId`. Nothing new is requested from the backend.

**Affected files:**
- `frontend/smartlogix-app/src/services/api.js` — add a `usuariosAPI.getMisDatos()` export (one line, same shape as `dashboardAPI.get()`). No changes to the axios instance itself; it already uses `withCredentials: true` for the `sl_jwt` cookie, which is all this page needs.
- `frontend/smartlogix-app/src/pages/MisDatos.jsx` — **new**, the page component.
- `frontend/smartlogix-app/src/App.jsx` — add `"mis-datos"` to the `NAV` array and a matching render branch, following the exact pattern already used for `dashboard`/`inventario`/`pedidos`/`envios`.

**Coordination classification:** N/A (single service, frontend-only, additive). Not a Saga, not a Facade change — a plain new consumer of an existing Facade/BFF endpoint.

## 3. API contract

No new or changed backend endpoints. For reference, the page consumes exactly:

- `GET /api/usuarios/me/datos` (existing, `api-gateway` → `UsuarioDatosController`) — auth via `sl_jwt` httpOnly cookie (`withCredentials`), no parameters, no role restriction beyond "logged in." See `docs/designs/arco-acceso-personal-data.md` §3.1 for the full response contract; not reproduced here except where it drives a UI decision (§4 below).

`frontend/smartlogix-app/src/services/api.js` gets one new export, matching the existing per-module convention exactly:

```js
export const usuariosAPI = {
  getMisDatos: () => api.get("/usuarios/me/datos"),
};
```

No new axios interceptor logic is needed: the shared 401 interceptor already redirects to `/` on any non-auth/non-session 401, which is the correct behavior here too (expired cookie while viewing this page → same forced re-login as every other page).

## 4. Component structure

Single-file page component, `MisDatos.jsx`, mirroring `Dashboard.jsx`'s structure (top-level `useState`/`useEffect` fetch, no external state library, no new dependency — `lucide-react` is already a project dependency and is reused for icons).

```
<MisDatos>
  loading spinner                                    (mirrors Dashboard's loading block, unchanged copy: "Cargando...")
  header: "Mis datos personales" + one-line explainer + estadoAgregacion badge (reused pattern from Dashboard's "Servicios: OK/PARCIAL/ERROR" badge, relabeled)
  <PartialWarningBanner>                              shown only if estadoAgregacion !== "OK"
  <CuentaCard>                                        cuenta section — or <CuentaAusenteCard> if cuenta === null
  <PedidosSection>                                    list of pedidos, or empty-state message
  <EnviosSection>                                     list of envíos, or empty-state message
  <FooterNote>                                         "Generado el <fecha>" + short ARCO+ explainer line
```

No sub-component files are needed given the page's size and the codebase's existing convention of keeping one page = one file with small inline sub-components (`StatCard` inside `Dashboard.jsx` is the precedent) — a `StatusBadge`/`InfoRow` inline helper is fine, no new `components/` directory is required for this cycle.

### Translation/formatting layer (non-technical reader requirement)

The task explicitly requires this page to present data in plain Spanish, not raw enum/ISO values. Reuse the existing formatters (`formatFechaChile` from `api.js`, `formatCLP` from `api.js`) and add local (page-scoped, not exported — no other page needs these yet, avoiding speculative shared abstraction) label maps:

- `ROLE_LABELS = { ROLE_USER: "Usuario", ROLE_ADMIN: "Administrador" }` (fallback: show the raw role string if an unmapped value appears, never blank).
- `PEDIDO_STATUS_LABELS` — reuse the same six states already enumerated in `Dashboard.jsx`'s `statusColorPed` (`PENDIENTE, CONFIRMADO, APROBADO, EN_ENVIO, ENTREGADO, CANCELADO`) mapped to Spanish-friendly display text (e.g. `EN_ENVIO → "En camino"`, `APROBADO → "Aprobado"`) — most are already Spanish words, so this is mostly a pass-through with 1-2 friendlier relabels, not a translation project.
- `ENVIO_STATUS_LABELS` — reuse `Envios.jsx`'s `STATUS`/`STATUS_COLORS` set (`CREADO, ASIGNADO, EN_RUTA, ENTREGADO`), same treatment.
- Dates (`cuentaCreadaEn`, `creadoEn`, `fechaEstimadaEntrega`, `generadoEn`) always rendered via `formatFechaChile`, never `new Date(x).toISOString()` or a raw string — consistent with how `Dashboard.jsx` already formats `generadoEn`, extended here to every date field instead of `Dashboard.jsx`'s partial use (`.substring(0,10)`).
- Money (`pedidos[].total`) rendered via `formatCLP`, matching `Pedidos.jsx`'s convention (verified this formatter already exists in `api.js` for exactly this purpose).

## 5. State handling (loading / success / partial / error)

Four distinct UI states, driven by two independent signals from the response (`estadoAgregacion` and `cuenta === null`), which are **orthogonal and must both be checked** — they are not the same condition:

| Local state | Trigger | What renders |
|---|---|---|
| `loading` | request in flight | Same spinner block as `Dashboard.jsx` (`animate-spin` div + "Cargando..." text) — no new visual language invented. |
| `error` (network/5xx/unexpected) | the `GET` call itself rejects for a reason other than a normal `200` with a degraded `estadoAgregacion` (i.e., the gateway itself is unreachable, or a non-401 5xx) | A full-page error card: "No pudimos cargar tus datos en este momento. Intenta nuevamente en unos minutos." + a "Reintentar" button that re-runs the fetch. (401 is not handled here — the shared axios interceptor already force-redirects to `/` before this branch is reached.) |
| `success, estadoAgregacion === "OK"` | normal case | Full content, no banner. |
| `success, estadoAgregacion !== "OK"` | `"PARCIAL"` or `"ERROR"` | Content renders **using whatever data did come back**, but prefixed with a non-dismissible amber/red banner (see below) that explicitly says the list may be incomplete — this is the single most important UI decision in this design (see below). |

### Handling `estadoAgregacion !== "OK"` (the core design decision)

**Decision: never let an empty `pedidos`/`envios` array under `PARCIAL`/`ERROR` render the same "no tienes pedidos" empty-state copy used for a genuinely-empty new user.** The backend design (`arco-acceso-personal-data.md` §8 criterion 7) explicitly guarantees `estadoAgregacion` reflects a real downstream outage rather than `"OK"` with empty arrays, precisely so the frontend does not have to guess — and precisely so the frontend does not throw that guarantee away by rendering an ambiguous empty list.

Concretely:
- A dedicated banner component renders above the content whenever `estadoAgregacion !== "OK"`:
  - `PARCIAL`: amber banner, icon `AlertTriangle` (lucide-react, already imported project-wide), text: **"Algunos de tus datos no se pudieron cargar en este momento porque uno de nuestros sistemas no respondió. La información que ves aquí puede estar incompleta — no significa que no tengas pedidos o envíos registrados. Intenta recargar la página en unos minutos."**
  - `ERROR`: red banner, same icon, text: **"No pudimos obtener tus datos en este momento porque nuestros sistemas no están respondiendo. Esto es un problema temporal, no una confirmación de que no tienes información registrada. Intenta nuevamente más tarde."**
- Each section (`PedidosSection`, `EnviosSection`) that is empty **and** `estadoAgregacion !== "OK"` shows section-local copy distinct from the true-empty case: "No pudimos verificar tus pedidos en este momento (ver aviso arriba)" instead of "Aún no tienes pedidos." This double-reinforcement (top banner + per-section caveat) is deliberate: a user who scrolls past the banner and only looks at the pedidos card must not misread it.
- The true-empty case (`estadoAgregacion === "OK"` and `pedidos: []`) keeps a calm, unambiguous "Aún no tienes pedidos registrados" — matching `Dashboard.jsx`'s existing "Sin pedidos aún" tone, no scare language for a legitimately new account.
- The badge shown in the page header reuses `Dashboard.jsx`'s exact three-color convention (`OK` green / `PARCIAL` yellow / `ERROR` red) for visual consistency across the app, but with the label "Estado de tus datos: OK/PARCIAL/ERROR" rather than "Servicios: ..." — the audience for this page is the data owner, not an operator, so it should read as "how complete is what you're seeing," not as an infra status light.

### Handling `cuenta: null`

**Decision:** this is the dangling-deleted-account edge case flagged in the backend design (§3.1: "cuenta is null... if the email from a still-valid JWT no longer resolves to an account"). The page must not render a broken-looking "Cuenta: undefined" card, and must not imply the user has literally zero account data (they might still have `pedidos`/`envíos` rows in the response, associated with an email that used to have an account). Render a distinct, calm, actionable message in place of the `CuentaCard`:

> "No pudimos confirmar los datos de tu cuenta en este momento. Esto puede ocurrir si tu cuenta fue eliminada recientemente pero tu sesión sigue activa. Si esto no es lo esperado, cierra sesión e inicia sesión nuevamente, o contáctanos."

This message is shown **regardless of `estadoAgregacion`** — `cuenta: null` can legitimately coexist with `estadoAgregacion: "OK"` (the other two services answered fine; only the account genuinely doesn't exist anymore) as well as with `"PARCIAL"`/`"ERROR"` (auth-service itself was unreachable). The copy above is deliberately worded to cover both without asserting which one happened, since the frontend cannot and should not guess which case it is from `estadoAgregacion` alone — `cuenta === null` under `"OK"` most likely means "account genuinely gone," while `cuenta === null` under `"PARCIAL"`/`"ERROR"` most likely means "auth-service was down," but the DTO gives no field to distinguish them, and inventing a distinction the backend doesn't expose would be guessing, not designing (flagged again in §9). `pedidos`/`envíos` sections still render normally below this card if they have data — the account-identity failure doesn't invalidate the rest of the disclosure.

## 6. Navigation placement

New nav item added to `App.jsx`'s existing `NAV` array, following the identical pattern used for `dashboard`/`inventario`/`pedidos`/`envios` (no new routing library, no React Router — the app already uses a plain `useState("dashboard")` + conditional render pattern):

```js
const NAV = [
  { key:"dashboard",  label:"Dashboard",  icon:"" },
  { key:"inventario", label:"Inventario", icon:"" },
  { key:"pedidos",    label:"Pedidos",    icon:"" },
  { key:"envios",     label:"Envíos",     icon:"" },
  { key:"mis-datos",  label:"Mis datos",  icon:"" },
];
```

and in the `<main>` render block:

```jsx
{page === "mis-datos" && <MisDatos />}
```

**Placement rationale:** last in the sidebar, after the operational pages (Dashboard/Inventario/Pedidos/Envíos) and before the logout button, since it is a personal/account-scoped page rather than an operational/business one — this mirrors the general convention (seen in many dashboards) of separating "run the business" nav items from "about me" ones, without inventing a new visual section/divider the current sidebar markup doesn't already support (no speculative sidebar restructuring — keeping the existing flat `<nav>` list as-is).

## 7. Data model changes

None. Frontend-only feature; no service owns new persisted state.

## 8. Design pattern fit

This is a plain new consumer of the existing **BFF/Facade aggregation endpoint** (`/api/dashboard`'s pattern, extended by `/api/usuarios/me/datos` per the backend design) — no new pattern on the frontend side either. The frontend already has an established "page fetches one aggregated JSON blob from `api-gateway`, derives a three-state status badge from it" convention in `Dashboard.jsx`; `MisDatos.jsx` reuses that convention verbatim (same `estadoServicios`-style badge shape, same loading-spinner markup, same empty-state tone) rather than introducing a different one. No new GoF pattern, no new state-management library, no new routing library — consistent with this codebase's existing "no speculative abstraction" posture.

## 9. Security requirements (OWASP Top 10 2021)

- **A01 Broken Access Control:** N/A for new access-control surface — this page calls an endpoint that already derives identity exclusively from the server-verified `sl_jwt` cookie and accepts no client-supplied identifier (per the backend design §3.1/§6). The frontend adds no path/query parameter of its own; `usuariosAPI.getMisDatos()` takes no arguments. There is nothing for the frontend to get wrong here in terms of whose data is requested.
- **A02 Cryptographic Failures:** No new secrets. The response contains no password hash (already excluded server-side, and enforced by the backend's own DTO-level exclusion — this page does not need to redact anything client-side because the payload it receives never contains sensitive credential material).
- **A03 Injection:** N/A — the page does no server-side rendering, no `dangerouslySetInnerHTML`, no dynamic query construction. All rendered values are inserted via normal JSX text interpolation (`{value}`), which React escapes by default.
- **A04 Insecure Design:** This is a pure read with a manual "Reintentar" retry button on error — retrying is idempotent (same `GET`, no side effects), so no double-submit/race concern is introduced. Worth calling out explicitly for review: the "Reintentar" button must not spam-retry automatically (no auto-polling loop) — a single manual click per attempt, consistent with every other page's fetch-once-on-mount pattern (`Dashboard.jsx`, `Envios.jsx` both fetch once via `useEffect`, not on an interval).
- **A05 Security Misconfiguration:** No new env vars, no new exposed ports, no new actuator endpoints — frontend-only static-content addition, built into the existing Vite/Nginx bundle the same way every other page already is.
- **A07 Auth Failures:** No change to JWT/cookie handling. The page relies entirely on the existing `sl_jwt` cookie mechanism and the existing shared axios 401 interceptor (`api.js`) to force re-login on an expired/invalid session — no new session logic is written for this page.
- **A08 Software/Data Integrity:** N/A — no webhook, no external deserialization; the only external input is the already-authenticated JSON response from `api-gateway` over the existing `withCredentials` axios instance.
- **A09 Logging/Monitoring:** **Nothing from this page's response payload may be sent to a browser console log, an error-tracking SDK breadcrumb, or any client-side analytics event** — no `console.log(res.data)` anywhere in `MisDatos.jsx` (the `.catch(console.error)` pattern used elsewhere in this codebase, e.g. `Dashboard.jsx`'s `.catch(console.error)`, is acceptable for a raw Axios *error* object on a failed fetch, since it does not contain personal data, but must not be used to log a *successful* response containing `cuenta`/`pedidos`/`envios`). This is a concrete implementation instruction: the developer must not "helpfully" add a debug log of the fetched personal data during implementation. Browser history is not a concern here — this is a `GET` with no query parameters and no personal data ever appears in the URL (unlike, say, a search page that might put an email in a query string).
- **A10 SSRF:** N/A — the page makes exactly one outbound call, to the fixed, hardcoded `/api/usuarios/me/datos` path via the shared `api` axios instance (relative URL, proxied by Vite/Nginx to the gateway) — no user-controlled URL construction of any kind.

## 10. Chilean compliance touchpoints

Cross-reference: `docs/COMPLIANCE_CL.md` §4.2, "Acceso" row.

- This page is the piece that closes the remaining practical gap the backend design's own open question 5 flagged: the backend PR made the derecho de acceso *technically* satisfiable (a self-service, authenticated endpoint exists), but `COMPLIANCE_CL.md` §3's operative requirement is that a data subject can "obtener [sus datos] sin necesitar una solicitud manual" — an API a logged-in user cannot discover or trigger from the UI does not meet that bar in practice, only in principle. Once this page ships, the "Acceso" row in §4.2 can move from `❌ pendiente` (or its interim state after the backend PR) to reflecting that the right is both implemented *and* usable end-to-end by a real, non-technical account holder — per §5 of that document's own instruction to the reviewer to update the row citing the merging commit/PR.
- **Retention/minimization (§4.4):** this page introduces no new persistence anywhere — it is a read-only view. It must not itself become a new copy of personal data at rest: no client-side caching to `localStorage`/`sessionStorage`/IndexedDB of the fetched payload across page loads (fetch fresh on every mount, matching every other page's pattern; the browser's in-memory React state for the current session is not a persistence concern).
- **Logging/audit (§4.4, §9 A09 above):** the compliance-relevant audit event ("user X requested their data at time T") is already emitted server-side by `UsuarioDatosController` per the backend design — this page does not need to (and must not) duplicate that audit trail client-side, since a client-side "I displayed this" log would be both redundant and a new place personal data could leak into logs (browser console, or any client error-reporting integration if one is added later).
- **No new categories of personal data are created, stored, exposed, or deleted by this page** beyond what the backend endpoint already discloses to the same authenticated owner — this is presentation of an existing, already-scoped disclosure, not a new data flow.
- Rectificación/Cancelación/Oposición/Portabilidad remain out of scope and unaffected by this page, consistent with the backend design's own scoping (§7 of `arco-acceso-personal-data.md`).

## 11. Acceptance criteria

1. A logged-in user with ≥1 pedido and ≥1 envío navigates to "Mis datos" (new sidebar item, visible in the same position for every authenticated role): the page shows their email, role (translated, e.g. "Usuario" not "ROLE_USER"), account-creation date formatted `dd-mm-yyyy hh:mm` via `formatFechaChile` (not a raw ISO string), and lists exactly their pedidos and envíos with formatted currency (`formatCLP`) and formatted dates — no raw ISO timestamp string is visible anywhere on the page.
2. A logged-in user with zero pedidos/envíos and `estadoAgregacion: "OK"` sees calm, explicit empty-state copy ("Aún no tienes pedidos registrados" / "Aún no tienes envíos registrados") — no error styling, no warning banner, no red/amber color anywhere on the page.
3. Simulate the backend returning `estadoAgregacion: "PARCIAL"` with `pedidos: []`, `envios: []` (per the backend's own documented degraded-mode behavior when `ms-pedidos` is down): the page shows the amber banner text verbatim as specified in §5, and the pedidos/envíos sections show the "no pudimos verificar" caveat copy — **not** "Aún no tienes pedidos registrados." This is the single most important assertion in this acceptance list: a screenshot/DOM diff test must confirm the true-empty and degraded-empty states render visibly different copy.
4. Simulate `estadoAgregacion: "ERROR"`: the red banner renders with its distinct copy (not the amber `PARCIAL` copy) and any partial data present is still displayed underneath it, not hidden.
5. Simulate `cuenta: null` (any `estadoAgregacion` value): the account section renders the dangling-account explanatory message from §5, not a blank/undefined-looking card, and does not crash the rest of the page — pedidos/envíos sections (if populated) still render normally below it.
6. Simulate the underlying `GET` request rejecting with a network error or 5xx (not a 401): the page shows the generic error card with a "Reintentar" button; clicking it re-issues exactly one new `GET /api/usuarios/me/datos` call.
7. **Negative/abuse case — unauthenticated access:** navigating directly to the app with no valid `sl_jwt` cookie never reaches `MisDatos.jsx` at all — `App.jsx`'s existing `checkingSession`/`authenticated` gate (via `GET /api/session`) renders `<Login />` before any nav item, including "Mis datos," is shown. Verify: with cookies cleared, the "Mis datos" nav button is not present in the DOM (it lives behind the same `authenticated` gate as every other nav item).
8. **Negative/abuse case — expired cookie while already on the page:** if the `sl_jwt` cookie expires between initial login and clicking into "Mis datos" (or expires mid-session), the resulting `401` from `GET /api/usuarios/me/datos` triggers the existing shared axios interceptor redirect to `/`, exactly as it already does for `/api/pedidos`, `/api/envios`, `/api/dashboard` — no page-specific 401 handling is added or needed, and none should silently swallow the 401 instead of redirecting.
9. **Negative/abuse case — malformed/unexpected response shape:** if `pedidos` or `envios` is `undefined`/missing entirely (defensive case, should not happen per contract but must not crash the UI), the page renders the relevant section's empty state rather than throwing (guard with `data?.pedidos ?? []` / `data?.envios ?? []`, matching `Dashboard.jsx`'s existing `data?.metricas ?? {}` defensive-access convention).
10. No `console.log` (or any client-side logging/analytics call) anywhere in `MisDatos.jsx` is passed the fetched response body (`res.data`) or any of its nested fields on a successful (200) response — code-review-checkable, not just runtime-testable.
11. Visual/regression check: adding the "Mis datos" nav item does not change the position, styling, or behavior of the existing four nav items or the logout button; `Dashboard`/`Inventario`/`Pedidos`/`Envíos` pages remain reachable and unchanged.
12. Role label fallback: if `cuenta.role` is a value not present in `ROLE_LABELS` (e.g. a future role added later), the raw role string is still displayed (never blank, never "undefined").

## 12. Open questions

1. **`cuenta === null` disambiguation copy (§5):** the DTO gives no field to distinguish "account genuinely deleted" from "auth-service was unreachable when queried." The message drafted in §5 is deliberately written to be true under either interpretation, but if product/legal wants a more specific message for the genuine-deletion case, that would require a backend contract change (e.g., a distinct `cuentaEstado: "NO_ENCONTRADA" | "ERROR_CONSULTA"` field) that is out of scope for this frontend-only design — flagging so the orchestrator can decide whether that's worth a backend follow-up before this ships, or whether the ambiguous-but-safe copy here is acceptable for v1.
2. **`ms-pagos` data is still absent from the underlying endpoint** (flagged as a known gap in the backend design's own §7/§9) — this page necessarily also omits payment/transaction history from "Mis datos," since it can only render what the endpoint returns. Not a frontend defect, but worth surfacing again here so nobody mistakes this page's shipping for "full ARCO+ access is now complete."
3. **Retry/backoff on `PARCIAL`/`ERROR`:** the design gives the user a manual way to reload the whole page (browser refresh) but no dedicated "reintentar" affordance inside the amber/red banner itself (only the full-page error state in §5 has one). Worth a quick product call on whether the degraded-state banner should also carry its own inline retry button rather than relying on a full page navigation — flagged as a minor UX nice-to-have, not a correctness gap, so I did not spec it as required in §11's acceptance criteria.
4. **Icon/visual language for the account-identity failure card (§5's `cuenta: null` case):** no existing page in this codebase has a precedent for this kind of "your account may not exist anymore" message — I've specified copy and tone but not a specific icon/color; suggest reusing the same `AlertTriangle`/amber treatment as the `PARCIAL` banner for visual consistency unless design/product wants something distinct, since this is arguably a more serious message than a transient outage.
