# ARCO+ Derecho de Acceso — `GET /api/usuarios/me/datos`

## 1. Summary

`docs/COMPLIANCE_CL.md` §4.2 lists the **derecho de acceso** (Ley 21.719, en vigor 2026-12-01) as `❌ pendiente`, and names the shape of the fix: a single BFF endpoint, `GET /api/usuarios/me/datos`, aggregating the calling user's own personal data from `auth-service` (account) + `ms-pedidos` (order history) + `ms-envios` (shipping/tracking) — analogous to the existing `/api/dashboard` aggregation.

This design adds exactly that endpoint, but reading the actual code surfaced two load-bearing gaps that shape most of the design, not just its edges:

1. **`ms-pedidos.Pedido` already has a `userEmail` column, but `OrderRepository` has no query method to filter by it** (`findByUserId`/`findByStatus` only) — confirming the task's hypothesis. A new `findByUserEmail` method is required.
2. **`ms-envios.Envio` has *no* user-identity linkage at all** — only a logical FK to `pedidoId` (no `userId`/`userEmail` column, "FK lógica — Database-per-Service" per the entity's own comment). Scoping envíos to "my own" therefore requires a **two-step lookup**: get the caller's pedido IDs from `ms-pedidos` first, then ask `ms-envios` for shipments whose `pedidoId` is in that set. This is why the aggregation cannot be a single flat 3-way parallel fan-out like `/api/dashboard` — envíos genuinely depend on the pedidos result.

The most important finding, however, is an **access-control trap that this design must close by construction, not just by convention**: `api-gateway`'s `InternalTokenIssuerFilter` already signs *every* request it proxies to `ms-pedidos`/`ms-envios` as `X-Internal-Service: api-gateway`, and each service's `InternalAuthFilter` already trusts `api-gateway` for "everything else" (any path that isn't one of the few specially-locked-down ones). Internal-service-auth answers "*which service* is calling," not "*did the browser tamper with the user identifier in the URL*." If the new per-user lookup endpoints (`GET /pedidos/interno/por-email/{email}`, `GET /envios/interno/por-pedidos`, `GET /auth/interno/usuarios/{email}`) were reachable through the existing public wildcard gateway routes (`/api/pedidos/**`, `/api/envios/**`, `/api/auth/**`), **any authenticated user could read any other user's order history and shipping data simply by editing the email in the URL** — internal-service-auth would happily let that request through, because it *is* genuinely coming from `api-gateway`. This design therefore excludes these new internal-only paths from the public route predicates entirely (§3, §6 A01) so they are reachable *only* via `api-gateway`'s own server-side code, which always derives the identity from the verified JWT cookie — never from client input.

## 2. Affected services

| Service | Change | Why |
|---|---|---|
| `api-gateway` | New `UsuarioDatosController` (`GET /usuarios/me/datos`), new DTOs, new `usuariosRoute()` in `GatewayConfig` (same shape as `dashboardRoute`/`sessionRoute`); **route-predicate exclusion** added to `authRoute()`, `pedidosRoute()`, `enviosRoute()` so `/api/{auth,pedidos,envios}/interno/**` never proxy through the public wildcard. Reuses existing `InternalTokenSigner`/`JwtUtil`. | Owns the BFF/aggregation pattern (`DashboardController` precedent) and the routing surface that must be locked down. |
| `auth-service` | **First-time addition** of `InternalAuthFilter` (mirroring `ms-pedidos`/`ms-envios`'s existing filter) + new internal-only endpoint `GET /auth/interno/usuarios/{email}` + new `UsuarioInternoDTO` (no password field) + new nullable `created_at` column on `User`. New `INTERNAL_SERVICE_SECRET` wiring in `docker-compose.yml` (currently absent for this service). | `auth-service` has never been an internal-auth *receiver* before (`internal-service-auth.md` explicitly scoped it out — "nothing calls it internally"); this is the first caller. |
| `ms-pedidos` | New repository method `OrderRepository.findByUserEmail(String email)`; new internal-only endpoint `GET /pedidos/interno/por-email/{email}` returning a redacted `PedidoDatosDTO` list. No change to the existing `InternalAuthFilter` code — the new path already falls into its default "everything else → `api-gateway` only" bucket. | Closes the query-method gap named in the task; the endpoint is what the compliance doc's `GET /pedidos` (all orders, unfiltered) cannot safely be reused for. |
| `ms-envios` | New repository method `EnvioRepository.findByPedidoIdIn(List<Long> pedidoIds)`; new internal-only endpoint `GET /envios/interno/por-pedidos?pedidoIds=1,2,3`. Same "no filter code change needed" note as `ms-pedidos`. | Only way to scope shipments to "my" data given `Envio` has no user-identity column of its own. |
| `ms-inventario`, `ms-pagos`, `notification-service` | **No change.** | `ms-pagos` holds transaction data (Flow reference/status/amount) that is arguably in scope for a *complete* derecho-de-acceso disclosure per `COMPLIANCE_CL.md` §2, but the compliance doc's own suggested endpoint composition (auth + pedidos + envios) and the task's explicit scope omit it — flagged as a gap in §7 and §9, not silently dropped. |
| `frontend` | **No change proposed in this design** — the task specifies the API contract only. A "Mis datos" UI page is a natural, cheap follow-up but is not designed here — see §9. | Out of the task's explicit scope; flagged so it isn't assumed done. |
| `docker-compose.yml` | Add `INTERNAL_SERVICE_SECRET` to `auth-service`'s `environment:` block. **Recommended (not strictly required by the literal task, but directly load-bearing for this design's own security — see §6 A01/A05):** remove `auth-service`'s host port mapping (`"8081:8081"`), matching the precedent already applied to the other 5 services in the prior OWASP audit. | `auth-service` is the only one of the six relevant services still reachable directly from the host, which matters *specifically* because this design adds a new personal-data endpoint to it. |

**Coordination classification: Facade/BFF aggregation (matches `/api/dashboard`'s existing classification), not a Saga.** This is a pure read with no multi-step write transaction and nothing to compensate. `api-gateway` is the single coordinator (`UsuarioDatosController`) fanning out to three subsystems on behalf of one caller — the textbook Facade/BFF shape already used for `/api/dashboard`. It deviates from `/api/dashboard`'s *fully parallel* 3-way fan-out in one specific way, justified by the data shape finding above: `ms-envios` must be queried in a second wave, after `ms-pedidos` returns the caller's pedido IDs, because `Envio` has no independent user-identity column to query by directly. `auth-service` and `ms-pedidos` are queried in parallel (`Mono.zip`), then `ms-envios` is queried once the pedido IDs are known (`flatMap`).

## 3. API contract

Convention: all endpoints require `Authorization` via the `sl_jwt` httpOnly cookie (per `jwt-httponly-cookie-migration.md`) except `/api/auth/*` and `/api/pagos/webhook/flow`. This design adds one more convention layer: **`/api/auth/interno/**`, `/api/pedidos/interno/**`, and `/api/envios/interno/**` are not routable through the gateway at all** — no predicate matches them, so a direct request to any of these paths returns the gateway's default `404`, regardless of JWT validity. They exist purely as container-to-container endpoints called by `api-gateway`'s own Java code.

### 3.1 `GET /api/usuarios/me/datos` — **new**

- Auth: any valid, unexpired `sl_jwt` cookie. **No role restriction** — this is a data-subject right tied to identity, not a staff/admin function, consistent with the single-tenant/shared-staff-pool model already accepted in `COMPLIANCE_CL.md` §4.1. No user identifier is ever accepted as a path, query, or body parameter — the only identity used is the one extracted server-side from the verified cookie (mirrors `GET /api/session`'s existing precedent exactly).
- Request: no body, no parameters.
- Response `200 OK`:
  ```json
  {
    "generadoEn": "2026-08-27T10:15:00",
    "estadoAgregacion": "OK",
    "cuenta": {
      "email": "dueña@pyme.cl",
      "role": "ROLE_USER",
      "cuentaCreadaEn": "2026-01-15T09:00:00"
    },
    "pedidos": [
      {
        "id": 42,
        "clienteNombre": "Comercial Andina Ltda.",
        "total": 15990.0,
        "status": "ENTREGADO",
        "tipoPedido": "NACIONAL",
        "destino": "Valparaíso",
        "productoId": 7,
        "cantidad": 3,
        "creadoEn": "2026-02-01T12:00:00"
      }
    ],
    "envios": [
      {
        "id": 17,
        "pedidoId": 42,
        "status": "ENTREGADO",
        "tipoEnvio": "TERRESTRE",
        "transportista": "Transportes del Sur",
        "destino": "Valparaíso",
        "fechaEstimadaEntrega": "2026-02-05T00:00:00",
        "creadoEn": "2026-02-01T12:05:00"
      }
    ]
  }
  ```
  - `estadoAgregacion`: `"OK"` (all three sources answered, including the legitimate case of zero pedidos/envíos), `"PARCIAL"` (one source unreachable/erroring), or `"ERROR"` (all three unreachable) — same three-state convention as `DashboardController.determinarEstado`. **Required so a downstream outage is never silently indistinguishable from "you have no data."**
  - `cuenta` is `null` (not an empty object) if `auth-service`'s internal lookup fails or the email from a still-valid JWT no longer resolves to an account (edge case: stateless JWT outlives an account change).
  - **Explicitly excluded from every response, at the DTO level (not just by omission of a value):** the password hash, the raw `Pedido.userId`/`Envio` internal foreign keys beyond `pedidoId` (which is meaningful to the user as "which of my orders"), and `Pedido.observaciones` when it contains internal operational notes not meaningful to the data subject (see §9 open question — may need light redaction if that field starts carrying non-user-relevant staff notes).
- Status codes:
  - `200 OK` — as above, including the legitimate empty-history case.
  - `401 Unauthorized` — missing/invalid/expired `sl_jwt` cookie, same `AuthFilter` behavior (`"Token requerido"` / `"Token inválido"`) as every other protected route. In normal operation `AuthFilter` already blocks this before the controller runs; the controller re-validates once more (mirrors `SessionController`'s existing redundant check) purely to extract the email locally.

### 3.2 `GET /auth/interno/usuarios/{email}` — **new, internal-only**

- Not reachable via any `/api/**` gateway route (excluded from `authRoute()`'s predicate — see §5).
- Auth: internal-service HMAC headers required (`InternalAuthFilter`, new to `auth-service`); allowlist: `api-gateway` only.
- Response `200 OK`: `{ "email": "dueña@pyme.cl", "role": "ROLE_USER", "cuentaCreadaEn": "2026-01-15T09:00:00" }` — a dedicated `UsuarioInternoDTO`, **never** the `User` entity (which has a `password` field).
- `404 Not Found` — no user with that email (edge case noted above).
- `401`/`403` — per the existing `InternalAuthFilter` contract (`internal-service-auth.md` §5.3): missing headers, expired timestamp, bad signature → `401`; wrong issuer → `403`.

### 3.3 `GET /pedidos/interno/por-email/{email}` — **new, internal-only**

- Not reachable via `/api/pedidos/**` (excluded — see §5).
- Auth: internal HMAC headers; falls into `ms-pedidos`'s existing `InternalAuthFilter` default bucket ("everything else → `api-gateway` only") — **no filter code change needed**, only the route-exclusion in `GatewayConfig` and the new controller/repository method.
- Response `200 OK`: `List<PedidoDatosDTO>` (fields as in §3.1's `pedidos` array) — **empty list, not an error**, if the email has no pedidos.
- `401`/`403` — same as above.

### 3.4 `GET /envios/interno/por-pedidos?pedidoIds=1,2,3` — **new, internal-only**

- Not reachable via `/api/envios/**` (excluded — see §5).
- Auth: internal HMAC headers; same default-bucket allowlist as §3.3, no filter code change.
- `pedidoIds`: comma-separated `Long`s. Empty/absent → `200` with `[]` (api-gateway should skip the call entirely when the caller has zero pedidos, to avoid a pointless internal hop — see §4/§6 A10-adjacent efficiency note, not a security requirement).
- `400 Bad Request` — `pedidoIds` present but contains a non-numeric token (defensive input validation, consistent with the codebase's existing Bean-Validation-everywhere posture).
- Response `200 OK`: `List<EnvioDatosDTO>` (fields as in §3.1's `envios` array).
- `401`/`403` — same as above.

## 4. Data model changes

| Service | Change |
|---|---|
| `auth_db` (`auth-service`) | New nullable column `users.created_at` (`TIMESTAMP`), added via `@Column(name = "created_at") private LocalDateTime createdAt;` on `User` + a `@PrePersist` setting it on new registrations — same convention already used by `Pedido.creadoEn`/`Envio.creadoEn`. **`ddl-auto=update` (confirmed in `application.properties`, no Flyway/Liquibase in this repo) means existing rows get `NULL`** — pre-existing accounts will show `"cuentaCreadaEn": null` in the response. This is expected, documented behavior, not a bug (§8, criterion 8), given the no-migration-tooling constraint already accepted elsewhere in this codebase. |
| `pedidos_db` (`ms-pedidos`) | **No schema change.** `Pedido.userEmail` already exists; only a new `OrderRepository.findByUserEmail(String email)` derived-query method (code, not schema). |
| `envios_db` (`ms-envios`) | **No schema change.** Only a new `EnvioRepository.findByPedidoIdIn(List<Long> pedidoIds)` derived-query method. |

No cross-service joins or foreign keys are introduced — the two-step lookup (§1, §2) is done in application code in `api-gateway`, respecting database-per-service.

## 5. Design pattern fit

This **extends** two patterns already present and audited in this codebase; it introduces no new one:

1. **BFF/WebHub aggregation** (`DashboardController`) — `UsuarioDatosController` lives in `api-gateway`, is reached via a `localhost:8080` loopback route exactly like `dashboardRoute()`/`sessionRoute()`, and signs its own outbound internal calls via the existing `InternalTokenSigner.exchangeFilter()`. **Deliberate deviation from `DashboardController`'s pattern, justified by the data shape (§1):** `DashboardController` fans out to all three services fully in parallel because none of its three calls depend on each other's results. This design cannot do that for `ms-envios`, because `Envio` has no user-identity column — `ms-envios` must be queried *after* `ms-pedidos` returns the caller's pedido IDs (`Mono.zip(cuentaMono, pedidosMono).flatMap(... enviosClient...)`). This is a genuine dependency in the data, not an arbitrary stylistic choice, and should be called out in code review as intentional, not a missed optimization.
2. **Repository pattern** (`JpaRepository`, used by every service) — `findByUserEmail`/`findByPedidoIdIn` are ordinary Spring Data derived-query methods, identical in kind to the existing `findByUserId`/`findByStatus`/`findByPedidoId`. No new abstraction.

**No new GoF pattern is introduced.** In particular, no shared "internal auth filter library" is extracted even though `auth-service`'s new `InternalAuthFilter` is a near-verbatim copy of `ms-pedidos`'s/`ms-envios`'s existing ones — this mirrors the explicit precedent and rationale already recorded in `internal-service-auth.md` §7 (duplication accepted, no multi-module Maven reactor changes for a single cross-cutting filter).

## 6. Security requirements (OWASP Top 10 2021)

### A01 — Broken Access Control (the core of this design)

- **Identity is derived exclusively server-side** from the verified `sl_jwt` cookie (`JwtUtil.extractEmail`), exactly like `GET /api/session` — `GET /api/usuarios/me/datos` accepts no user/email/id parameter from the client, so there is no IDOR surface *on that endpoint itself*.
- **The real risk is the three new internal endpoints, and it is closed by routing, not by internal-service-auth alone.** `InternalTokenIssuerFilter` signs *every* gateway-proxied request to `ms-pedidos`/`ms-envios` as `X-Internal-Service: api-gateway`, and both services' existing `InternalAuthFilter.isAllowed()` already trusts `api-gateway` for any path outside their few specially-locked-down ones. If `GET /pedidos/interno/por-email/{email}` were reachable at `/api/pedidos/interno/por-email/{email}`, **any logged-in user could read any other user's order history** by editing the email in the URL — the request would sail through `AuthFilter` (valid JWT, any user), through `InternalTokenIssuerFilter` (auto-signs as `api-gateway`), and through `ms-pedidos`'s `InternalAuthFilter` (issuer `api-gateway` is allowed for "everything else"). **Internal-service-auth proves *which service* is calling, not *whether the browser tampered with the identifier in the URL* — it cannot and does not substitute for a route-level access control here.** This design's actual fix: `GatewayConfig`'s `authRoute()`/`pedidosRoute()`/`enviosRoute()` path predicates must each be changed to exclude their respective `interno/**` sub-path (e.g. `path("/api/pedidos/**").and(RequestPredicates.not(path("/api/pedidos/interno/**")))`), so these paths **404 at the gateway** for any client-originated request and are reachable *only* via `UsuarioDatosController`'s own direct `WebClient` calls to the container hostnames, which always carry the gateway's own server-derived email.
- **`auth-service`'s published host port (`8081:8081`) makes the gateway-route exclusion necessary but not sufficient for that specific service.** Unlike the other 5 services (closed in the prior OWASP audit specifically to prevent bypassing the gateway), `auth-service` is directly reachable from the host today. This means the new `InternalAuthFilter` on `auth-service` is a **hard requirement, not defense-in-depth** — without it, `curl http://<host>:8081/auth/interno/usuarios/{any-email}` would leak any account's email/role/creation date to anyone who can reach the host network at all, with zero JWT and zero internal-auth check. Recommend closing this port in the same change (§2) — it is the same one-line fix already applied to the other 5 services, and leaving it open while adding a new personal-data endpoint to that exact service would be knowingly reintroducing the same finding the prior audit fixed elsewhere.
- No new role/tenant check is needed beyond identity extraction — consistent with `COMPLIANCE_CL.md` §4.1's confirmed single-tenant model (no `tenant_id`), this endpoint's "ownership" check *is* the email match, and that match happens entirely server-side.

### A02 — Cryptographic Failures

- The password hash must **never** appear in any payload this feature produces. `auth-service`'s new internal endpoint must return a dedicated `UsuarioInternoDTO` (`email`, `role`, `cuentaCreadaEn`) — **not** the `User` entity, which carries `password` and would serialize it via Lombok `@Data` if returned directly. This is a concrete implementation instruction, not generic advice: reusing `User` as a response type is the specific mistake to avoid here.
- No payment/card data is touched (`ms-pagos` explicitly out of scope this iteration — §2, §7).
- No new secrets beyond wiring the already-existing `INTERNAL_SERVICE_SECRET` into `auth-service` (§2) — same value, same handling convention (env var only, no default, fails closed on blank — see A04 below for the specific bug this must not reintroduce).

### A03 — Injection

N/A. `findByUserEmail`/`findByPedidoIdIn` are Spring Data derived-query methods (parameterized automatically, no native/native-native SQL). The `pedidoIds` query parameter is parsed as a list of `Long` via `Long.parseLong` per token (rejecting non-numeric input with `400`, §3.4) — never concatenated into a query string.

### A04 — Insecure Design (abuse cases)

- **Retry/repeat calls:** idempotent by nature — this is a pure read with no side effects. No double-submit or duplicate-write concern exists (unlike the Saga/webhook flows this codebase also has).
- **Race condition:** none introduced — no write, no compensation, no ordering dependency between concurrent callers (each call only reads the *current* state for *one* user).
- **Partial downstream failure must not be silently mistaken for "no data":** if `ms-pedidos` or `ms-envios` is down, `estadoAgregacion` must reflect `"PARCIAL"`/`"ERROR"`, not `"OK"` with an empty array — this is the one meaningful abuse-adjacent design requirement for a read-only aggregation endpoint (§3.1, §8 criterion 7).
- **Must not reintroduce the exact bug QA already caught once in this codebase:** `internal-service-auth.md`'s own "QA feedback (iteration 1)" section documents that a blank `INTERNAL_SERVICE_SECRET` previously caused an unhandled `IllegalArgumentException: Empty key` (raw `500`) instead of a clean `401`, across all 6 services that had the filter at the time. `auth-service`'s **new** `InternalAuthFilter`/signing code must be implemented with the *fixed* pattern from day one (fail-fast `@PostConstruct` check on a blank secret, exactly as `ms-pedidos`'s current `InternalAuthFilter.validarConfiguracion()` already does) — not the pre-fix pattern.

### A05 — Security Misconfiguration

- New env var wiring: `INTERNAL_SERVICE_SECRET` must be added to `auth-service`'s `environment:` block in `docker-compose.yml` (currently absent — confirmed by reading the file). No new value, no new secret, just extending an existing one to a sixth service.
- **Recommended:** remove `auth-service`'s `ports: ["8081:8081"]` mapping (§2, A01) — no new exposed ports are being *added*, but an existing one becomes higher-priority to close given this change.
- No new actuator endpoints exposed by this design.
- New `api-gateway` WebClient base URLs (`auth.service.url`, reusing existing `pedidos.service.url`/`envios.service.url` patterns from `DashboardController`) follow the existing `@Value("${x.service.url:http://service:port}")` convention with an in-code default — no new required env var strictly, though adding an explicit `AUTH_URL` to `docker-compose.yml` for symmetry with `INVENTARIO_URL`/`PEDIDOS_URL`/`ENVIOS_URL`/`PAGOS_URL` (already present for `api-gateway`) is a reasonable consistency improvement.

### A07 — Auth Failures

No change to JWT issuance, validation, or session lifecycle. This feature only *reads* the identity `AuthFilter` already validated; it does not touch login/logout/expiration/cookie handling.

### A08 — Software/Data Integrity

- No deserialization of untrusted external input — the only client-influenced value that ever reaches a downstream service in this feature is the calling user's own email, extracted server-side from a signature-verified JWT, never taken from a request path/query/body at the point where it's forwarded internally.
- The three new internal calls carry the standard HMAC signature (`X-Internal-Service`/`X-Internal-Timestamp`/`X-Internal-Signature`), signed by the same `InternalTokenSigner` `api-gateway` already uses for `DashboardController` — no new signing mechanism, no new trust boundary shape.

### A09 — Logging/Monitoring

- **Must log:** each call to `GET /api/usuarios/me/datos` (email, timestamp) as an access-request audit event — this is exactly the "accountability" evidence `COMPLIANCE_CL.md` §3 calls for regarding ARCO+ requests, and the kind of event a controller should be able to point to if asked "can you show when a data subject exercised their right of access." Also log rejected internal-auth attempts against the three new internal paths, using the existing rejection-logging convention (`InternalAuthFilter.reject(...)`, unchanged).
- **Must NOT log:** the aggregated personal-data payload itself (pedido details, shipping destinations) in application logs — logging "user X requested their data at time T" is sufficient; logging the *contents* of the disclosure would create a redundant, unnecessary copy of personal data in log storage, working against Ley 21.719's minimization principle even though the data was legitimately disclosed to its own owner. Also never log the password hash (unaffected, not newly at risk) or the JWT/cookie value (unchanged from existing convention).

### A10 — SSRF

N/A. All downstream base URLs (`auth-service:8081`, `ms-pedidos:8083`, `ms-envios:8084`) are fixed, operator-configured values, unchanged pattern from `DashboardController`. The only user-influenced value inserted into a URL is the caller's own already-verified email as a path segment on a fixed, hardcoded base URL — never a redirect target, never a host/scheme derived from user input.

## 7. Chilean compliance touchpoints

This design directly implements `COMPLIANCE_CL.md` §4.2's "Acceso" row — **once merged, that row should move from `❌ pendiente` to `✅ implementado`**, citing the merging commit/PR, per §5 of that document's own instructions to the reviewer.

- **What Ley 21.719's derecho de acceso requires, operationally, per `COMPLIANCE_CL.md` §3's own summary ("Derechos ARCO+: Acceso... transparencia, responsabilidad demostrable"):** a data subject must be able to (a) get confirmation of whether SmartLogix processes personal data about them, (b) see the actual data held, across the categories the company itself has inventoried in §2 (`auth-service`: email/credential; `ms-pedidos`: nombre cliente/email/destino/historial de compra; `ms-envios`: dirección de destino/estado de envío), and (c) obtain it without needing a manual, ad-hoc support request — a self-service, authenticated endpoint returning structured JSON satisfies (c) more concretely than, say, an email-a-DPO process would. This design satisfies (a)–(c) for the categories it covers. As with the rest of this document, this is engineering-level alignment, not a legal certification (per the document's own disclaimer).
- **Known, explicitly-flagged gap, not silently omitted:** `ms-pagos` (referencia de orden Flow, estado de pago, monto — §2) is **not** included in this iteration, matching the compliance doc's own suggested endpoint composition and the task's explicit scope. This means the disclosure is not yet fully complete relative to *every* category of personal/transactional data SmartLogix holds about a user — flagged as a fast-follow in §9, not a claim of full completeness today.
- **§4.4 Logging y auditoría:** this design's new audit log line (access-request email + timestamp, §6 A09) is a direct, concrete improvement to the "Eventos de seguridad relevantes logueados... Verificar por servicio" row.
- **Retention (§4.4):** N/A for new categories of data — the only new persisted field is `users.created_at`, which is metadata about data already retained, not a new category of personal data subject to a new retention question.
- **Rectificación / Cancelación / Oposición / Portabilidad (§4.2):** unaffected, remain `❌ pendiente`. Worth noting as a design observation (not a scope claim): because this endpoint's response is already structured, versioned JSON per user, a future **Portabilidad** endpoint could very plausibly reuse this same DTO shape almost as-is — flagged as a natural next step in §9, not proposed as in-scope here (matches this document's own "no speculative abstraction" ground rule).

## 8. Acceptance criteria

1. Logged in as user A, who has ≥1 pedido with `userEmail` matching A's login email and ≥1 envío linked to that pedido: `GET /api/usuarios/me/datos` returns `200`, `cuenta.email == A`, **no `password`/`contraseña` key present anywhere in the response** (assert key absence, not just a null value), `pedidos` contains exactly A's pedidos (matched by email) and no others, `envios` contains exactly the envíos whose `pedidoId` belongs to that pedido list and no others.
2. Logged in as user B with zero pedidos: `200`, `pedidos: []`, `envios: []`, `estadoAgregacion: "OK"` (a legitimate empty state, distinct from criterion 7's degraded state).
3. No `sl_jwt` cookie at all: `401 "Token requerido"`, and the request never reaches `UsuarioDatosController` (verify via absence of any downstream internal call/log for that request).
4. Expired or signature-tampered `sl_jwt` cookie: `401 "Token inválido"`.
5. **IDOR abuse case (the critical one) — with a valid cookie for user A, none of the following discloses user B's data:**
   a. `GET /api/usuarios/me/datos` never accepts nor is influenced by any client-supplied email/id parameter (already covered by #1 — there is no parameter to try).
   b. `GET /api/pedidos/interno/por-email/{B's email}` through the gateway → gateway returns `404` (no matching route), never a proxied `200` with B's pedidos.
   c. `GET /api/auth/interno/usuarios/{B's email}` through the gateway → `404`, same reasoning.
   d. `GET /api/envios/interno/por-pedidos?pedidoIds={B's pedido ids}` through the gateway → `404`, same reasoning.
   e. From within `smartlogix-net` (simulating a compromised container with no `INTERNAL_SERVICE_SECRET`), `ms-pedidos:8083/pedidos/interno/por-email/{any email}` directly → `401 "Autenticación interna requerida"`.
   f. Same as (e) against `auth-service:8081/auth/interno/usuarios/{any email}` → `401` (confirms `InternalAuthFilter` was actually wired into `auth-service`, not skipped).
   g. From the host machine, `curl http://localhost:8081/auth/interno/usuarios/{any email}` with no internal-auth headers → `401` (or connection refused, if the port-closing recommendation in §2/§6 is adopted) — never a `200` leaking account data.
6. Static/code-level check: `UsuarioInternoDTO` (the internal `auth-service` response type) has no `password` field declared at all — not merely `null` at runtime.
7. Stop/block `ms-pedidos` and call the endpoint as a user with existing pedidos/envíos: response is still `200` (graceful degradation, matching `DashboardController`'s existing fault-tolerance convention), `pedidos: []`, `envios: []` (since envíos can't be resolved without pedido IDs), and `estadoAgregacion` is `"PARCIAL"` or `"ERROR"` — **never `"OK"`** — so a genuine outage is never confused with "you have no orders."
8. A brand-new registration after this change shows a non-null `cuenta.cuentaCreadaEn`; an account that existed before this change shows `cuenta.cuentaCreadaEn: null` — documented as expected (no backfill), not treated as a bug.
9. Full `docker compose up --build` smoke test: register + login as a fresh user, create a pedido, let its Saga create the corresponding envío, then call `GET /api/usuarios/me/datos` and confirm both appear, correctly scoped to that user only.
10. Log output for one call to the endpoint contains exactly one audit line with the caller's email and a timestamp, and **no** log line anywhere in the request's trace contains the full response payload, the password hash, or the JWT/cookie value.
11. Regression: `GET /api/pedidos`, `GET /api/envios`, `GET /api/dashboard`, `POST /api/auth/login`, and `POST /api/auth/register` all behave exactly as before this change — in particular, the new `RequestPredicates.not(path("/api/{service}/interno/**"))` exclusions on `authRoute()`/`pedidosRoute()`/`enviosRoute()` must not shadow or break any existing legitimate path under those prefixes.
12. Starting `auth-service` with `INTERNAL_SERVICE_SECRET` unset/blank fails the same way the other 5 services already do post-fix (clean `401` on every internal-auth check, or a startup failure — pick the same one of the two established behaviors already used elsewhere, not the pre-fix raw `500` documented in `internal-service-auth.md`'s QA feedback).

## 9. Open questions

1. **Should `ms-pagos` data be folded into this endpoint (or a versioned successor) for full ARCO+ completeness?** `Pago` (per `COMPLIANCE_CL.md` §2: referencia de orden Flow, estado de pago, monto) likely has the same identity-linkage gap as `Envio` today (a `pedidoId`-shaped FK, not a direct user email/id) — flagged as a fast-follow, not designed here, to keep this cycle's scope matching the task's explicit ask.
2. **Closing `auth-service`'s host port (`8081:8081`)** — recommended in §2/§6 as directly load-bearing for this design, but it's adjacent scope (a `docker-compose.yml`-only change unrelated to the ARCO+ feature itself) and might affect a local-dev workflow that hits Swagger UI on `8081` directly. Worth a one-line confirmation from whoever owns the dev workflow before merging — if direct Swagger access on `8081` is actually used, the alternative is to keep the port open but treat `InternalAuthFilter` as the *sole* (not merely first) line of defense for that service, which this design already does regardless.
3. **Case sensitivity of the `Pedido.userEmail` match.** `CreatePedidoRequest.userEmail` is client-supplied at order-creation time (not derived from the creator's own JWT — a pre-existing, out-of-scope gap), so there's no guarantee it was entered with the same casing as the account's login email. Recommend a case-insensitive match (`LOWER(user_email) = LOWER(:email)`) in `findByUserEmail` rather than assuming exact-match is safe, so a user doesn't silently see an incomplete "my data" view due to a casing mismatch — flagging as an implementation decision to make explicitly, not to default silently.
4. **Should `Pedido.observaciones` be included verbatim?** It sometimes carries internal-operational text (e.g., `"Pago Flow confirmado. Token: ..."`, set by `OrderService`) rather than customer-meaningful content. This design includes the core pedido fields but is not fully certain `observaciones` is always safe/meaningful to disclose as-is — worth a quick look during implementation at what values this field actually takes in practice before deciding to include or drop it from `PedidoDatosDTO`.
5. **Is a frontend "Mis datos" page in scope for this cycle, or backend/API-only?** The task's ask is framed entirely around the API contract; this design is backend-only. Flagging so the orchestrator can decide whether a follow-up frontend design is expected before this is considered "done" from a user-facing ARCO+ standpoint (an API a user can't discover or trigger from the UI only partially satisfies the *practical* spirit of "derecho de acceso," even if it satisfies it technically).
6. **Rate limiting/scraping protection for this endpoint** — none is added, consistent with every other authenticated `GET` in this codebase having none today. Not treated as blocking since it's not a new class of risk introduced by this feature specifically (the existing httpOnly-cookie + `SameSite=Lax` posture already mitigates the main session-hijacking vector), but personal-data-bulk-disclosure endpoints are a marginally higher-value target than most — worth a note for whoever prioritizes future hardening work.
