# ARCO+ Derechos Restantes — Rectificación y Portabilidad (Cancelación y Oposición diferidas)

## 1. Summary

`docs/COMPLIANCE_CL.md` §4.2 lists rectificación, cancelación, oposición, and portabilidad as `❌ pendiente`, with derecho de acceso already shipped (`docs/designs/arco-acceso-personal-data.md`: `GET /api/usuarios/me/datos`, api-gateway BFF aggregating auth-service + ms-pedidos + ms-envios, identity derived only from the `sl_jwt` cookie, internal calls signed via `InternalTokenSigner` with per-endpoint issuer allowlists).

This design implements two of the four remaining rights now — **rectificación** (`PUT /api/usuarios/me/email`, correcting the one actual self-describing profile field the codebase has — the account's login email) and **portabilidad** (`GET /api/usuarios/me/datos/exportar`, a thin, explicitly-versioned export envelope around the exact same aggregation already built for acceso) — and explicitly defers **cancelación** and **oposición** to separate future cycles, each with its own scoped-but-unresolved design questions (§9). This split is deliberate, not a shortcut: reading `User.java`, `Pedido.java`, `Envio.java`, and `notification-service`'s only endpoint confirmed that rectificación and portabilidad are small, low-risk, single-service-or-pure-reuse changes, while cancelación is a genuinely large, cross-service, high-risk operation (anonymization vs. retention tension across 4 databases, no existing Saga-with-compensation shape that actually fits "erasure," session-revocation questions) that would not survive review as a single diff — and oposición, on inspection, has **no actual optional/legitimate-interest processing to object to today** (notification-service sends only transactional, contract-necessary logistics notifications; no marketing, analytics, or profiling exists anywhere in this codebase), so building a preference-flag endpoint for it now would be speculative abstraction for a purpose that doesn't exist yet.

## 2. Scope decision for this cycle, and justification

| Right | This cycle? | Why |
|---|---|---|
| **Rectificación** | **Yes** | Reading `User.java` confirms the *only* self-describing, user-correctable field is `email` (`role` has its own admin flow, `password` has its own change-password flow — neither is in scope, matching the task's explicit exclusion). `Pedido.clienteNombre`/`destino` are transactional snapshots about a PYME's *end customer* at order time, not necessarily the logged-in account holder's own data, and are not proposed for retroactive rewriting here (§8, open question). Single-service (`auth-service`) mutation, reusing every existing convention (BFF pattern, internal HMAC call, cookie-derived identity, login's cookie-rotation code shape). |
| **Portabilidad** | **Yes** | `GET /api/usuarios/me/datos` already returns structured, versioned JSON per user — Ley 21.719's portability requirement ("formato estructurado, de uso común, lectura mecánica") is satisfied by JSON today. This needs **no new backend aggregation work**, only a thin export envelope (versioning metadata + `Content-Disposition` framing) around the existing aggregation — a formatting/framing concern, exactly as the access design's own §9 flagged as a likely near-term reuse. |
| **Cancelación** | **No — deferred, §9.1** | Requires a real cross-service decision (hard delete vs. anonymize) with a genuine legitimate-retention tension (financial/order records vs. erasure), touches all 4 relevant databases (`auth_db`, `pedidos_db`, `envios_db`, `pagos_db`), needs new internal endpoints on 3 services, raises a session-revocation question this codebase's stateless-JWT design has never had to answer, and — per `COMPLIANCE_CL.md`'s own §6 priority list — is explicitly named as one of "the two most commonly audited rights," meaning it deserves a dedicated design cycle with its own acceptance criteria, not a rider on this one. Bundling it here would produce a diff spanning 4 services' data models plus session-handling changes, which is not reviewable as a single unit. |
| **Oposición** | **No — deferred/reframed, §9.2** | Grepped the codebase for marketing/analytics/profiling/opt-in/opt-out/newsletter/tracking-consent: **none exists.** `notification-service.NotificationListener` (`POST /notificaciones`) only relays transactional logistics notifications triggered by `ms-pedidos`'s Saga `NotifyStep` (order/shipment status) — processing necessary for contract execution, not the kind of legitimate-interest/optional processing Ley 21.719's derecho de oposición is meant to let a data subject stop while still using the service. Building an opt-out preference flag for a processing purpose that doesn't exist would be exactly the "speculative abstraction" this process is supposed to avoid. |

This mirrors `COMPLIANCE_CL.md` §6's own sequencing language ("implementar al menos Acceso y Cancelación... antes de cualquier despliegue con datos reales") only partially — acceso is done, cancelación is **not** done here despite that suggestion, because the size/risk mismatch is real and is called out explicitly in §9.1 rather than silently accepted. This is a documented deviation from the compliance doc's suggested priority order, made deliberately for review-safety reasons, and should be surfaced to the human before implementation (see final Open Questions).

## 3. Affected services

| Service | Change | Why |
|---|---|---|
| `api-gateway` | New `RectificacionController` (`PUT /usuarios/me/email`) in the same package/pattern as `UsuarioDatosController`; new route wiring (reuses the existing `usuariosRoute()` bean — no new route needed, `/api/usuarios/**` already covers this path, `authFilter` applied, `stripCookieFilter` **not** applied, same as `usuariosRoute()`'s existing rationale). New `GET /usuarios/me/datos/exportar` added to the existing `UsuarioDatosController` (reuses its private `agregarDatos(email)` method — no duplicated aggregation logic). New DTOs: `RectificarEmailRequest`, `RectificacionResponseDTO`, `ExportacionDatosDTO`. | BFF/gateway is where all ARCO+ self-service endpoints already live (`UsuarioDatosController`, `SessionController` precedent). |
| `auth-service` | New internal-only endpoint `PUT /auth/interno/usuarios/{email}` (path already exists for `GET`; this adds `PUT` on the same prefix — `InternalAuthFilter` already guards the whole `/auth/interno/**` prefix regardless of HTTP method, **no filter code change needed**). New `AuthService.rectificarEmail(...)` method. New DTOs: `RectificarEmailInternoRequest`, `RectificacionInternaDTO`. **No schema change** — `email` column already exists, already unique/not-null. | Only service holding `User.email`/`User.password` — the only place password verification and the uniqueness check can correctly happen. |
| `ms-pedidos`, `ms-envios` | **No change.** Rectificación does not retroactively rewrite `Pedido.userEmail`/historical order data (§8, open question) — an explicit, flagged scope decision, not an oversight. Portabilidad reuses the exact same read endpoints the access feature already added (`GET /pedidos/interno/por-email/{email}`, `GET /envios/interno/por-pedidos`) — no new endpoint. | Keeps this cycle's diff to exactly the two services that need it. |
| `ms-inventario`, `ms-pagos`, `notification-service` | **No change.** | Not in scope for either right this cycle (`ms-pagos` was already excluded from the access aggregation itself — same known, flagged gap, not newly introduced here). |
| `frontend` | **No change proposed.** Backend/API-only, same posture as the access design (`arco-acceso-personal-data.md` §9, point 5) — flagged again in Open Questions since two API-only ARCO+ cycles in a row without UI is a growing gap. | Out of explicit task scope. |

**Coordination classification.** Both features are **independent, single-request BFF/Facade operations** — not a Saga. Rectificación is a two-hop call (`api-gateway` → `auth-service`, one write, one internal call, no compensation needed: if the internal call fails, nothing was changed, and the gateway simply returns the error — there is no multi-step transaction to roll back). Portabilidad is a pure read, identical in shape to the already-classified acceso Facade/BFF aggregation (`arco-acceso-personal-data.md` §2) — literally the same code path with a thin wrapper. Neither introduces multi-step compensation logic, so classifying either as a Saga would be over-engineering for what they actually do.

## 4. API contract

Convention (unchanged): all endpoints require the `sl_jwt` httpOnly cookie except `/api/auth/*` and `/api/pagos/webhook/flow`. New internal-only paths (`/auth/interno/usuarios/{email}` `PUT`) are **not** newly excluded from gateway routing — they reuse the exact exclusion already in place for the `GET` on the same path (`GatewayConfig.authRoute()`'s `path("/api/auth/interno/**").negate()`), so no `GatewayConfig` change is needed for this reason; that exclusion already blocks every method on that sub-path, not just `GET`.

### 4.1 `PUT /api/usuarios/me/email` — new (Rectificación)

- Auth: valid `sl_jwt` cookie, no role restriction (same as acceso). Identity (the account being changed) is derived **exclusively** from the cookie — never from a body/path parameter — closing the same IDOR class the acceso design closed for reads.
- Request body:
  ```json
  { "emailNuevo": "correcto@pyme.cl", "passwordActual": "su-contraseña-actual" }
  ```
  Validation (Bean Validation, matching `RegisterRequest`'s existing convention): `emailNuevo` — `@NotBlank @Email`; `passwordActual` — `@NotBlank`.
- Response `200 OK` (email successfully changed, **and** the case where `emailNuevo` case-sensitively equals the caller's current email — a no-op treated as success, not a conflict, per §8 idempotency notes):
  ```json
  { "email": "correcto@pyme.cl", "role": "ROLE_USER", "cuentaCreadaEn": "2026-01-15T09:00:00", "actualizadoEn": "2026-08-27T11:00:00" }
  ```
  Plus a rotated `Set-Cookie: sl_jwt=<new JWT with subject=emailNuevo>` — same `httpOnly`/`secure`/`SameSite=Lax`/`maxAge` shape as `AuthController.login`'s cookie. **The old cookie value must not remain usable as a matching identity going forward** — a fresh token is minted with the new email as `sub`, exactly like a fresh login, so the browser's next request is already authenticated as the corrected identity.
- Status codes:
  - `200 OK` — as above.
  - `400 Bad Request` — malformed `emailNuevo` (not a valid email shape) or missing `passwordActual` (standard `@Valid` rejection, same shape as every other DTO-validated endpoint in this codebase).
  - `401 Unauthorized` — missing/invalid/expired `sl_jwt` cookie (`"Token requerido"`/`"Token inválido"`, unchanged `AuthFilter` behavior), **or** `passwordActual` does not match the account's stored hash (`"Contraseña actual incorrecta"` — distinct message, this is a legitimate step-up-auth failure, not a session failure, and must not be confusable with a stolen-cookie scenario in logs, see §6 A09).
  - `404 Not Found` — the email derived from a still-valid JWT no longer resolves to an account (edge case already documented in the acceso design — cookie outlives the account).
  - `409 Conflict` — `emailNuevo` already belongs to a **different** account (`"Este email ya está registrado"`).
  - `429 Too Many Requests` — new `rectificacionRateLimiter` (Resilience4j, same shape as `loginRateLimiter`: 5/60s, global — not per-user, matching the existing precedent exactly), because this endpoint accepts a password guess and returns a distinguishable 401 vs. 200 (a password-verification oracle for anyone holding a stolen-but-valid cookie — see §6 A04).

### 4.2 `PUT /auth/interno/usuarios/{email}` — new, internal-only

- Not reachable via `/api/**` (already excluded — same predicate as the existing `GET` on this prefix, no `GatewayConfig` change).
- Auth: internal HMAC headers, `InternalAuthFilter`'s existing "everything under `/auth/interno/**` → `api-gateway` only" rule — **no filter code change**.
- `{email}` = the caller's **current** email, always derived server-side by `api-gateway` from the verified cookie — never client input at this hop either (mirrors the `GET` variant exactly).
- Request body: `{ "emailNuevo": "...", "passwordActual": "..." }` (same shape forwarded from §4.1 — this internal hop is where the BCrypt check and uniqueness check actually happen, since only `auth-service` holds `User.password`/`UserRepository`).
- Response `200 OK`:
  ```json
  { "email": "correcto@pyme.cl", "role": "ROLE_USER", "cuentaCreadaEn": "2026-01-15T09:00:00", "token": "<fresh JWT, sub=emailNuevo>" }
  ```
  `token` is a **bearer credential in transit over the already-authenticated internal channel** — this is new: no prior internal DTO in this codebase carried a session token. It must never be logged (§6 A02/A09) and this DTO's `toString()` must not include it verbatim (either a custom `toString` masking it, or omit `@Data`'s auto-generated one for this specific class and write an explicit accessor-only class).
- `401 Unauthorized` — `passwordActual` mismatch, or internal-auth header failure (existing contract).
- `404 Not Found` — no user at `{email}` (cookie outlived account deletion/rename edge case).
- `409 Conflict` — `emailNuevo` taken by a different account (`UserRepository.existsByEmail` check, excluding the caller's own current row).

### 4.3 `GET /api/usuarios/me/datos/exportar` — new (Portabilidad)

- Auth: identical to `GET /api/usuarios/me/datos` — valid cookie, no role restriction, no parameters accepted.
- Implementation: calls the **exact same** `UsuarioDatosController.agregarDatos(email)` aggregation already built for acceso — this endpoint adds no new downstream calls, no new internal endpoints, and no new failure modes beyond what acceso already has.
- Response `200 OK`:
  ```json
  {
    "formatoVersion": "1.0",
    "tipoSolicitud": "PORTABILIDAD_ARCO",
    "titular": "dueña@pyme.cl",
    "generadoEn": "2026-08-27T11:00:00",
    "datos": { "...": "misma forma exacta que UsuarioDatosDTO (§3.1 de arco-acceso-personal-data.md)" }
  }
  ```
  Response headers additionally include `Content-Disposition: attachment; filename="smartlogix-mis-datos-{yyyyMMdd}.json"`, making the response a downloadable file from a browser rather than an inline API payload — the concrete, minimal thing that distinguishes "portabilidad" from "acceso" here, since the underlying data and its structure are identical by design.
- Status codes: same as `GET /api/usuarios/me/datos` (`200` including the empty-history case, `401` for missing/invalid cookie). `datos.estadoAgregacion` can legitimately be `"PARCIAL"`/`"ERROR"` under a downstream outage — this endpoint does **not** upgrade or hide that state to make the export look more complete than it is; a partial export is still labeled as such (same fault-tolerance posture as acceso, not a new decision).

## 5. Data model changes

| Service | Change |
|---|---|
| `auth_db` (`auth-service`) | **No schema change.** `users.email` already exists, `unique`, `not null`. This design only adds an `UPDATE` code path (`AuthService.rectificarEmail`) using the existing column and the existing `UserRepository.existsByEmail`/`findByEmail` methods — no new repository method needed. |
| `pedidos_db`, `envios_db` | **No schema change, no code change.** Portabilidad reuses existing `findByUserEmail`/`findByPedidoIdIn`; rectificación does not touch these databases at all (§8 open question explains why). |

No cross-service joins/foreign keys introduced, consistent with database-per-service.

## 6. Design pattern fit

Both features **extend** patterns already audited in this codebase; neither introduces a new one:

1. **BFF/WebHub aggregation** (`DashboardController` → `UsuarioDatosController` precedent) — `RectificacionController` and the new `exportar` method live in `api-gateway`, reached via the existing `usuariosRoute()` loopback route, signing outbound calls via the same `InternalTokenSigner.exchangeFilter()`. Portabilidad is the most literal possible extension of this pattern: it calls the *same private method* as the existing acceso controller, not a re-implementation.
2. **Repository pattern** — no new repository methods needed for either feature (`existsByEmail`/`findByEmail` already exist on `UserRepository`).
3. **The internal-service-auth HMAC pattern** (`internal-service-auth.md`) — the new `PUT /auth/interno/usuarios/{email}` reuses the *exact same* filter, secret, and issuer-allowlist rule as the existing `GET` on the same prefix. No new signing mechanism, no new allowlist entry (the filter already gates the whole prefix, not per-method).
4. **The `AuthController.login` cookie-rotation shape** — rectificación's cookie-setting code in `api-gateway` (`ResponseCookie.from(COOKIE_NAME, newToken)...`) is a direct structural copy of `AuthController.login`'s existing cookie-building code, just triggered from a different service and event. This is the one place this design asks the developer to duplicate ~10 lines of cookie-building logic rather than invent a shared helper across two services with no existing shared-module precedent — consistent with this codebase's already-accepted stance on cross-service duplication (`internal-service-auth.md` §7).

No new GoF pattern (Saga, Strategy, Observer, etc.) is introduced for either right in this cycle's scope.

## 7. Security requirements (OWASP Top 10 2021)

### A01 — Broken Access Control
- Identity for **both** new endpoints is derived exclusively from the verified `sl_jwt` cookie, server-side, at `api-gateway` — neither endpoint accepts a user/email identifier as a client-supplied parameter at any hop (mirrors the acceso design's core anti-IDOR property exactly). `{email}` in the internal `PUT` path is always the gateway's own server-derived value, never proxied from the request body.
- **The critical IDOR check for rectificación specifically**: a user must not be able to change *another* user's email by any input manipulation. Since there is no email/id parameter anywhere in the public request (`PUT /api/usuarios/me/email`'s body contains only `emailNuevo`/`passwordActual`, never "whose account"), there is no field to tamper with — the ownership check *is* the cookie-derived identity, same as acceso. This must be verified by test, not just asserted (§9, criterion 5).
- Same internal-only routing exclusion the acceso design already established for `/api/auth/interno/**` applies unchanged and covers the new `PUT` on that prefix for free (§4).

### A02 — Cryptographic Failures
- `passwordActual` travels: browser → `api-gateway` (over the existing CORS/cookie-authenticated channel, unchanged) → `auth-service` (over the internal HMAC-authenticated channel). This is the **first time a plaintext password crosses the internal service boundary** in this codebase — previously `auth-service` never received internal calls at all (`internal-service-auth.md` §2). This is necessary (only `auth-service` can verify it against the BCrypt hash) and is not a new trust boundary crossing beyond what internal-service-auth already protects, but it raises the bar on **never logging it** (A09) higher than for the read-only acceso endpoints, since this is the first internal payload containing a live credential.
- The new internal response DTO carries a **fresh JWT** (`token` field, §4.2) — also a first (no prior internal DTO carried a bearer credential). Must never be logged in full; if ever logged for debugging, mask it the same way Flow tokens are already masked (`token.substring(0,8)+"***"`).
- BCrypt verification reuses the existing `PasswordEncoder` bean (`BCryptPasswordEncoder(12)`) — no new hashing scheme.

### A03 — Injection
N/A for both features. `existsByEmail`/`findByEmail`/the `UPDATE` via JPA `save()` are parameterized Spring Data operations; no native SQL, no string concatenation of user input into a query.

### A04 — Insecure Design (abuse cases — the load-bearing section for these two mutation endpoints)
- **Password-guessing oracle (rectificación, new risk class vs. the read-only acceso endpoint):** an attacker holding a stolen-but-valid `sl_jwt` cookie (a materially privileged starting position — httpOnly defeats casual XSS token theft) could otherwise use this endpoint's distinguishable `401` (wrong password) vs. `200` (right password) as a password oracle. Mitigated by `rectificacionRateLimiter` (5/60s, §4.1) — same posture, same numeric limit, as `loginRateLimiter`, applied for the same reason.
- **Double-submit / lost-response retry:** if `auth-service` successfully updates the email and mints a new token, but the HTTP response back to `api-gateway` (or to the browser) is lost, a client retry will resend the **old** cookie (the new one was never received) — `auth-service`'s internal lookup by the old email will now `404` (the row's email column already changed). This is the correct, safe failure mode: **no double-application, no security bypass, just a `404` requiring the user to refresh/re-login** — documented as expected behavior (§9, acceptance criterion), not silently left as an unhandled edge case.
- **Concurrent-request race (two simultaneous rectificación calls with the same current cookie, different `emailNuevo` targets, or the same target):** `AuthService.rectificarEmail` must catch `DataIntegrityViolationException` from the underlying unique-constraint-backed `save()` and translate it to the standard `409` — the existing `existsByEmail`-then-`save()` sequence has a TOCTOU gap under concurrency; the DB's unique index is the actual safety net, and the exception path must not leak as an unhandled `500`. This directly mirrors the exact class of bug `internal-service-auth.md`'s own "QA feedback (iteration 1)" caught once already (an uncaught exception surfacing as a raw `500` instead of a clean error status) — this design must not reintroduce that failure shape for a different exception type.
- **Replay of the internal HMAC-signed request** (captured within its 30s TTL): same accepted residual risk already documented and accepted in `internal-service-auth.md` §8 A04 — not a new risk introduced by this feature, the mutation itself (email change) is not separately idempotent-safe against a true replay within the window (a replayed rectificación request would re-apply the same change, which is a no-op the second time since the email is already changed — falls into the `404`-on-retry case above, not a new state).
- **Portabilidad has no new abuse case beyond acceso's** — it is a pure read using the same aggregation; no double-submit or race concern (§9, acceso design A04 reasoning applies unchanged).

### A05 — Security Misconfiguration
- No new env vars, no new ports, no new actuator exposure. `rectificacionRateLimiter` is new Resilience4j *configuration* (an `application.properties` entry in `auth-service`, mirroring `loginRateLimiter`'s existing three lines) — not a new dependency or infrastructure component.

### A07 — Auth Failures
- Rectificación **rotates** the session (new JWT minted with the corrected `sub`) — this is new behavior this codebase hasn't had before (login is the only prior place a token was minted). The old token is not actively revoked (stateless JWT, no deny-list infrastructure exists — same accepted limitation as `logout`'s existing behavior, per the acceso design's own note that logout "cannot revoke the token itself"). This means a **stolen old token remains technically valid** (signature-wise) until its natural expiry even after a legitimate rectificación — it just no longer resolves to a *findable* account under its embedded (now-stale) email for any endpoint that looks the user up by email (which is every current lookup path). This is a real, accepted residual risk, stated explicitly rather than glossed over — same category of tradeoff already accepted for logout.

### A08 — Software/Data Integrity
- No deserialization of untrusted external input. The internal HMAC signature (unchanged mechanism, `internal-service-auth.md`) is what gives the `PUT /auth/interno/usuarios/{email}` call its integrity guarantee — same as every other internal call.

### A09 — Logging/Monitoring
- **Must log:** one audit line per rectificación attempt — success (`"[ARCO+] Rectificación de email — anterior=X, nuevo=Y"`, both emails, since neither is a secret and this is exactly the accountability record Ley 21.719 requires for a rectificación being exercised) and rejected attempts (wrong password, conflict) with email + reason but **not** the attempted password. One audit line per portabilidad request (`"[ARCO+] Solicitud de portabilidad — email=X"`), distinct from the existing acceso audit line, so the two legally-distinct rights remain separately auditable in logs.
- **Must NOT log:** `passwordActual` (either endpoint receiving it), the new/old JWT token value in full (mask if ever logged, per A02), and — matching the acceso design's existing rule — never the full aggregated payload for the portabilidad export.

### A10 — SSRF
N/A for both — same fixed, operator-configured internal URLs as the acceso design, no user-influenced host/scheme.

## 8. Chilean compliance touchpoints

This design implements `COMPLIANCE_CL.md` §4.2's "Rectificación" and "Portabilidad" rows — once merged, those two rows should move to `✅ implementado`, citing the merging commit/PR (per §5 of that document). The "Cancelación" and "Oposición" rows should **not** be marked done and should instead be updated with a short note pointing to §9 of this document (deferred, scoped follow-up) and, for Oposición specifically, a note reflecting the actual finding: *"sin tratamiento opcional/interés legítimo identificado hoy (solo notificaciones transaccionales) — revisar si se agrega marketing/analytics/perfilamiento."* This is a more accurate status than leaving a bare `❌ pendiente`, which currently implies "an endpoint is owed" rather than "there is currently nothing to build."

- **Ley 21.719, derecho de rectificación**: satisfied for the one field that is actually the data subject's own correctable identifying data (`email`). The known limitation — historical `Pedido.userEmail` values are not retroactively corrected — is a real completeness gap relative to a maximalist reading of "all inaccurate data about me held anywhere," but is defensible as: (a) those records represent a transaction as it existed at the time (an accurate historical fact, not itself "inaccurate" merely because the account's current login email later changed), and (b) propagating identity changes across service boundaries is exactly the kind of larger cross-service consistency problem flagged for cancelación's own future design, not something to bolt onto this smaller change. Documented explicitly, not silently accepted (§9).
- **Ley 21.719, derecho de portabilidad**: satisfied — JSON is a structured, commonly-used, machine-readable format; the `Content-Disposition` framing plus explicit `formatoVersion`/`tipoSolicitud` envelope makes the export self-describing as a portability artifact distinct from a routine API response, which is the concrete, minimal thing this right adds over acceso.
- **§4.4 Logging y auditoría**: both new audit log lines (§7 A09) are direct improvements to the same "eventos de seguridad relevantes logueados" checklist row the acceso design already improved.
- **Retention (§4.4)**: N/A — no new category of persisted data, no new retention question (the `email` column already exists and is already retained under whatever policy governs the `users` table today).
- **Cancelación / Oposición**: explicitly **not** advanced by this design (§2, §9.1, §9.2) — flagged, not silently deferred.

## 9. Acceptance criteria

### Rectificación (`PUT /api/usuarios/me/email`)
1. Logged in as user A with correct `passwordActual` and a valid, unused `emailNuevo`: `200`, response `email == emailNuevo`, `Set-Cookie` present with a new `sl_jwt` value different from the request's cookie, and a subsequent `GET /api/session` (or any protected call) using the *new* cookie resolves to `emailNuevo`.
2. Same as #1 but `passwordActual` is wrong: `401 "Contraseña actual incorrecta"`, `users.email` in `auth_db` is **unchanged** (verify via a follow-up login with the old credentials still succeeding), no `Set-Cookie` header in the response.
3. `emailNuevo` already belongs to a different existing account: `409 "Este email ya está registrado"`, no change to either account's row.
4. `emailNuevo` malformed (e.g., `"not-an-email"`) or missing: `400`, standard Bean Validation error shape, no downstream call made (verify via absence of any internal-auth log line for the attempt).
5. **IDOR abuse case (critical):** with a valid cookie for user A, there is no request body field, query parameter, or header that changes *whose* account is rectified — confirm the endpoint's contract has no `email`/`id`/`usuario` field referring to the target account anywhere but the cookie; attempting to add such a field client-side (e.g. `{"email":"B@x.cl","emailNuevo":"...","passwordActual":"..."}`) has no effect (server ignores unknown fields, per existing Jackson defaults) and A's own account is the one modified, never B's.
6. No `sl_jwt` cookie: `401 "Token requerido"`, request never reaches `RectificacionController`'s body (no internal call, no DB write).
7. **Rate limiting abuse case:** 6 rapid attempts with wrong `passwordActual` within 60 seconds → the 6th returns `429`, not a 6th `401` (proves the password-oracle mitigation is wired, not just documented).
8. **Race/idempotency abuse case:** two concurrent requests from the same valid cookie both targeting the same `emailNuevo` (correct password on both) — exactly one succeeds `200`, the other returns `409` (not `500`), and `users.email` ends up as `emailNuevo` exactly once (no duplicate rows, no unhandled exception in logs).
9. **Internal-endpoint IDOR check:** directly attempting `PUT /api/auth/interno/usuarios/{any-email}` through the gateway → `404` (no matching route), same as the existing acceso `GET` variant's already-verified behavior on this prefix.
10. **Internal-endpoint bypass check:** from within `smartlogix-net`, `auth-service:8081/auth/interno/usuarios/{any-email}` (PUT) with no internal-auth headers → `401`, same as the `GET` variant.
11. Log output for a successful rectificación contains exactly the audit line with both emails and **no** `passwordActual` value or full JWT anywhere in the request's trace.
12. Full smoke test: register, login, rectify email, log out, log back in with the **new** email and original password → succeeds; log-in attempt with the **old** email → fails (account no longer exists under that email).

### Portabilidad (`GET /api/usuarios/me/datos/exportar`)
13. Logged in as user A with existing pedidos/envíos: `200`, `Content-Disposition: attachment; filename="smartlogix-mis-datos-{date}.json"` header present, `datos` field byte-for-byte structurally identical (same fields/values) to what `GET /api/usuarios/me/datos` returns for the same user in the same moment, `formatoVersion` and `tipoSolicitud` present.
14. No `sl_jwt` cookie: `401`, identical to acceso's existing behavior.
15. **IDOR abuse case:** no client-supplied identifier of any kind influences whose data is exported (same as acceso's own criterion — no parameter exists to tamper with).
16. Stop `ms-pedidos`: export still returns `200` with `datos.estadoAgregacion` = `"PARCIAL"`/`"ERROR"` (never silently `"OK"` with missing data framed as a complete export) — proves this endpoint doesn't misrepresent a degraded aggregation as a complete portable copy.
17. Log output shows a distinct `"[ARCO+] Solicitud de portabilidad"` line (not the acceso line) for a call to this endpoint, and no log line contains the exported payload contents.
18. Regression: `GET /api/usuarios/me/datos` (unchanged endpoint) continues to behave exactly as before this change.

## 10. Open questions

1. **Historical `Pedido.userEmail` is not updated on rectificación (§8).** After a user changes their login email, `GET /api/usuarios/me/datos`/the portability export will show *fewer* historical pedidos linked to the new email (since `ms-pedidos.findByUserEmail` matches on the current login email, and old pedidos still carry the pre-rectification email) — a real, user-visible side effect of this cycle's scope decision, not a hidden bug. Options for a future cycle: (a) propagate the corrected email to `ms-pedidos`/`ms-envios`/`ms-pagos` as part of rectificación (turns this into a small Saga-shaped, cross-service operation), or (b) switch the acceso/portabilidad aggregation to key off `Pedido.userId` instead of email (if `userId` is reliably the account's own stable ID — needs verification, since the acceso design's own §9 point 3 already flagged that `Pedido.userEmail` is client-supplied at order-creation time and not guaranteed to be the creator's own login email in the first place, which may mean `userId` has the same reliability gap). Needs a decision before this is presented as "rectificación is fully solved," not before this narrower cycle ships.
2. **Should cancelación be pulled forward given `COMPLIANCE_CL.md` §6 names it (alongside acceso) as one of the two most commonly audited rights?** This design explicitly does **not** implement it despite that recommendation (§2) — surfacing this tension for the human/orchestrator to weigh explicitly (regulatory-audit priority vs. review-safety of the diff size) rather than silently picking one side.
3. **Handoff notes for a future Cancelación design cycle:**
   - **Hard delete vs. anonymize (the central tension):** recommend anonymize-in-place for `pedidos_db`/`envios_db`/`pagos_db` (replace `userEmail`/`clienteNombre`/`destino`/address-shaped fields with a fixed anonymized marker, e.g. `"USUARIO_ELIMINADO"`, while retaining `total`/`status`/`productoId`/dates/Flow transaction references for whatever accounting/audit retention obligation applies) rather than deleting rows outright, given the tension between erasure and legitimate transactional/financial retention. `auth-service`'s own account row is the one place a fuller delete (or a "deactivated + anonymized email" soft-delete) is more defensible, since the account itself has no independent retention basis once cancelación is granted and no pending obligation remains.
   - **Blocking condition:** should cancelación be refused/deferred while the account has an active, unfulfilled pedido (e.g. `PENDIENTE_PAGO`, `PENDIENTE`, `EN_ENVIO`)? Needs a product/legal decision, not an engineering default.
   - **Coordination shape:** this is a genuine multi-service write operation, but note it does **not** cleanly fit the existing `SagaOrchestrator`'s forward-step/compensation shape (there's no meaningful "undo" of an anonymization once granted — the correct failure semantics are "retry until every service confirms anonymization succeeded," not "roll back"). A future design should decide whether to reuse `SagaOrchestrator`'s machinery anyway (treating "retry to completion" as the compensation-equivalent) or introduce a different reliability mechanism (e.g. an outbox/retry table) — flagged as a genuinely open architectural question, not a given.
   - **Session revocation:** cancelación should end the account's active session(s) immediately, which the current stateless-JWT design has no mechanism for (same gap noted in §7 A07 for rectificación, at higher stakes here). A future design must decide whether this finally justifies a short-lived revocation/deny-list store (e.g. Redis) or an accepted "old tokens remain valid until natural expiry post-cancelación" residual risk.
   - **Re-authentication/confirmation UX**: at minimum, current-password re-entry (matching rectificación's pattern); likely also a distinct, unambiguous "are you sure" step given irreversibility, to be designed then, not assumed here.
4. **Oposición reframing**: recommend `COMPLIANCE_CL.md` §4.2's row be updated to reflect "no optional processing exists to object to today" rather than left as a bare `❌ pendiente` implying missing engineering work. If/when SmartLogix adds marketing email, analytics, or profiling-based recommendations, that is the trigger to design an actual opt-out mechanism (likely: a boolean preference column on `User`, checked by whichever service performs that optional processing) — not designed speculatively here.
5. **Frontend gap accumulating across two ARCO+ cycles now.** Neither acceso nor this design proposes a UI. A "Mis datos" account page (view + rectify + export) is cheap relative to the backend work already done and would make these rights *practically*, not just technically, exercisable by an end user — worth a dedicated small frontend design before treating ARCO+ as user-facing-complete.
6. **Rate limiting is global, not per-account** (`rectificacionRateLimiter`, same as the pre-existing `loginRateLimiter`) — a targeted attacker could still exhaust the shared 5/60s budget across unrelated users' legitimate rectificación attempts as an availability side-channel. Inherited from existing precedent, not a new risk introduced here, but worth flagging since this is the first time this shared-bucket rate limiter design is reused for a second endpoint — if it becomes a real operational problem, per-account/per-IP limiting would need infrastructure this stack doesn't have today (matches the note already in `internal-service-auth.md` about no Redis in this stack).
