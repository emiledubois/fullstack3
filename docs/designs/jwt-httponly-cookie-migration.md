# JWT Authentication Migration: localStorage → httpOnly Cookie

## 1. Summary

SmartLogix currently issues a JWT in the JSON body of `POST /api/auth/login`, and the frontend stores it in `localStorage` (`frontend/smartlogix-app/src/services/api.js`), attaching it to every request via an axios request interceptor (`Authorization: Bearer <token>`). Any JavaScript running in the page — including code injected via a future XSS bug — can read `localStorage` and exfiltrate the token. This is documented as a known limitation in `README.md` ("Limitaciones conocidas") and `docs/COMPLIANCE_CL.md` (§4.1, "Deber de seguridad").

This design moves the JWT out of JS-readable storage entirely: `auth-service` sets it as an `httpOnly`, `Secure`, `SameSite=Lax` cookie on `login`, `api-gateway`'s `AuthFilter` reads the JWT from that cookie instead of the `Authorization` header, and the frontend stops touching the token altogether (no `localStorage`, no manual header). This is a **clean cutover, not a transition period** — the JSON body will no longer contain the token at all. Keeping a dual-mode ("cookie AND body") would leave the exact JS-readable exfiltration path this change exists to close, so a hybrid is deliberately rejected (see §5 for the rationale). Because the frontend can no longer read the token to know "am I logged in", this design adds one small, in-scope endpoint (`GET /api/session`) so the SPA can ask the server instead of inspecting client state.

Because cookies are auto-attached by the browser (unlike a header the JS interceptor had to set manually), this migration introduces CSRF exposure that did not exist before. §6 (A04, A07) and the CSRF-specific discussion below spell out the mitigation and why it's sufficient without a stateful double-submit token.

## 2. Affected services

| Service | Change | Why |
|---|---|---|
| `auth-service` | `AuthController`/`AuthService` — `login` sets the JWT via `Set-Cookie` instead of returning it in the body; new `POST /auth/logout` to clear the cookie. | Origin of the token; only service that issues it. |
| `api-gateway` | `AuthFilter` reads the JWT from the cookie instead of `Authorization` header; strips the cookie before forwarding downstream (mirrors the existing `X-User-Email` stripping); `SecurityConfig` CORS: `allowCredentials(true)`; new `SessionController` + route for `GET /api/session`. | Central JWT validation point (API Gateway pattern) and CORS policy owner. |
| `frontend/smartlogix-app` | `services/api.js` — remove `localStorage` interceptor, add `withCredentials: true`; `Login.jsx` — stop storing the token, use the JSON body only for UI (email/role); `App.jsx` — replace synchronous `localStorage.getItem("token")` check with an async call to `GET /api/session` on mount; logout calls `POST /api/auth/logout`. | Frontend can no longer read the cookie; must ask the server for session state. |
| `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos`, `notification-service` | **No change.** | They never see the JWT — they trust the gateway-set `X-User-Email` header. This is confirmed unaffected. |

This is **independent parallel changes**, not a Saga or a Facade: there is no multi-step business transaction with compensations, and no single service coordinates subsystems on behalf of a caller. `auth-service` and `api-gateway` each own one side of a shared contract (cookie name/attributes, JWT expiration), and the frontend adapts to both. The main coordination risk isn't transactional consistency, it's **deployment ordering** — see Open Questions.

## 3. API contract

Convention unchanged: all endpoints require the JWT except `/api/auth/*` and `/api/pagos/webhook/flow`. The JWT is now supplied automatically as a cookie rather than manually as a bearer header — "auth requirement" below means "valid, unexpired `sl_jwt` cookie, signature verified against `JWT_SECRET`, HS256" unless stated otherwise.

### 3.1 `POST /api/auth/register` — **no contract change**
Unchanged request/response. Included here only to confirm it is not touched: no token is issued at registration today, and this design doesn't add one.

