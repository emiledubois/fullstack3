# Frontend Visual Revamp — Approved Design System

## Revision 1 — response to QA iteration-1 finding (font loading blocked by CSP)

**What changed and why:** QA's iteration-1 review (see the "## QA feedback (iteration 1)" section appended at the bottom of this doc) found that the original §2.2/§3 design loaded Sora and Public Sans via `<link>` tags pointed at `fonts.googleapis.com`/`fonts.gstatic.com`. The developer implemented that exactly as specified, but `frontend/smartlogix-app/nginx.conf` already ships (from an earlier security-hardening cycle this session) a Content-Security-Policy with no allowance for either host — confirmed by reading the file directly:

```
Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
```

There is no `font-src` directive at all (so fonts fall back to `default-src 'self'`), and `style-src` has no `fonts.googleapis.com` allowance either. Under the real nginx-served build, the Google Fonts stylesheet request is blocked, and every Sora/Public Sans declaration silently falls back to `system-ui`. This was invisible under `npm run dev` (no CSP is served there), which is why QA only caught it testing the actual dockerized build.

**Decision: self-host the fonts via npm (`@fontsource/sora` + `@fontsource/public-sans`) — option (b) of QA's two proposed paths — rather than relaxing the CSP (option (a)).**

Rationale:
- **Zero policy change.** Self-hosted font files are served from the app's own origin as build assets and satisfy the existing `default-src 'self'` fallback (and `style-src 'self'`) with no edit to `nginx.conf` at all. The CSP hardening done earlier this session stays exactly as strict as it was — this revision doesn't reopen a control that was deliberately tightened just to make two font families load.
- **Smaller trust surface, not just a smaller diff.** Relaxing `style-src`/adding `font-src` for `fonts.googleapis.com`/`fonts.gstatic.com` would be a genuine, permanent loosening of a security control, for the sole benefit of two font families that have no functional dependency on Google's CDN — `@fontsource` ships the identical static weights (Sora 600/700/800, Public Sans 400/500/600) as self-owned files. Once self-hosting is on the table there's no product reason to prefer widening the CSP instead.
- **Consistent with the project's own data-minimization stance.** `docs/COMPLIANCE_CL.md` (§ "Deber de seguridad") lists minimization as a required technical/organizational measure, and its compliance table already credits SmartLogix for avoiding unnecessary PII exposure to third parties (Flow token masking, no raw PAN handling by SmartLogix). Loading fonts from Google on every page view sends every visitor's IP address — including authenticated users on internal ops pages like Inventario/Pedidos — to Google on every load, for a purely cosmetic asset. That's a small but real, entirely avoidable third-party data flow with no compensating product benefit; self-hosting removes it outright. I checked both `docs/COMPLIANCE_CL.md` and `docs/ISO27001_MAPPING.md` directly — neither mandates this specifically (no existing text about font/CDN loading), but the minimization principle they do state points the same direction, so self-hosting is the choice consistent with the project's existing compliance posture rather than one that requires new justification.
- **No new runtime dependency or ops risk.** `@fontsource` packages are static font files bundled at build time by Vite like any other static asset (hashed, cached, served from the app's own `/assets/` output) — this introduces a build-time npm dependency only, not a new network call, external service, or CI-time external fetch.

This supersedes §2.2 and the relevant rows of §3 below. §§4-9 and the acceptance criteria are unaffected — the visual output (which glyphs render, at which weights, on which elements) is identical; only the delivery mechanism changes — and are left as originally written.

## 1. Summary

The user approved seven static HTML mockups (Dashboard, Login, Inventario, Pedidos, Envíos, MisDatos, PagoResultado) defining a new visual language for `frontend/smartlogix-app`: a Sora/Public Sans type pairing, an indigo brand palette (`#4f46e5`/`#4338ca`), a light neutral app background (`#f6f7f9`), 14px-radius white cards, pill-shaped status badges, and a consistent 248px sidebar with 5 nav items + logout. This design translates those literal mockup values into reusable Tailwind config tokens and applies them to the existing React components — **markup structure and `className` only**. No routing, state, data-fetching, or business-logic line changes. The mockups are the literal source of truth for every hex/px value below; nothing is rounded or reinterpreted.

## 2. Design tokens (`tailwind.config.js` → `theme.extend`)

All values below are copied verbatim from the `.dc.html` mockups' inline `style` attributes (cross-checked across all 7 files — every repeated value was identical across files, confirming these are the actual system tokens, not one-off drafts).

### 2.1 Colors

```js
colors: {
  brand: {
    50:  '#eef0ff', // active nav bg, badge/icon-chip bg
    100: '#e2e2fb', // Pedidos expanded-row detail card border
    400: '#818cf8', // Login logo gradient, light stop
    500: '#6366f1', // Sidebar logo gradient, light stop
    600: '#4f46e5', // primary buttons, links (a { color })
    700: '#4338ca', // link/nav hover, active nav text, gradient dark stop
    900: '#1e1b4b', // Login page bg gradient, dark stop
  },
  ink: {
    900: '#14161f', // headings (h1, "SmartLogix" wordmark, big numbers)
    800: '#1e2230', // base app text color
    700: '#25283a', // table/row primary text (names, amounts)
    600: '#4b4e5c', // Login field labels
    500: '#5b5e6d', // sidebar inactive nav text
    400: '#6b6e7d', // secondary/meta text — THE ACCESSIBILITY-CORRECTED VALUE.
                     // Do not substitute a lighter gray; this exact value already
                     // passed the contrast check that an earlier draft failed.
    300: '#9497a6', // placeholders, muted meta (dates, chevrons)
    200: '#c3c5cf', // unfilled/disabled-looking mock field text, inactive chevron
  },
  line: {
    100: '#f5f6f9', // table row divider
    150: '#f1f2f6', // card section divider (header/footer borders inside a card)
    200: '#ecedf2', // card border, table header border
    300: '#eef0f4', // sidebar header/footer border
    400: '#e7e9ee', // sidebar outer right border
    500: '#dcdee4', // form input border
  },
  app: { bg: '#f6f7f9' }, // page background (body / main content area)
  success: { bg: '#eafbf1', text: '#15803d', dot: '#22c55e' },
  warning: { bg: '#fff8e6', text: '#b7791f' },
  info:    { bg: '#eef3ff', text: '#2563eb' },
  accent:  { bg: '#f3ecff', text: '#7c3aed' }, // Envíos brand accent + EN_RUTA
  danger:  { bg: '#fdecec', text: '#c0392b', border: '#f4d9d9',
             strong: '#b3352f', strongText: '#a83f3a' },
  chip:    { bg: '#eef0f4', text: '#5b5e6d' }, // neutral tipoPedido/tipoEnvio chips
}
```

Usage: `bg-brand-600`, `text-ink-400`, `border-line-200`, `bg-success-bg text-success-text`, etc.

### 2.2 Fonts

```js
fontFamily: {
  sans:    ['"Public Sans"', 'system-ui', 'sans-serif'], // overrides Tailwind's default sans stack — body copy
  heading: ['Sora', 'system-ui', 'sans-serif'],           // page titles, section titles, h1/h2, big stat numbers
}
```

**Self-hosted via npm, not Google Fonts** (see Revision 1 above — the original Google Fonts `<link>` approach is blocked by the CSP already shipped in `nginx.conf`, and stays blocked; the fix is to stop depending on an external host rather than to widen the policy). Add as `dependencies`:

```
npm install @fontsource/sora @fontsource/public-sans
```

`src/index.css` gets the weight-specific imports **before** the `@tailwind` directives (weights taken from the union of all 7 mockups — Sora needs 600/700/800, Public Sans needs 400/500/600):

```css
@import '@fontsource/sora/600.css';
@import '@fontsource/sora/700.css';
@import '@fontsource/sora/800.css';
@import '@fontsource/public-sans/400.css';
@import '@fontsource/public-sans/500.css';
@import '@fontsource/public-sans/600.css';

@tailwind base;
@tailwind components;
@tailwind utilities;
```

Vite bundles these as hashed static assets served from the app's own origin (e.g. `/assets/*.woff2`), which satisfies the existing CSP's `default-src 'self'` fallback — there's no `font-src` directive to widen, and no `style-src` allowance is needed since no external stylesheet request is made anymore. **No change to `nginx.conf`/the CSP is required or made by this design.**

`index.html`'s `<head>` **loses** the `<link rel="preconnect" href="https://fonts.googleapis.com">`, `<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>`, and `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?...">` tags that were added in iteration 1 — there's no external host to preconnect to or stylesheet to fetch anymore.

`index.css` keeps the rule already added so Public Sans applies app-wide without adding `font-sans` to every element (Tailwind preflight doesn't set a body font by default today):

```css
body { @apply font-sans text-ink-800 bg-app-bg; }
```

`font-heading` is applied explicitly per-element (h1 page titles, h2 modal/section titles, the "SmartLogix" wordmark, StatCard big numbers, PagoResultado `h1`) — matching the mockups' `.heading` class, which is opt-in, not global.

### 2.3 Border radius

Named tokens (not overriding Tailwind's default `rounded-lg`/`rounded-xl` scale, to avoid silently changing radius semantics anywhere these default utilities might still be used):

```js
borderRadius: {
  chip:   '5px',   // tipoPedido/tipoEnvio neutral chips
  field:  '8px',   // compact form inputs (Inventario "Agregar producto" grid)
  input:  '10px',  // standard form inputs, search bar, buttons where mockup uses 10px
  nav:    '9px',   // sidebar nav items, logo mark, primary action buttons (Agregar/Crear)
  card:   '14px',  // all white content cards across every page
  'login-card':  '18px', // Login form card
  'result-card': '20px', // PagoResultado card
}
```

Pill badges use Tailwind's existing `rounded-full` (999px in mockups) — no new token needed.

### 2.4 Spacing / layout constants (not new tokens, just documented literal values from the mockups for the developer to hard-code where Tailwind's default spacing scale already lands on them)

- Sidebar width: `248px` → `w-[248px]` (no existing Tailwind step matches 248px exactly; use an arbitrary-value class rather than approximating with `w-60`/`w-64`).
- Sidebar logo mark: `32px × 32px`, `rounded-nav`, `bg-gradient-to-br from-brand-500 to-brand-700`.
- Main content padding: `32px 36px` → `p-8 px-9` (32px = `p-8`, 36px has no exact step; use `px-9` = 36px ✓ actually matches exactly, keep it).
- Card padding: `18-20px` vertical, `20-22px` horizontal depending on card — use `p-5` (20px) as the default card padding; the 18px/22px variants in the mockup are sub-pixel-different enough to not need their own token, `p-5` is the closest single value and keeps things consistent.

## 3. Affected files

| File | Change |
|---|---|
| `frontend/smartlogix-app/tailwind.config.js` | Add `theme.extend.colors/fontFamily/borderRadius` per §2. |
| `frontend/smartlogix-app/package.json` | **New dependencies (Revision 1).** Add `@fontsource/sora` and `@fontsource/public-sans` — self-hosted font files, see §2.2. |
| `frontend/smartlogix-app/index.html` | **Revised (Revision 1).** Remove the Google Fonts `<link rel="preconnect">`/`<link rel="stylesheet">` tags added in iteration 1 — fonts are now self-hosted, no external request is made. |
| `frontend/smartlogix-app/src/index.css` | **Revised (Revision 1).** Add `@import` statements for the `@fontsource/sora` (600/700/800) and `@fontsource/public-sans` (400/500/600) weight CSS files before the `@tailwind` directives, per §2.2; keep existing `body { @apply font-sans text-ink-800 bg-app-bg; }` rule and existing reset/scrollbar rules (scrollbar thumb colors may be updated to `ink-300`/`ink-200` to match the new palette, optional/cosmetic). |
| `frontend/smartlogix-app/src/components/Sidebar.jsx` | **New.** Extracted from `App.jsx` (see §4). |
| `frontend/smartlogix-app/src/components/StatusBadge.jsx` | **New.** Shared pill badge (see §4). |
| `frontend/smartlogix-app/src/App.jsx` | Replace inline `<aside>` block with `<Sidebar />`; restyle the "Cargando..." screen and outer shell wrapper with new tokens. No changes to `useState`/`useEffect`/auth logic. |
| `frontend/smartlogix-app/src/pages/Login.jsx` | Restyle only: gradient background, card radius/shadow, label/input colors, button colors, font classes. No changes to `handleSubmit`, state, or validation logic. |
| `frontend/smartlogix-app/src/pages/Dashboard.jsx` | Restyle `StatCard` (kept page-local, see §4) with tone-based coloring incl. the danger-card variant; restyle header pill, "Últimos pedidos"/"Alertas de stock" cards; replace ad-hoc status color function with `<StatusBadge>`. No changes to `dashboardAPI` calls, `estadoAgregacion`/loading logic. |
| `frontend/smartlogix-app/src/pages/Inventario.jsx` | Restyle form grid, search bar, table, and low-stock badge via `<StatusBadge>`. No changes to `search`/`filtrados`/`handleSubmit` logic. |
| `frontend/smartlogix-app/src/pages/Pedidos.jsx` | Restyle cart table, filter pills, main table, expandable detail row via `<StatusBadge>`. No changes to `lineas`/cart/expand/filter logic or `localStorage` behavior. |
| `frontend/smartlogix-app/src/pages/Envios.jsx` | Restyle form and kanban columns/cards via `<StatusBadge>` and new accent/tone colors. No changes to `handleAdvance`/`nextStatus` logic. |
| `frontend/smartlogix-app/src/pages/MisDatos.jsx` | Restyle `StatusBadge` (local component, `estado="OK"|"PARCIAL"|"ERROR"` pill), banners, account/pedidos/envíos cards via shared `<StatusBadge>` where applicable. No changes to `estadoAgregacion`/`cuenta`-null/retry logic; **all text strings referenced by `MisDatos.test.jsx` are preserved verbatim** (see §7). |
| `frontend/smartlogix-app/src/pages/PagoResultado.jsx` | Restyle the `PAGADO` state to match the mockup's green gradient/card exactly (`from-[#064e3b] to-[#059669]`, icon bg `#d1fae5`/text `#059669`, badge bg `#d1fae5`/text `#065f46`). The other 4 states (`RECHAZADO`, `ANULADO`, `PENDIENTE`, `error`, `loading`) have **no corresponding mockup** — keep their current Tailwind gradients/colors as-is (see §6 open question) rather than inventing new tokens for states the design canvas never reviewed. |

## 4. Shared component extraction plan

Judgment applied per the codebase's existing "don't over-abstract" convention (e.g. no generic `Card` wrapper exists anywhere despite every page using bordered white boxes) — only extract where there's genuine multi-file duplication or a clear readability win, not because "5 pages share a look."

**Extract:**

1. **`components/Sidebar.jsx`** — Justification: it's a real architectural extraction, not new duplication-avoidance, because the app already has exactly **one** sidebar instance (rendered once in `App.jsx`, wrapping the page switch — it is *not* duplicated per-page today the way the mockups render it 5 times as standalone files). Moving it out of `App.jsx` is about separating shell/layout markup from the auth-check/session/page-routing logic that `App.jsx` also owns, and gives one place to implement the active-item highlighting contract (`bg-brand-50 text-brand-700 font-semibold` when active, `text-ink-500 font-medium` otherwise) correctly once. Props: `{ active: string, onNavigate: (key) => void, onLogout: () => void }`. Icons: use the already-installed `lucide-react` (no new dependency) — `Home` (Dashboard), `Package` (Inventario), `ShoppingCart` (Pedidos), `Truck` (Envíos), `User` (Mis datos), `LogOut` (Cerrar sesión) — closest matches to the mockup's custom SVG glyphs.
2. **`components/StatusBadge.jsx`** — Justification: the *exact same* pill pattern (`text-xs font-semibold px-2.5 py-1 rounded-full` + a bg/text color pair) is independently reimplemented today in Dashboard (`statusColorPed`), Pedidos (`STATUS_COLORS`, used in 2 places — main row and expanded detail), Envios (`STATUS_COLORS`), Inventario (inline bajo-stock badge), MisDatos (`StatusBadge` local component + `PEDIDO_STATUS_COLORS` + `ENVIO_STATUS_COLORS`). That's 5 files reimplementing one visual contract — genuine duplication, worth collapsing. API: `<StatusBadge tone="success|warning|danger|info|accent|chip">{label}</StatusBadge>`. Each page keeps its own **status → tone** mapping object (e.g. `PEDIDO_TONE = { PENDIENTE: "warning", CONFIRMADO: "chip", APROBADO: "success", EN_ENVIO: "info", ENTREGADO: "success", CANCELADO: "danger" }`) — the mapping logic stays page-local (it's domain knowledge, not presentation), only the rendering primitive is shared.

**Do not extract:**

- **Generic `Card` wrapper** — every card's internal shape differs enough (some have an internal header with its own border, some don't; padding varies 18–22px; some have `overflow-hidden` for a table, some don't) that a wrapper would need so many props/slots it wouldn't save anything over `className="bg-white border border-line-200 rounded-card"` inline. Matches the codebase's existing convention of no generic layout primitives.
- **`StatCard`** — stays page-local to `Dashboard.jsx` (it already exists as a local component there today; keep it, just extend its `tone` prop per §5). It has exactly one consumer; moving it to `components/` would be indirection with no second caller to justify it.
- **A shared "Auth page" gradient-card wrapper** for Login/PagoResultado — the two pages differ enough in structure (form vs. status/result display) and each is single-occurrence; not worth the abstraction.

## 5. Dashboard `StatCard` — full tone table

The mockup shows exactly **4** stat cards (Productos, Pedidos, Envíos, ⚠ Bajo stock). The current `Dashboard.jsx` renders **7** across two grid rows (a second row — Pendientes, Envíos en Ruta, Valor Inventario — exists today and is real functionality, not something to remove per the "don't change behavior" rule). Since the mockup doesn't cover the second row, this design extends the same visual system to it rather than leaving it stale-styled or deleting it:

| Stat | Tone | Icon chip bg / text | Card border | Source |
|---|---|---|---|---|
| Productos | `brand` | `bg-brand-50` / `text-brand-700` | `border-line-200` | mockup |
| Pedidos | `success` | `bg-success-bg` / `text-success-text` | `border-line-200` | mockup |
| Envíos | `accent` | `bg-accent-bg` / `text-accent-text` | `border-line-200` | mockup |
| ⚠ Bajo stock | `danger` | `bg-danger-bg` / `text-danger-text` | `border-danger-border` (whole card, plus label/value in `danger-strongText`/`danger-strong`) | mockup |
| Pendientes | `warning` | `bg-warning-bg` / `text-warning-text` | `border-line-200` | extrapolated (not in mockup) |
| Envíos en Ruta | `info` | `bg-info-bg` / `text-info-text` | `border-line-200` | extrapolated (not in mockup) |
| Valor Inventario | `accent` | `bg-accent-bg` / `text-accent-text` | `border-line-200` | extrapolated (not in mockup) |

`StatCard` gets a `tone` prop replacing the current freeform `color` className string; the `danger` tone is the only one that changes the *card's* border/text (not just the icon chip), matching the mockup's distinct treatment of the low-stock card.

## 6. Scope and sequencing for this cycle

**Decision: one dev cycle, all 7 pages + shared tokens/components, sequenced as one commit per file (tokens → fonts → Sidebar/StatusBadge → App.jsx → each page) rather than split into two human-facing review cycles.**

Justification:
- The prerequisite work (Tailwind tokens, fonts, `Sidebar`, `StatusBadge`) is shared infrastructure that every page depends on — splitting the *pages* into two cycles wouldn't reduce that shared surface, it would just make the reviewer look at the token table twice (once in cycle 1's diff, once again implicitly when cycle 2's pages consume it).
- This is pure visual restyling (§ "what must NOT change" — no new logic anywhere), so the review burden per page is "does this match the mapping table," a mechanical check, not a logic review. Seven mechanical checks in one PR is reasonable; two review round-trips for the same kind of check is not proportionate overhead.
- The manual-QA gap (see §9) exists identically whether the untested pages ship in cycle 1 or cycle 2 — deferring 4 of them to a second cycle doesn't buy back any automated safety net, since none of the deferred pages have tests either way.
- Each page file is an independent, low-blast-radius change (restyling `Envios.jsx` cannot break `Pedidos.jsx`), so there's no cross-page coupling risk that would argue for smaller batches.

The developer should still land this as **one commit per file** (not one giant commit) so the reviewer can bisect/review incrementally within the single cycle, and so a single problematic page can be reverted without reverting the whole revamp.

## 7. What must not change (behavior preservation checklist)

- Dashboard: `dashboardAPI.get()` call, `estadoAgregacion`/`estado` state, loading spinner condition, `onNavigate` callbacks to `"pedidos"`/`"inventario"`.
- Inventario: `search` filter predicate (name/SKU/bodega substring match), `bajosStock` count, `stockActual < umbralMinimo` low-stock predicate, form validation/`handleSubmit`.
- Pedidos: cart (`lineas`) add/increment/remove logic, SKU autocomplete matching, `totalCalculado`, expand/collapse (`expanded` state), status filter (`filtro`), `localStorage` line-item persistence (`pedido_lineas_${id}`).
- Envíos: kanban grouping (`byStatus`), `nextStatus`/`handleAdvance` status-advance action, form `handleCreate`.
- MisDatos: `estadoAgregacion` OK/PARCIAL/ERROR branching, `cuenta === null` → `CuentaAusenteCard` branch, retry button behavior. **Exact strings preserved** (queried by `MisDatos.test.jsx`): "Cargando" (regex, loading state), "No pudimos cargar tus datos" (generic error), "Reintentar" (button name), the full `PARCIAL_TEXT`/`ERROR_TEXT`/`CUENTA_AUSENTE_TEXT` constants, "derecho de acceso" and "Ley 21.719" (compliance disclaimer), and the account fields rendered as plain text (`user@smartlogix.cl` must remain queryable via `getByText`).
- PagoResultado: all 5 `estado` branches (`loading`, `PAGADO`, `RECHAZADO`, `ANULADO`, `PENDIENTE`, `error`), the `token` query-param / no-token branching in the two `useEffect`s, the mount-fade transition. `App.test.jsx` queries `/pago exitoso|verificando pago/i` — both strings preserved.
- Login: `handleSubmit` validation/error-message logic, `isReg` toggle, placeholder texts (`admin@smartlogix.cl`, `••••••••`) exactly as `Login.test.jsx` queries them by placeholder, all button/heading accessible names (`iniciar sesión`, `ingresar`, `crear una`, `crear cuenta`, `completa todos los campos`, `¡cuenta creada! ahora inicia sesión.`).
- App.jsx: `checkingSession`/`authenticated` gating, `esPagoResultado` path check, `logout()` behavior, and the accessible names `App.test.jsx` queries (`role: button, name: /dashboard/i}`, `/cerrar sesión/i`, heading `/smartlogix/i`).

## 8. Acceptance criteria

1. `npm run test` (Vitest) passes with **zero modifications** to `App.test.jsx`, `pages/Login.test.jsx`, `pages/MisDatos.test.jsx`.
2. `npm run build` (Vite) completes without errors after the Tailwind config and font changes.
3. `npm run lint` passes with no new errors introduced by the restyle.
4. Sidebar renders identically (248px width, logo mark, 5 nav items + logout, `#f6f7f9`-adjacent white bg, `line-400` right border) on all 5 in-app pages (Dashboard, Inventario, Pedidos, Envíos, MisDatos), and the **active nav item matches the currently rendered page** in each case (`bg-brand-50 text-brand-700` on the active item, `text-ink-500` on the rest) — verified manually/visually per page since there is no automated DOM assertion for Tailwind classes today.
5. Login and PagoResultado (`PAGADO` state) render with the exact gradient stops, card radius, and icon/badge colors specified in §2/§3 (spot-checked against the mockup files, not approximated).
6. The `#6b6e7d` secondary-text color (`ink-400`) is the one that ships — grep the built CSS/config for any lighter substitute (e.g. `#9ca3af`/Tailwind `gray-400`) used in a "secondary text" context and confirm none remain.
7. Sora is loaded and applied only to elements with `font-heading` (page `h1`s, section titles, StatCard numbers, the "SmartLogix" wordmark); Public Sans is the default body font everywhere else — verified via computed style / DevTools font panel on at least one element of each family.
8. Inventario: submitting the "Agregar producto" form with a duplicate SKU (or any pre-existing negative-path behavior in `inventarioAPI.create`) still shows the existing red error banner — restyled colors (`danger-bg`/`danger-text`) but unchanged trigger condition/text source (`err.response?.data?.message`).
9. Pedidos: clicking a collapsed row still expands the detail panel showing the correct localStorage-backed line items; clicking again collapses it. Restyled colors only — `expanded` state logic unchanged.
10. Envíos: clicking the "→ NEXT_STATUS" action still calls `enviosAPI.updateStatus` and moves the card to the correct kanban column after reload; a card in the terminal `ENTREGADO` column shows no advance button (unchanged conditional render).
11. MisDatos: all 6 existing Vitest scenarios (loading, generic error + retry, OK/no-banner, PARCIAL banner, ERROR banner, `cuenta === null`) render with the new visual tokens but with **identical text content and identical branch triggers** — confirmed by the unmodified test file passing.
12. Login: submitting with empty fields still shows "Completa todos los campos" and does not call `authAPI.login` (unchanged negative-path behavior); a rejected login still surfaces the backend's `err.response.data.message` string, restyled only in color/border.
13. No page introduces a hard-coded hex color or px radius value outside the token table in §2 — all new/changed `className`s reference the Tailwind tokens (`brand-*`, `ink-*`, `line-*`, `success/warning/danger/info/accent/chip`, `rounded-card/nav/input/field/chip`) rather than arbitrary values, except where explicitly called out in §2.4 (e.g. `w-[248px]`).
14. `Sidebar.jsx` and `StatusBadge.jsx` exist under `frontend/smartlogix-app/src/components/`, are imported by `App.jsx` and by all 5 status-displaying pages respectively, and no page still contains its own inline copy of the pill-badge JSX after the change.

## 9. Open questions

1. **The 4 untested pages (Dashboard, Inventario, Pedidos, Envíos, PagoResultado — 5, not 4, once PagoResultado is counted) have no Vitest coverage**, per the deferral noted in `docs/designs/frontend-test-infrastructure.md`. This design's correctness for those pages rests entirely on manual/QA visual comparison against the `.dc.html` mockups — there is no automated visual-regression tool in this repo (`grep` for playwright/percy/chromatic/cypress found nothing) and none is proposed here (would be scope creep beyond a pure restyle). **The orchestrator should confirm with the human whether a manual QA pass against the 7 mockup files is an acceptable verification method for this cycle**, or whether visual-regression tooling should be a prerequisite before merging — I've assumed the former to keep scope tight, but it's a real gap worth surfacing.
2. **PagoResultado's 4 non-`PAGADO` states have no mockup.** I've chosen to leave their current Tailwind colors untouched rather than invent new tokens for a design the canvas never reviewed. If the human wants full-suite consistency (e.g. a matching red/gray/amber gradient system), that needs its own mockup pass before implementation — flagging rather than guessing.
3. **Dashboard's second stat-card row (Pendientes/Envíos en Ruta/Valor Inventario) has no mockup.** I extrapolated tones (`warning`/`info`/`accent`) to keep visual consistency without deleting existing functionality. Worth a quick human sanity-check that these tone choices read correctly next to the 4 mockup-defined cards, since they were never actually designed.
4. **Icon fidelity**: the mockups use hand-drawn inline SVGs; this design substitutes the closest `lucide-react` icons (`Home`, `Package`, `ShoppingCart`, `Truck`, `User`, `LogOut`) rather than hand-copying the mockup's exact SVG paths, to stay consistent with the icon library already used elsewhere in the app (`Dashboard.jsx`, `MisDatos.jsx`). If pixel-exact icon shapes matter to the approved design, the mockup SVGs should be copied verbatim instead — flagging the substitution rather than assuming it's fine.
5. **248px sidebar width has no matching Tailwind default step** — using an arbitrary-value utility (`w-[248px]`) is the literal-accuracy choice per this task's instructions, but it's worth confirming the team is fine with one arbitrary-value class in an otherwise token-driven system (the alternative, rounding to `w-64`/256px, would violate the "don't round or reinterpret" instruction).

## QA feedback (iteration 1) — DESIGN GAP, routed back to architect

**VERDICT: FAIL** on acceptance criteria 5 and 7 (font loading), everything else PASSED — including exhaustive live functional re-testing of all 4 previously-untested pages (cart math, autocomplete, kanban status transitions, search filtering, expand/collapse) against the real docker-compose stack, which found **zero behavioral regressions**. The "no behavioral changes" claim in §7 holds.

**The bug**: §2.2/§3 prescribe `<link>` tags loading Sora/Public Sans from `fonts.googleapis.com`/`fonts.gstatic.com`. The developer implemented this exactly as specified. But `frontend/smartlogix-app/nginx.conf` already ships a Content-Security-Policy (`style-src 'self' 'unsafe-inline'`, added in an earlier security-hardening cycle this session) with no allowance for either Google Fonts host. Under `npm run dev` there's no CSP, so this was invisible — QA only found it by testing the actual dockerized/nginx-served build, where the font stylesheet request fails with `errorText: 'csp'` and every "Sora"/"Public Sans" declaration silently falls back to `system-ui`. §3's "Affected files" table never listed `nginx.conf`, and this conflict wasn't in §9's open questions — it's a real gap in this design, not something the developer missed.

**Decision needed** (this requires a design call, not a mechanical fix): either (a) relax the CSP to add `style-src`/`font-src` allowances for `fonts.googleapis.com`/`fonts.gstatic.com`, or (b) self-host the fonts via npm (`@fontsource/sora` + `@fontsource/public-sans`), which works under the existing `'self'` CSP with zero policy change and avoids a third-party network dependency that leaks visitor IPs to Google on every page load. Revise §2.2/§3 with the chosen approach and update the affected-files table to include `nginx.conf` if the CSP-relaxation path is chosen, or `package.json`/`index.css`/`index.html` (font `@import`/`@font-face` wiring) if self-hosting is chosen.

**Resolution: see "Revision 1" at the top of this document — self-hosting (option b) was chosen.**