### 3.2 `POST /api/auth/login` — **changed response**
- Auth: public (as today).
- Request: unchanged — `{ "email": string, "password": string }` (`@NotBlank` on both, per existing `LoginRequest`).
- **Response body — changed.** Was: raw JWT string. Now:
  ```json
  { "email": "user@pyme.cl", "role": "ROLE_USER", "expiresAt": "2026-08-27T14:00:00Z" }
  ```
  `expiresAt` is derived from the same `jwt.expiration` the token is signed with — it's informational only (lets the frontend show "session expiring soon"), it is **not** a security control (the cookie's own `Max-Age`/JWT signature are what's enforced server-side).
- **New response header:** `Set-Cookie: sl_jwt=<jwt>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=<jwt.expiration in seconds>`. `Secure` is conditional on a new `COOKIE_SECURE` property — see §6 A05.
- Status codes:
  - `200 OK` — valid credentials, cookie set, body as above.
  - `401 Unauthorized` — invalid email/password. **Behavior change from today**: `AuthService.login` currently throws a plain `RuntimeException("Credenciales inválidas")` with no `@ExceptionHandler`, which Spring Boot's default error handling turns into `500 Internal Server Error`. Since this method is being touched anyway to add cookie logic, this design also adds a minimal exception mapping so bad credentials return `401`, not `500` — QA needs a real status code to assert against. No `Set-Cookie` header on this path.
  - `429 Too Many Requests` — unchanged Resilience4j rate limiter (5/60s), `Retry-After: 60`. **Implementation note:** the fallback method's signature (`loginRateLimitFallback`) must be updated to a return type compatible with the new controller return type (`ResponseEntity<?>` on both, or a shared supertype) — today both are `ResponseEntity<String>` so they match; changing the success path to a DTO without updating the fallback will break the Resilience4j proxy wiring.
  - `400 Bad Request` — Bean Validation failure (unchanged).

### 3.3 `POST /api/auth/logout` — **new**
- Auth: **public, no cookie required** (idempotent — calling it with no session, an expired cookie, or a tampered cookie all succeed). Rationale: the worst case of an unauthenticated caller hitting this is forcing an unrelated browser to log out, which is a nuisance, not a privilege escalation or data exposure — consistent with treating it like `login`/`register` under the existing public `/api/auth/**` gateway route.
- Request: no body.
- Response: `200 OK`, empty or `{"message":"Sesión cerrada"}`. Sets `Set-Cookie: sl_jwt=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0` to instruct the browser to delete the cookie.
- If a valid `sl_jwt` cookie **is** present, log the logout event (email from the token, timestamp) for audit purposes — see §6 A09 / §7. If absent or invalid, still return `200` without attempting to log an identity.

### 3.4 `GET /api/session` — **new**
Needed because the frontend can no longer read the cookie to decide whether to show the login screen or the app shell; `App.jsx`'s current `const token = localStorage.getItem("token")` synchronous check has no cookie equivalent.
- Auth: **required** (goes through the normal `AuthFilter` pipeline, like `/api/inventario/**` etc. — not under `/api/auth/**`, which is intentionally the public route).
- Implemented in **api-gateway** (not auth-service) as a small controller alongside the existing `DashboardController`, re-using the gateway's own `JwtUtil` bean to read the already-validated cookie/claims directly — no new inter-service call, no auth-service DB hit. `JwtUtil` in api-gateway needs one addition: extract the `role` claim (today it only extracts email).
- Response: `200 OK` → `{ "email": "user@pyme.cl", "role": "ROLE_USER" }`.
- Status codes:
  - `200 OK` — valid cookie.
  - `401 Unauthorized` — missing/invalid/expired cookie (same `AuthFilter` behavior as any other protected route: `"Token requerido"` / `"Token inválido"`).

### Summary of AuthFilter behavior change
| | Before | After |
|---|---|---|
| Token source | `Authorization: Bearer <jwt>` header | `sl_jwt` cookie |
| Missing token | `401 "Token requerido"` | `401 "Token requerido"` (same message, new source) |
| Invalid/expired token | `401 "Token inválido"` | `401 "Token inválido"` (unchanged — `jwtUtil.isValid()` already catches `ExpiredJwtException` generically) |
| `X-User-Email` anti-spoofing | Strips client header, sets verified value | **Unchanged** — same strip-then-set logic, now fed by the cookie-sourced token instead of header-sourced |
| Downstream `Cookie` header | Forwarded as-is (not scrubbed) | **New:** stripped before proxying to `ms-inventario`/`ms-pedidos`/`ms-envios`/`ms-pagos`, mirroring the `X-User-Email` minimization pattern — those services never need the raw JWT and shouldn't see it |

No change to Authorization-header acceptance is added as a fallback. This is a deliberate **clean cutover**: accepting the token from two sources (cookie for the browser, header for tooling) would mean the CSRF/XSS reasoning below only holds for one of the two paths, and a hiring engineer reviewing this should see one unambiguous source of truth for identity. The practical cost: README's `curl`-based examples that capture `$TOKEN` from the login response body no longer work as written and must be updated to use a cookie jar (`curl -c cookies.txt -b cookies.txt ...`) — flagged in Open Questions as doc/script cleanup for the developer agent.

## 4. Data model changes

**None, confirmed.** No new tables or columns in `auth_db` or any other service database. The `users` table (`email`, `password`, `role`) is untouched. The JWT remains fully stateless — there is no server-side session table, token store, or revocation list. This means `POST /api/auth/logout` clears the *browser's* copy of the cookie but does not invalidate the JWT itself: if a copy of the token existed anywhere else (e.g., captured in transit before this fix, or via a compromised network path), it stays valid until its natural `exp` claim. This is not a regression — `localStorage.removeItem("token")` had the identical limitation today — but it's worth stating explicitly since "does logout need a cookie-clearing endpoint" invites the follow-up "does it actually revoke the session", and the honest answer is no. See Open Questions.

## 5. Design pattern fit

This extends the existing **API Gateway** pattern already documented in the README: `api-gateway`'s `AuthFilter` is already the single, centralized point of JWT validation for every downstream request except `/api/auth/*` and the Flow webhook. This design changes *where the filter reads the token from*, not the pattern itself — no new abstraction is introduced. The new `GET /api/session` endpoint reuses the same **WebHub/BFF** placement convention already established by `DashboardController` (a controller that lives in the gateway and answers directly, without a downstream microservice hop) — again not a new pattern, just applying the existing one to a second, much simpler use case.

The rejected alternative worth naming: a **transition/dual-mode** login response (cookie *and* body token) was considered per the task's option to do so, and rejected. It would technically ease a staged rollout, but it directly reintroduces the JS-readable token this design exists to eliminate, so for a single first-party SPA with no external API consumers to migrate gradually, the dual-mode has cost (residual XSS exposure) without a corresponding benefit (no other client needs the old contract).

No new GoF pattern (Strategy, Observer, Factory, etc.) is warranted — this is a transport-mechanism change to an existing cross-cutting concern, not new domain logic. Introducing e.g. a "TokenExtractionStrategy" abstraction for a single, fixed source (cookie) would be speculative.

## 6. Security requirements (OWASP Top 10 2021)

### A01 — Broken Access Control
- Authorization model is unchanged: still "any authenticated user can call any endpoint" — SmartLogix is single-tenant per PYME with a shared staff pool (per `COMPLIANCE_CL.md` §4.1, "N/A — modelo de dominio es single-tenant"), so there is no per-resource ownership check to add or regress here.
- The `X-User-Email` anti-spoofing strip-then-set in `AuthFilter` **must not be weakened**: it must continue to run *after* the token is validated and *before* forwarding, exactly as today, just fed from the cookie instead of the header. This is the mechanism that prevents identity spoofing downstream — confirm in review that the cookie-reading refactor didn't accidentally move this to before validation or drop it.
- New requirement: the `sl_jwt` cookie itself must be stripped before proxying to internal services (see §3 table) — not an access-control fix (internal services already don't trust it) but a minimization measure consistent with the existing "internal services shouldn't see credentials they don't need" posture.
- `GET /api/session` and `POST /api/auth/logout` return only the caller's own identity/action — no user/tenant ID is accepted as a path or body parameter, so there is no IDOR surface to check on these two new endpoints.

### A02 — Cryptographic Failures
- No new secrets are introduced. `JWT_SECRET` handling (HS256, no hardcoded fallback, `verifyWith(SecretKey)`) is unchanged.
- The JWT itself carries only `email` and `role` — no payment data, no card data (Flow never sends the PAN to SmartLogix per `COMPLIANCE_CL.md` §2). Moving it to a cookie doesn't change what it contains.
- `Secure` requires TLS to have any effect (browsers refuse to send `Secure` cookies over plain HTTP). **This repo's Docker Compose deployment runs entirely over HTTP today** (`COMPLIANCE_CL.md` §4.1 lists "Cifrado en tránsito (HTTPS) en producción ⚠️ pendiente"). This design does **not** fix that gap, but it does make it load-bearing for the first time: `COOKIE_SECURE` must default to `true` and be explicitly set to `false` only in the local/dev `.env` (same convention as `FLOW_VERIFY_SIGNATURE`), or the cookie will silently never be sent back to the gateway and login will appear to "not persist." Before any real production deployment, TLS termination is now a hard prerequisite, not just a documented limitation — flagged again in §7.
- Net risk comparison: over unencrypted HTTP, a network-level attacker could already read the bearer token today (same as they'll be able to read the cookie tomorrow) — this migration does not change the transport-level exposure, it only closes the **JS-readable/XSS** exposure. Don't oversell this change as fixing "cryptographic failures" — it fixes A03-adjacent/XSS token theft, not transport security.

### A03 — Injection
N/A. No new SQL, dynamic queries, or shell/command construction. Cookie value is passed straight into the existing `jjwt` parser (`Jwts.parser().verifyWith(...).parseSignedClaims(...)`), identical call used today for the header value — just a different source string, same signature-verified parsing path, no new parser/deserializer introduced.

### A04 — Insecure Design (abuse cases)
- **Retry/double-submit of login:** idempotent — calling `login` twice issues two valid cookies (the second overwrites the first in the browser); no server-side state to corrupt. No change from today's behavior other than a cookie replacing a returned string.
- **Race condition — concurrent register with same email:** pre-existing behavior, unrelated to this design (DB has a `unique` constraint on `email`, so the loser of the race gets a constraint-violation-driven `500` today; not introduced or fixed by this change — noted so it isn't mistaken for new scope).
- **CSRF (the abuse case this design most needs to address, since cookies are auto-attached where a manually-set `Authorization` header wasn't):**
  - **Is `SameSite=Lax` sufficient, or is `Strict` needed, or a double-submit token?** `SameSite=Lax` is the correct choice, not `Strict`, and a stateful double-submit token is **not warranted**. Reasoning:
    1. `SameSite=Lax` cookies are **not** attached to cross-site subresource requests — XHR/`fetch`, `<img>`, `<iframe>`, and cross-site `<form>` POSTs — regardless of method. They're only attached on top-level, "safe" (GET) navigations. This already blocks the realistic CSRF vector for a JSON SPA: a malicious page doing `fetch('https://smartlogix/.../pedidos', {method:'POST', credentials:'include'})` from another origin gets no cookie attached at all.
    2. `Strict` would additionally block the cookie on top-level cross-site GET navigations, which matters for one real flow here: Flow Chile redirects the user's browser back to `FLOW_URL_RETURN` (`/pago-resultado`) via a top-level navigation from `sandbox.flow.cl`. Confirmed by reading `PagoResultado.jsx`: that route only renders static content and reads a `token` query param; it does **not** need the auth cookie on the redirect request itself — the follow-up call to `GET /api/pagos/por-token/{token}` happens from JS *after* the page has already loaded on our own origin, which is same-site by then and gets the cookie under either `Lax` or `Strict`. So `Strict` would happen to work today too — `Lax` is chosen as the more standard, less surprising default (avoids breaking any future flow that does need the cookie on a top-level redirect landing, e.g. an email-link deep link into an authenticated page).
    3. Defense in depth already present in this codebase independently helps here: all mutating endpoints consume `application/json` (`@RequestBody` + Bean Validation). A forged cross-site request using a plain HTML `<form>` cannot set `Content-Type: application/json` (only `text/plain`, `application/x-www-form-urlencoded`, `multipart/form-data` are "simple" content types) — such a request would either fail JSON deserialization server-side or, if attempted via `fetch` with an explicit JSON content type, would trigger a CORS preflight that fails against `api-gateway`'s explicit origin allowlist (§A05).
    4. **Recommended additional cheap layer (should-do, not blocking):** have the frontend send a fixed custom header (e.g. `X-Requested-With: XMLHttpRequest`, already present in the gateway's `allowedHeaders` CORS list) on every request, and have `AuthFilter` reject state-changing methods (`POST`/`PUT`/`PATCH`/`DELETE`) that lack it. A simple `<form>` CSRF cannot set custom headers, and a `fetch`-based one triggers a CORS preflight that the origin allowlist blocks. This gives synchronizer-token-equivalent protection without the complexity of issuing, storing, and validating a random per-session value — appropriate for a JSON-only API with a single first-party frontend origin. If SmartLogix ever needs to support a genuinely third-party/cross-site frontend (a separate marketing domain calling the API, a mobile WebView, etc.), that's the point to revisit and add a real double-submit cookie token — not before, as that would be speculative for the current single-frontend shape.
  - **Login/registration CSRF** ("force a victim to log into the attacker's account"): low severity here (no ambient authority is exercised by these two endpoints — they're public, and a forced login just leaves the victim logged into an account the attacker controls, with no data of the victim's exposed). Not mitigated separately; covered by the same CORS + content-type reasoning above.

### A05 — Security Misconfiguration
- New env var: `COOKIE_SECURE` (auth-service) — default `true`; must be documented in README's variable table and set to `false` only in local/dev `.env` (mirrors `FLOW_VERIFY_SIGNATURE` convention). **Must be `true` in any deployment reachable over the public internet.**
- `api-gateway`'s CORS config (`SecurityConfig.corsConfigurationSource`) changes `setAllowCredentials(false)` → `true`. This is safe **only** because `allowedOriginPatterns` remains an explicit allowlist (`localhost:5173`, `localhost:3000`, `127.0.0.1:5173`, `localhost`) rather than `*` — Spring rejects `*` with credentials at startup, so this can't silently regress into a wildcard-with-credentials misconfiguration, but review should confirm no new wildcard entry is added to that list as part of this change.
- No new exposed ports, no new actuator endpoints.
- Cookie `Domain` attribute must be left **unset** (default to the request host) rather than hardcoded to a specific service hostname — this is what makes the cookie work transparently through both the Docker nginx reverse-proxy (`nginx.conf` → `api-gateway:8080`) and the Vite dev-server proxy (`vite.config.js` → `localhost:8080`), since in both cases the browser only ever sees the frontend's own origin.

### A07 — Auth Failures
- JWT signature validation, algorithm (HS256, fixed via `verifyWith(SecretKey)`), and expiration handling in `JwtUtil` are **unchanged** — only the string's transport location changes.
- `AuthFilter`'s existing generic `catch (Exception e) { return false; }` in `isValid()` already handles expired/malformed/wrong-signature tokens uniformly regardless of source — no new handling needed for the cookie case.
- Logout does not revoke the JWT server-side (see §4) — this is a known, accepted limitation of the stateless-JWT design, not something this feature claims to fix.

### A08 — Software/Data Integrity
N/A for external input integrity beyond what already exists: the cookie value goes through the same signature-verified `jjwt` parse as the header value did — no new deserialization surface. The Flow webhook's HMAC-SHA256 signature verification (`ms-pagos`) is untouched by this design.

### A09 — Logging/Monitoring
- **Must log** (new, since this design touches the login/logout lifecycle directly and `COMPLIANCE_CL.md` §4.4 flags "Eventos de seguridad relevantes logueados... Verificar por servicio" as unverified): login success (email, timestamp), login failure (email attempted, timestamp — **not** the password), and logout (email, timestamp, only when a valid cookie was presented).
- **Must NOT log**: the JWT value itself (neither the cookie header nor the token string), the password, or the full `Set-Cookie`/`Cookie` header content. Existing convention in `ms-pagos` of masking sensitive tokens (`token.substring(0,8)+"***"`) is a reasonable model if any debug logging of the cookie header is ever added — but the default should be to not log it at all.

### A10 — SSRF
N/A. No outbound HTTP calls are built from user input in this feature.

## 7. Chilean compliance touchpoints

Per `docs/COMPLIANCE_CL.md`, `auth-service` processes "email, hash de contraseña" — a dato personal + credencial squarely in scope of Ley 21.719 (in force 2026-12-01). This feature is directly relevant:

- **§4.1 Deber de seguridad (Ley 21.719) / gestión de riesgos (Ley 21.663):** this change is concrete progress on the "Limitaciones conocidas" item already called out in README and implicitly in §4.1's security posture — it closes the JWT-in-`localStorage` XSS exposure. It should be reflected as a checklist update once merged (move the relevant README bullet and any corresponding compliance note from "known limitation" to "resolved," citing the PR/commit, per §5 of `COMPLIANCE_CL.md`'s own instructions to the reviewer).
- **§4.1 "Cifrado en tránsito (HTTPS) en producción ⚠️ pendiente":** this design makes that item a **hard functional prerequisite**, not just a best-practice gap — `COOKIE_SECURE=true` without TLS breaks login. Recommend escalating this line item's priority in the compliance doc as a direct consequence of this feature, ahead of any real production deployment.
- **§4.4 Logging y auditoría:** this design adds concrete login/logout audit events (see A09), which is a direct, in-scope improvement to the "Verificar por servicio" row for `auth-service`.
- **ARCO+ rights (§4.2):** not affected. No new personal data field is created, stored, exposed, or deleted by this change — the cookie carries the same `email`/`role` claims the JWT always did, just via a different transport. No data subject access/deletion/retention question is newly raised.
- **Retention:** N/A — no new persisted data; the cookie's lifetime (`Max-Age`) is a client-side, session-scoped artifact, not a retained record subject to Ley 21.719 retention-limit obligations.

## 8. Acceptance criteria

1. `POST /api/auth/login` with valid credentials returns `200`, a JSON body containing `email` and `role` (and `expiresAt`), and a `Set-Cookie: sl_jwt=...` header with `HttpOnly`, `SameSite=Lax`, `Path=/` attributes, and `Secure` present when `COOKIE_SECURE=true`. **The response body does not contain the JWT in any field.**
2. `POST /api/auth/login` with invalid credentials returns `401` (not `500`), no `Set-Cookie` header, and a body that does not leak whether the email exists vs. the password being wrong (unchanged existing behavior — same generic message for both).
3. `POST /api/auth/login` beyond the rate limit (6th attempt within 60s) returns `429` with `Retry-After: 60`, as today.
4. A request to any previously-protected endpoint (e.g. `GET /api/inventario`) **with no cookie at all** returns `401 "Token requerido"`.
5. A request with a cookie containing a syntactically valid but wrong-signature JWT returns `401 "Token inválido"`.
6. A request with a cookie containing an **expired** JWT (`exp` in the past) returns `401 "Token inválido"`.
7. A request with a cookie containing a valid, unexpired JWT succeeds, and the downstream service (e.g. `ms-inventario`) receives `X-User-Email` set to the value from the verified token — **not** any client-supplied `X-User-Email` header sent alongside the cookie (regression test for the existing anti-spoofing control: send both a forged `X-User-Email` header and a valid cookie for a *different* user; the forwarded header must reflect the cookie's identity, not the forged one).
8. The `Cookie` header is **not** present in the request api-gateway forwards to `ms-inventario`/`ms-pedidos`/`ms-envios`/`ms-pagos` (verifiable via a debug log or test double on one internal service).
9. `GET /api/session` with a valid cookie returns `200` with `{email, role}` matching the logged-in user; with no/invalid/expired cookie returns `401`.
10. `POST /api/auth/logout` always returns `200` (with a valid cookie, with an expired cookie, and with no cookie at all) and always sets `Set-Cookie: sl_jwt=; Max-Age=0` (or equivalent expiry-in-the-past) in the response.
11. After calling `POST /api/auth/logout`, the browser no longer sends `sl_jwt` on subsequent requests, and `GET /api/session` returns `401`.
12. **XSS abuse case:** with a script injected into the page (simulate via dev tools / a test harness, since the codebase has no known XSS today) attempting `document.cookie` or reading any `localStorage`/`sessionStorage` key, **no JWT value is retrievable** — `document.cookie` does not list `sl_jwt` (proves `HttpOnly` is effective) and no `localStorage` key contains a token (`localStorage.getItem("token")` is `null`/absent — the key itself should no longer be written).
13. **CSRF abuse case:** a simulated cross-site `fetch('http://<gateway-origin>/api/pedidos', {method:'POST', credentials:'include', body: ...})` issued from a page on a different origin either (a) sends no cookie at all (`SameSite=Lax` blocking a cross-site subrequest) or (b) is rejected by CORS (origin not in the allowlist) before reaching `AuthFilter` — assert at least one of these holds and the request never results in a created pedido.
14. Frontend: after a fresh login, reloading the app shows the authenticated shell (via `GET /api/session` succeeding) without any token ever appearing in `localStorage`, `sessionStorage`, or a JS-readable cookie.
15. Frontend: clicking "Cerrar sesión" calls `POST /api/auth/logout`, then the app shows the login screen, and a manual reload does not silently re-authenticate.
16. Payment redirect flow (`/pago-resultado`) still works end-to-end after the migration: the top-level redirect from Flow lands on the page, and the subsequent `GET /api/pagos/por-token/{token}` call succeeds using the cookie (no regression from the `SameSite=Lax` change).
17. Docker Compose smoke test: full `docker compose up --build`, login via the frontend at `http://localhost:5173`, confirm cookie is set with the correct attributes for the actual deployment topology (nginx reverse proxy), not just the Vite dev-server proxy path.

## 9. Open questions

1. **Set-Cookie header propagation through the proxy chain** — needs empirical verification during implementation, not just design review: does Spring Cloud Gateway MVC's `HandlerFunctions.http(...)` forward the `Set-Cookie` response header from `auth-service` back through `api-gateway` unmodified, and does nginx's `proxy_pass` in `frontend/smartlogix-app/nginx.conf` do the same? Both are expected to work by default (no header stripped, no `Domain` rewrite needed since we don't set one), but this is the single most load-bearing plumbing detail in this design and should be the first thing the developer agent confirms with a real `curl -v` trace, before writing the rest of the frontend changes.
2. **Deployment ordering** — during rollout, is there a moment where an old frontend build (still doing `localStorage`) talks to a new `auth-service` (no longer returning the token in the body)? If deploys aren't atomic across the three affected components, users mid-session could get stuck. Given this is a single-environment student/portfolio project (not a rolling multi-instance production deployment), this is likely low-risk, but worth a one-line confirmation from whoever runs the deploy (`docker compose up --build` rebuilds everything at once).
3. **Custom-header CSRF layer (§6 A04, point 4)** — recommended but not made mandatory in the acceptance criteria above. Should the orchestrator treat it as in-scope for this cycle, or as fast-follow hardening? It's cheap enough that I'd lean towards including it now, but flagging since it touches `AuthFilter` (a security-critical file) beyond the minimum needed to satisfy the task's literal ask.
4. **README/docs cleanup** — the `curl`-based "Primer uso" section and any other example in README that captures `$TOKEN` from the login response body will no longer work as written. Not a design ambiguity, just confirming it's in scope for the developer agent to update alongside the code (README already documents the localStorage limitation this PR fixes, so leaving it stale would be an inconsistency reviewers would flag).
5. **Cookie name (`sl_jwt`)** — arbitrary choice, no functional constraint found against it. Could alternatively use a `__Host-` prefixed name (`__Host-sl_jwt`) for the stronger browser-enforced guarantee (forces `Secure`, `Path=/`, no `Domain`) once `COOKIE_SECURE=true` is the permanent state in production — not proposed as a requirement now because it would need conditional logic to avoid breaking local HTTP dev (browsers reject `__Host-` cookies without `Secure`). Worth a one-line decision from whoever finalizes the TLS rollout.
