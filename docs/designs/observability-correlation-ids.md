# Correlation-ID propagation and structured tracing for multi-step flows

## Revision 1 — response to QA's design-gap finding (three endpoints have no log statement to carry the correlation ID)

**What changed and why:** QA's live docker-compose run (see the "## Design gap found in QA (revision 1)" section appended at the bottom of this doc) confirmed the entire mechanism this design specifies — filter generation/validation, MDC lifecycle, header propagation across both WebClient (surviving `Retry.backoff()`'s thread hop) and RestTemplate, the `saga_estado.correlation_id` migration, log-injection defenses — works exactly as designed, with 269/269 tests passing and zero regressions. But acceptance criteria 2, 3, and 7 require grepping the correlation ID out of specific services' logs for specific request paths, and three of those paths turned out to have **no pre-existing `log.*` call at all** for the ID to attach to:

- `CancelacionController` (api-gateway): zero `log.*` calls on the happy path or the 409-blocked path (only inside two failure `catch` blocks, confirmed by reading the file — lines 137, 172, 247 of the pre-revision version).
- `EnvioController` (ms-envios): zero logging anywhere in the class, and `grep -rn "log\." services/ms-envios/src/main/java` confirmed zero logging anywhere in the *entire service* except `InternalAuthFilter`'s rejection warning.
- `OrderController.getPedidosPorEmail` (ms-pedidos, `GET /pedidos/interno/por-email/{email}`): no log statement, distinct from the sibling `PUT .../anonimizar` endpoint on the same controller, which already logs at INFO via `OrderService.anonimizarPorEmail` (`"[ARCO+] Pedidos anonimizados por cancelación — cantidad={}"`, confirmed present and requiring no change).

**Decision: option (a) — scope in a small, precisely-named set of new `log.info` statements at exactly these points.** Rejected option (b) (narrowing AC2/AC3/AC7 to only already-logged endpoints and documenting the rest as an accepted gap) because it would leave the feature unable to do the one thing it exists for on two of its three named flows: the Cancelación Facade (api-gateway → auth-service/ms-pedidos/ms-envios/ms-pagos) and the Saga's envío step are two of the three flows this design's own Summary names as "currently undebuggable end-to-end," and Cancelación's bloqueo-check and completion/blocked outcomes are exactly the steps a support engineer or auditor would need to trace. Declining to add three `log.info` lines — pure observability instrumentation, zero business-logic change, no new behavior, no new endpoint, no new data flow — to avoid a small diff would defeat the design's stated purpose for no corresponding benefit. This is the same bar §4 of this doc already applied when rejecting Micrometer Tracing/Zipkin: do the smallest thing that solves the actual, stated problem — here, that smallest thing is adding the missing log lines, not weakening the acceptance criteria to fit an implementation that doesn't yet meet them.

**Exactly what to add** (INFO level throughout — confirmed by re-reading every `log.*` call site across all 7 services: `log.info` is the established level for normal request-handling / business-event lines — e.g. `"Pedido creado: id={} tipo={}"`, `"[ARCO+] Cancelación iniciada — email={}"`, `"[Saga {}] Envío creado: id={}"` — while `log.warn`/`log.error` are reserved for degraded/failure/compensation paths, which these are not):

1. **`services/api-gateway/.../controller/CancelacionController.java`** (already `@Slf4j`, no new import needed):
   - Happy path — one new line immediately before the final `return ResponseEntity.ok()...` (i.e., after the Paso 4 `finalizar` call succeeds):
     ```java
     log.info("[ARCO+] Cancelación completada — email={}", email);
     ```
   - Blocked path — one new line immediately before `revertirCancelacion(email);` inside the `if (!pedidosBloqueantes.isEmpty())` block:
     ```java
     log.info("[ARCO+] Cancelación bloqueada — email={}, pedidosBloqueantes={}", email, pedidosBloqueantes);
     ```
   - Both match this class's own existing `[ARCO+] ... email={}` convention (already used in its three `log.warn` catch-block lines) — `email` is already logged elsewhere in this exact controller and in `UsuarioDatosController`'s ARCO+ endpoints, so this introduces no new category of logged PII, only extends an already-reviewed convention to the two paths that were silently missing it. `pedidosBloqueantes` is a list of internal pedido IDs (`Long`), not personal data.

2. **`services/ms-envios/.../controller/EnvioController.java`**: this class has no logger today — add `@Slf4j` (import `lombok.extern.slf4j.Slf4j`) alongside its existing `@RestController @RequestMapping("/envios") @RequiredArgsConstructor`. Add exactly three lines, at the three methods that sit on the Saga or the Cancelación-bloqueo-check paths this design's Summary names — not on `getAll`, `getById`, `updateStatus`, or `health`, which are not on either named flow and are left untouched to keep this revision's diff scoped to the QA finding, not a blanket sweep of an unrelated service:
   - `crear` (`POST /envios`, hit by the Saga's `EnviosSagaClient.crearEnvio`) — capture the result in a local variable and log after creation, before the existing return:
     ```java
     EnvioDTO creado = envioService.crearEnvio(req);
     log.info("[Envios] Envío creado: id={}, pedidoId={}", creado.getId(), creado.getPedidoId());
     return ResponseEntity.status(HttpStatus.CREATED).body(creado);
     ```
   - `cancelarEnvio` (`DELETE /{id}/cancelar`, hit by the Saga's compensation call `EnviosSagaClient.cancelarEnvio`) — one line before the existing return:
     ```java
     log.info("[Envios] Envío {} cancelado (compensación saga={})", id, sagaId);
     ```
   - `getPorPedidos` (`GET /interno/por-pedidos`, hit by `CancelacionController.obtenerPedidosBloqueantes`, the Cancelación bloqueo check) — log only on the success path, not on the existing 400 (non-numeric `pedidoIds`) branch, since that branch is pre-existing input-validation behavior unrelated to this gap:
     ```java
     List<EnvioDatosDTO> resultado = envioService.getPorPedidoIds(ids);
     log.info("[Envios] Consulta interna por pedidos — pedidoIds={}, resultados={}", pedidoIds, resultado.size());
     return ResponseEntity.ok(resultado);
     ```
   - Deliberately **not** logging `destino`/`transportista`/any other field from `EnvioDTO`/`EnvioDatosDTO` — those can carry delivery-address data; logging only the numeric `id`/`pedidoId`/count/requested-IDs keeps this at the same minimization level as every other `[ARCO+]`/`[Saga]` log line in the codebase (COMPLIANCE_CL.md's "Minimización: no loggear PII ... en texto plano" row, currently ✅).

3. **`services/ms-pedidos/.../controller/OrderController.java`** (already `@Slf4j`): add one line to `getPedidosPorEmail` (`GET /pedidos/interno/por-email/{email}`, hit by both the Cancelación bloqueo check and `UsuarioDatosController`'s ARCO+ access/portability aggregation):
   ```java
   List<PedidoDatosDTO> pedidos = orderService.getPedidosByUserEmail(email);
   log.info("[ARCO+] Consulta interna de pedidos por email — email={}, resultados={}", email, pedidos.size());
   return ResponseEntity.ok(pedidos);
   ```
   Same `[ARCO+] ... email={}` convention already established in this exact codebase (`AuthService`, `UsuarioDatosController`, and now `CancelacionController` above). No change needed to `anonimizarPorEmail` on the same controller — it already logs at INFO via `OrderService.anonimizarPorEmail`.

No other file changes. All three additions are plain `log.info` calls reading already-in-scope local variables (`email`, path/query params already validated by Spring's binding, or values already returned to the client in the response body) — no new field is introduced anywhere that isn't already either logged elsewhere in this codebase under the same convention, or already present in that same endpoint's own response body. Once `logging.pattern.level` (§5.4) is applied, every one of these new lines automatically carries `[cid=...]` — no MDC-reading code is added by this revision; the correlation ID reaches these lines exactly the same way it already reaches every pre-existing log line in the codebase.

This supersedes nothing in §§1-10 below — the MDC/header mechanism, the `@Async`/`Retry.backoff()` fixes, and the `saga_estado.correlation_id` migration were all verified correct by QA and are unchanged. Only §11 (Acceptance criteria) is amended, to make AC2/AC3/AC7 concrete against the log lines now being added, and to add explicit coverage for the blocked path and the Saga envío-compensation path that QA's finding also implicated.

## 1. Summary

SmartLogix has three cross-service, retryable flows that are currently undebuggable end-to-end: the Flow payment webhook (ms-pagos → ms-pedidos → Saga), the Cancelación 4-step orchestration in api-gateway's `CancelacionController` (auth-service → ms-pedidos/ms-envios → ms-pedidos/ms-pagos → auth-service), and the `SagaOrchestrator` in ms-pedidos (ms-inventario → ms-pedidos → ms-envios → notification-service). Today, tying one logical operation together across these service boundaries means manually grepping per-service logs with no shared identifier. This design adds a lightweight correlation ID (`X-Correlation-Id` header + `correlationId` MDC key) generated at the edge, propagated through the *existing* internal-header-propagation mechanism (the same Filter/Interceptor/ExchangeFilterFunction pattern already used for `X-Internal-Service`/`X-User-Email`), and rendered in every log line via a one-line `logging.pattern.level` change per service — no new library, no new infrastructure, no business-logic change.

## 2. Affected services

All 7 services + api-gateway, but strictly as **independent parallel changes** (not a Saga, not a Facade) — each service gets the same small, self-contained addition (an inbound filter that reads-or-generates the ID and sets MDC, plus, where the service makes outbound calls, an explicit header on each outbound call). No service depends on another service's change to work correctly in isolation; a partial rollout (e.g., api-gateway updated but ms-pagos not yet) degrades gracefully to today's behavior (missing header ⇒ downstream service just generates its own fresh ID, same as an external caller).

- **api-gateway**: new global `CorrelationIdFilter` (edge — generates ID if absent, wraps the request so the ID is visible to `RouterFunction`/`HandlerFunctions.http(...)` reverse-proxying and to the BFF `@RestController`s reached via the `localhost:8080` loopback routes: `CancelacionController`, `RectificacionController`, `OposicionController`, `UsuarioDatosController`, `DashboardController`, `SessionController`).
- **auth-service, ms-inventario, ms-pedidos, ms-envios, ms-pagos, notification-service**: same-shaped inbound `CorrelationIdFilter` (mirrors the existing per-service `InternalAuthFilter`), plus outbound propagation added at each existing outbound call site (WebClient in ms-pedidos's Saga steps/clients, RestTemplate in ms-pagos's `PagoService`).
- **frontend**: not affected. No UI requirement was given; the response header is enough for a human to grab during support/QA with browser devtools or curl. (See Open Questions.)

## 3. What already exists (read before implementing)

Confirmed by reading the code, not assumed:

- `docker-compose.yml` has **no** APM/tracing backend (no Zipkin, Jaeger, ELK, Loki, etc.) and no exposed port for one. Every service already logs to stdout/stderr via plain `@Slf4j` (`lombok.extern.slf4j.Slf4j`), no `logback-spring.xml` exists anywhere in the repo (`find ... -iname "logback*.xml"` returned nothing) — logging uses Spring Boot's default console pattern. **No MDC usage exists anywhere today** (`grep -rn "MDC"` across all `src/main/java` returned nothing).
- The established service-to-service auth pattern is a matched pair per direction:
  - api-gateway → downstream: `InternalTokenSigner.exchangeFilter()` (`services/api-gateway/.../security/InternalTokenSigner.java`), a `WebClient` `ExchangeFilterFunction` that HMAC-signs `X-Internal-Service`/`X-Internal-Timestamp`/`X-Internal-Signature`, applied per-`WebClient`-instance at construction time (e.g. in `CancelacionController`'s constructor, `builder.clone().baseUrl(...).filter(internalTokenSigner.exchangeFilter()).build()`).
  - Receiving service: a per-service `InternalAuthFilter` (`jakarta.servlet.Filter`, `@Order(Ordered.HIGHEST_PRECEDENCE)`) verifies those headers before the request reaches Spring Security/the controller. Same shape in auth-service, ms-inventario, ms-pedidos, ms-envios, ms-pagos, notification-service.
  - ms-pagos → ms-pedidos specifically uses `RestTemplate` + `InternalAuthInterceptor` (`ClientHttpRequestInterceptor`), not `WebClient` — `PagoService.confirmarPagoEnPedidos`/`marcarPagoFallido` call `restTemplate.postForEntity(...)`.
  - api-gateway's own inbound side (`AuthFilter`, `InternalTokenIssuerFilter`, `StripCookieFilter`) uses the **functional** `HandlerFilterFunction<ServerResponse, ServerResponse>` style (`org.springframework.web.servlet.function.*`, part of `spring-cloud-starter-gateway-mvc` — a **synchronous, servlet-based** gateway, confirmed via `pom.xml`, not WebFlux) attached per-route in `GatewayConfig` (e.g. `.filter(authFilter).filter(internalTokenIssuerFilter).filter(stripCookieFilter)`). These filters mutate the `ServerRequest`'s headers via the strip-then-set idiom (`ServerRequest.from(req).headers(h -> h.remove(...)).header(...).build()`), and that mutated header set is what `HandlerFunctions.http(...)` forwards to the proxied target — this is *why* `X-User-Email`/`X-Internal-*` already reliably reach downstream services today, including api-gateway's own BFF `@RestController`s reached through the `localhost:8080` loopback routes (`dashboardRoute`, `sessionRoute`, `usuariosRoute` — genuine second HTTP hops on the gateway's own embedded Tomcat, not in-process method calls).
  - `webhookFlowRoute` (Flow's callback) is the one route with **no** `authFilter`/`internalTokenIssuerFilter` at all — Flow cannot send a JWT or sign internal headers.
- **Threading model, verified by reading the actual call sites, not assumed:**
  - `CancelacionController` calls `.block()` immediately after every single-shot WebClient chain (steps 1, 2, 4) — fully synchronous from the caller's point of view.
  - Step 3 (`anonimizarPedidosYPagos`) is the *only* place with real concurrency: `Mono.zip(pedidosMono, pagosMono).block()`, where each leg has `.retryWhen(Retry.backoff(3, Duration.ofMillis(300)))`. **`Retry.backoff()` resubscribes after the backoff delay using `Schedulers.parallel()` by default** — meaning a retried attempt's subscription (and therefore anything a shared, request-independent `ExchangeFilterFunction` bean does at that point, e.g. `InternalTokenSigner.exchangeFilter()`) runs on a `parallel-N` thread, **not** the original servlet thread that called `anonimizarPedidosYPagos`. `MDC` is a `ThreadLocal`; a value set via `MDC.put()` on the servlet thread is **not visible** on that `parallel-N` thread unless explicitly carried over. This is a real, concrete gap for anything that reads `MDC.get()` *inside* a shared, bean-scoped Reactor operator — see §5 for why the design below avoids ever doing that.
  - Today, **no log statement in this codebase executes inside a Reactor operator** (no `.doOnNext`/`.doOnError`/`.map`-with-logging on any of these Monos) — every `log.warn/info` in `CancelacionController`, `PagoService`, `SagaOrchestrator`, `NotifyStep` etc. runs either *before* a WebClient call is built or *after* `.block()` has returned control to the original calling thread (`.block()` parks the calling thread and resumes it on that same thread once the upstream completes — it does not itself cause a thread hop for the code that follows it). So MDC set once at the top of a request, on that request's own thread, is already reliably visible to every *existing* log line in the codebase, retries included, **as long as the correlation ID header sent to the retried HTTP call is baked in as a literal value at Mono-assembly time rather than re-read from MDC on each resubscription** (see §5.3).
  - The RestTemplate path (ms-pagos → ms-pedidos) has **no** thread-hopping concern at all — `RestTemplate` and its `ClientHttpRequestInterceptor`s are always fully synchronous on the calling thread, so reading `MDC.get()` directly inside a shared `ClientHttpRequestInterceptor` bean is safe there.
  - `SagaOrchestrator.ejecutar()` runs its 4-step loop synchronously, in-process, on the single servlet thread handling either `POST /api/sagas/pedido` (direct call) or `POST /pedidos/{id}/confirmar-pago` (triggered from the Flow webhook chain via `OrderController` → `OrderService.confirmarPagoYDispararSaga`). No `@Async`/thread-pool hop inside the Saga itself.
  - Out of scope but noted for completeness: `ms-pedidos`'s **other** notification path — `LogisticaFacade`/`OrderService` publishing `PedidoAprobadoEvent`, consumed by `NotificationListener.handlePedidoAprobado` which **is** `@Async` — has the same MDC-thread-hop problem, but it is not one of the three flows this task named (it's the non-Saga `POST /pedidos` path). Not touched here; flagged in Open Questions.

## 4. Decision: MDC + header propagation only. No Micrometer Tracing, no Zipkin.

Evaluated and explicitly rejected for now:
- **Micrometer Tracing + Zipkin (or any APM backend)**: would require a new docker-compose service, a new exposed port, a new operational dependency to keep running and to secure, and — critically — **nobody currently consumes traces**. There is no dashboard, no on-call process, no existing precedent in this repo for standing up new infrastructure speculatively (the repo's own Flyway-baseline and coverage-gate work were both scoped to solve an immediate, concrete problem with the smallest change that solves it — same bar applies here). A correlation ID that shows up in `docker compose logs` via `grep` solves the actual, stated problem ("debugging any of these end-to-end means manually grepping per-service logs with no shared identifier") without any of that cost.
- This does **not** foreclose Micrometer Tracing later — the MDC convention this design uses (`correlationId` in `logging.pattern.level`) is the same shape Micrometer Tracing's Slf4j bridge uses (`traceId`/`spanId`), so adopting it later would not require reverting this work.

## 5. Design

### 5.1 New header and MDC key

- HTTP header: `X-Correlation-Id`
- MDC key: `correlationId`
- Format: opaque token, `^[A-Za-z0-9._-]{1,64}$` (covers `UUID.randomUUID().toString()` and human-supplied IDs for manual `curl` testing). **Never** derived from or containing PII (email, name, etc.) — it is a random/opaque value only.

### 5.2 Edge behavior (generate-if-absent)

Every service gets a new `CorrelationIdFilter implements jakarta.servlet.Filter`, `@Order(Ordered.HIGHEST_PRECEDENCE)` — one order-bucket *before* the existing `InternalAuthFilter` (bump each service's existing `InternalAuthFilter`'s `@Order(Ordered.HIGHEST_PRECEDENCE)` to `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` so ordering between the two is deterministic and documented, not incidental). Behavior, identical across all 7 services + api-gateway:

1. Read `X-Correlation-Id` from the incoming request.
2. If present and matches the format above, use it. Otherwise (absent, malformed, oversized) **generate a fresh `UUID.randomUUID().toString()`** — never trust an external value blindly, same "strip-then-set" posture `AuthFilter`/`InternalTokenIssuerFilter` already use for `X-User-Email`/`X-Internal-*` (see §6 A09 on why: this value lands directly in every log line, so it is a log-injection surface).
3. In api-gateway specifically, wrap the request in a small `HttpServletRequestWrapper` that makes `getHeader("X-Correlation-Id")`/`getHeaders(...)` return the canonical (validated-or-generated) value, so that Spring Cloud Gateway MVC's `RouterFunction`/`HandlerFunctions.http(...)` — which builds its `ServerRequest` view from this same `HttpServletRequest` — forwards it automatically to whichever downstream/loopback target it proxies to, with **zero changes needed to `GatewayConfig`'s per-route `.filter(...)` chains**. (This is the one piece of this design that rests on an assumption about Spring Cloud Gateway MVC's default header-forwarding behavior rather than a line of code already proven to do this in the repo — flagged explicitly in Open Questions; verify with a real `curl` early in implementation.)
4. `MDC.put("correlationId", id)` for the duration of the request; `MDC.remove("correlationId")` in a `finally` block (mandatory — Tomcat threads are pooled and reused, a leaked MDC value would bleed into an unrelated later request on the same thread).
5. Echo the value back as a response header `X-Correlation-Id` (useful for `curl -v`/QA/support).

This filter is a plain `@Component`-registered Servlet `Filter`, so Spring Boot auto-registers it for every URL, including `webhookFlowRoute` (Flow's callback, which has no other filter today) and requests that `InternalAuthFilter`/`AuthFilter` end up rejecting with 401/403 (the correlation ID must still appear in the rejection's log line).

### 5.3 Outbound propagation — two different mechanisms for two different HTTP clients, deliberately

**RestTemplate (ms-pagos → ms-pedidos only)**: safe to read `MDC.get("correlationId")` directly inside a shared, singleton `ClientHttpRequestInterceptor` — either extend the existing `InternalAuthInterceptor` or add a sibling `CorrelationIdInterceptor` registered alongside it in `PagoService.configurarRestTemplate()`. `RestTemplate` never hops threads, so this is reliable with no caveats.

**WebClient (api-gateway's `CancelacionController`; ms-pedidos's Saga steps/clients `InventarioSagaClient`, `EnviosSagaClient`, `NotifyStep`)**: do **not** read `MDC.get()` inside the shared `InternalTokenSigner.exchangeFilter()` bean or any new shared `ExchangeFilterFunction` — that bean is built once (at controller/component construction time) and reused across every request and every retry attempt, including retry resubscriptions that run on a `parallel-N` thread per §3. Instead, at **each call site**, capture the value into a local variable on the calling thread — where it is always reliably present, retry or not — and pass it as an explicit header on that specific request builder call:

```java
String correlationId = MDC.get("correlationId");
pedidosClient.put()
    .uri("/pedidos/interno/por-email/{email}/anonimizar", email)
    .header("X-Correlation-Id", correlationId)
    .retrieve()
    .bodyToMono(Map.class)
    .retryWhen(Retry.backoff(3, Duration.ofMillis(300)))
    ...
```

Because `"X-Correlation-Id"` is set with a literal `String` value at assembly time (not a supplier/lambda that re-reads MDC), that same value is baked into the `ClientRequest` template and is resent unchanged on every retry attempt — this works correctly *without* needing Reactor `Context` propagation, `Hooks.enableAutomaticContextPropagation()`, or any new dependency, and is why introducing those would be speculative here. Apply this same "capture-then-explicit-header" pattern at every existing WebClient call site listed in §2; do not add it only to the retried step.

### 5.4 Logging

No new `logback-spring.xml`. Add one line to each of the 7 services' `application.properties` (Spring Boot's documented mechanism for exactly this: injecting an MDC value into the default console pattern without redefining the whole layout):

```properties
logging.pattern.level=%5p [cid=%X{correlationId:-none}]
```

`%X{correlationId:-none}` renders `none` for any log line emitted outside an HTTP request (e.g. `@PostConstruct` startup validation), and the real value for every request-scoped line.

### 5.5 `saga_estado.correlation_id` (ms-pedidos)

Add a nullable `correlation_id VARCHAR(64)` column to `saga_estado` and set it once in `SagaOrchestrator.ejecutar()` when the row is first persisted (alongside the existing `sagaRepo.save(estado)` calls — no change to *what* the Saga does, only one more field recorded). Rationale: `saga_estado` already persists one row per Saga execution and already outlives log retention/rotation; a `correlation_id` column lets someone reconstruct "which log lines correspond to this persisted Saga row" long after the originating logs have rotated away — directly serving COMPLIANCE_CL.md §4.4's "logging suficiente para reconstruir un incidente." This is the only schema change in this design; no other service's data model changes (Pago's webhook retries can legitimately each carry a *different* correlation ID since Flow does not propagate one — see §5.6 — so a single persisted column on `Pago` would just get overwritten and add little value; the existing `flowOrder`/`token`/`commerceOrder` fields remain the correct persistent join key across time for payment flows).

Migration: `services/ms-pedidos/src/main/resources/db/migration/V2__add_correlation_id_to_saga_estado.sql`:
```sql
ALTER TABLE saga_estado ADD COLUMN correlation_id VARCHAR(64);
```

### 5.6 Flow webhook — how a webhook-originated flow gets a starting ID

Flow Chile calls `POST /api/pagos/webhook/flow` with no custom headers and no knowledge of any ID SmartLogix used earlier. `webhookFlowRoute` has no `authFilter`/`internalTokenIssuerFilter`, but it **does** get the new global `CorrelationIdFilter` (§5.2, applied to every request, not per-route) — since Flow never sends `X-Correlation-Id`, the filter generates a fresh UUID for that specific webhook call. This is a **different** ID than the one used during the original `POST /api/pagos/crear` checkout call minutes/hours earlier — there is no mechanism to avoid that (Flow does not echo back arbitrary custom headers/state we don't control). This is by design, not a gap: correlation ID answers "which log lines belong to this one HTTP call chain, including its retries," while the pre-existing `token`/`commerceOrder`/`flowOrder` fields (already logged today, e.g. `PagoService.iniciarPago`'s `"[Pagos] Pago iniciado: pedidoId={}, token={}, urlPago={}"` and `procesarWebhook`'s masked-token log) remain the correct **domain** join key for pivoting from "this webhook call" back to "the checkout call that created this payment," across time and across the external Flow hop. Document both IDs' distinct purpose so nobody mistakes the correlation ID for a payment-lifecycle key.

## 6. API contract

No new endpoint and no change to any existing endpoint's request/response *body* or auth requirement. The only contract addition:

| Header | Direction | Required | Notes |
|---|---|---|---|
| `X-Correlation-Id` | Request (optional, any endpoint) | No | If present and matches `^[A-Za-z0-9._-]{1,64}$`, honored; otherwise ignored and a fresh UUID is generated. Never validated against any allowlist of callers — it carries no authorization weight. |
| `X-Correlation-Id` | Response (all endpoints, including `/api/auth/*` and `/api/pagos/webhook/flow`) | Always present | Echo of the ID used for that request. |

No existing endpoint's status codes, error bodies, or auth (JWT role/claim) requirements change.

## 7. Data model changes

- **ms-pedidos** (`pedidos_db`): new nullable column `saga_estado.correlation_id VARCHAR(64)` via `V2__add_correlation_id_to_saga_estado.sql` (§5.5). No other table changes.
- **auth-service, ms-inventario, ms-envios, ms-pagos, notification-service**: no schema changes.

## 8. Design pattern fit

This extends the existing internal-header-propagation convention already used for `X-Internal-Service`/`X-Internal-Timestamp`/`X-Internal-Signature` (`InternalTokenSigner`/`InternalTokenIssuerFilter`/`InternalAuthFilter`/`InternalAuthInterceptor`) and for `X-User-Email` (`AuthFilter`): a strip-then-set edge `Filter`, paired with a matching outbound `ExchangeFilterFunction`/`ClientHttpRequestInterceptor`. Structurally this is Chain of Responsibility (the Filter chain) plus a small Decorator (wrapping the outbound request with an extra header) — the same shape already used five times in this codebase, not a new pattern. It is **not** listed in `README.md`'s named GoF pattern table (Repository/Factory Method/Circuit Breaker/Observer/Strategy/Facade/Saga) and does not need to be added there — like `InternalTokenSigner`/`InternalAuthFilter` before it, this is cross-cutting infrastructure plumbing, not a domain design pattern, and inventing a README entry for it would be exactly the kind of speculative abstraction the brief warns against.

## 9. Security requirements (OWASP Top 10 2021)

- **A01 Broken Access Control**: N/A for access control itself — `X-Correlation-Id` carries no identity or authorization claim and must never be used to make an access-control decision (this must not become a de facto session/tenant token). All existing ownership checks (JWT-derived email, `InternalAuthFilter`'s allowlist-by-issuer) are completely unaffected; this design changes nothing about *who* can call *what*.
- **A02 Cryptographic Failures**: no secrets, keys, or payment data are placed in the correlation ID. Explicit requirement: the ID must be a random/opaque token, never derived from or containing an email, name, card/payment reference, or JWT claim — otherwise it would leak PII into logs and the response header beyond what's logged today.
- **A03 Injection**: N/A — no SQL, no dynamic query, no shell/command construction touches this value. The one new column (`saga_estado.correlation_id`) is written via JPA parameter binding like every other column on that entity.
- **A04 Insecure Design**: retries of the anonymization step intentionally reuse the *same* correlation ID (§5.3) — that is the point (grouping retries under one ID for debugging) and must **not** be repurposed as an idempotency key; the underlying endpoints' idempotency already comes from their own UPDATE-by-email semantics (per `arco-cancelacion-oposicion.md`), unchanged by this design. A caller replaying the same client-supplied `X-Correlation-Id` on an unrelated later request causes no harm beyond a slightly confusing log trail (no state, no cache, no dedup keyed on it) — explicitly out of scope to add such a dedup mechanism here (would be new business logic).
- **A05 Security Misconfiguration**: no new env var, no new exposed port, no new actuator endpoint, no change to `management.endpoints.web.exposure.include` in any service. `docker-compose.yml` is unchanged.
- **A07 Auth Failures**: N/A — JWT/session/cookie handling is completely unchanged; `CorrelationIdFilter` runs *before* `AuthFilter`/`InternalAuthFilter` purely so rejected requests are still traceable, but it makes no auth decision itself.
- **A08 Software/Data Integrity**: no change to Flow's webhook HMAC signature verification (`FlowService.verificarFirmaWebhook`) or to the internal-service HMAC checks — correlation ID is explicitly never treated as an integrity or authenticity signal for anything. Deserialization surface: the header is read as a bounded (≤64 char), regex-validated `String` only — never deserialized into an object.
- **A09 Logging/Monitoring**: this *is* the feature. Format validation (§5.2 step 2) exists specifically to prevent log-injection (CRLF/newline injection to forge fake log lines, or a multi-KB header to bloat log storage) via a value that, unlike most logged fields today, can be supplied by an external caller and is guaranteed to appear in every log line for that request. Must NOT log: passwords, full JWTs, card numbers (unchanged — this design adds a field to log lines, it does not add any new field to log). The masked-token convention already in place (`token.substring(0,8)+"***"`) is untouched.
- **A10 SSRF**: N/A — the correlation ID is never used to construct an outbound URL or make a routing decision; it's a header value only.

## 10. Chilean compliance touchpoints

Cross-referencing `docs/COMPLIANCE_CL.md`:

- **Ley 21.663 (§3, gestión de riesgos + reporte de incidentes)**: directly relevant and directly improved. COMPLIANCE_CL.md §4.4 lists "Logging suficiente para reconstruir un incidente (quién, qué, cuándo) sin loggear secretos" as **"Verificar en auditoría viva."** This design is a concrete step toward closing that gap for the three named cross-service flows — reviewer/QA should update that checklist row to reflect this once implemented (still `⚠️ parcial`, since the `@Async` Observer path in §3 remains unaddressed, and log **retention** — a separate row in the same table — is still `❌ pendiente`).
- **Ley 21.719 (protección de datos personales)**: this design does not add any new personal-data field to what's already logged (no new PII is introduced into logs; it adds a non-PII, opaque correlation field alongside existing lines). It does not touch any ARCO+ right's business logic (Cancelación's actual anonymization semantics are unchanged) — it only makes Cancelación's *execution* easier to audit/debug, which is itself an "accountability" (responsabilidad demostrable) improvement under Ley 21.719 principles. Residual consideration worth naming explicitly: because the correlation ID makes it materially easier to reconstruct one user's full cross-service journey from log access alone, the value of restricting *who has log access* and how long logs are retained goes up — this doesn't create a new legal obligation beyond what COMPLIANCE_CL.md §4.4 already flags as pending (log retention policy), but this design makes that pre-existing gap slightly more consequential if left unresolved, worth a one-line callout in that doc.

## 11. Acceptance criteria

1. Given a request to `POST /api/usuarios/me/cancelacion` with no `X-Correlation-Id` header, the response includes an `X-Correlation-Id` header whose value is a well-formed UUID.
2. Given the same request with a valid caller-supplied `X-Correlation-Id: test-abc-123`, for an account with **no** blocking pedidos/envíos (happy path), the response echoes back exactly `test-abc-123`, and that exact string appears in api-gateway's log line for that request — specifically `CancelacionController`'s `log.info("[ARCO+] Cancelación completada — email={}", email)` line added in Revision 1.
3. **End-to-end (docker-compose, real run)**: register a user, log in, create a pedido, then call the Cancelación endpoint with a known `X-Correlation-Id`. `docker compose logs api-gateway auth-service ms-pedidos ms-envios ms-pagos | grep <that-id>` returns log lines from **all of**:
   - api-gateway (`CancelacionController`'s `[ARCO+] Cancelación completada` line, Revision 1),
   - auth-service (existing `[ARCO+] Cancelación iniciada — email=...` and `[ARCO+] Cancelación completada — id=...` lines, unchanged),
   - ms-pedidos (the new `[ARCO+] Consulta interna de pedidos por email — email=..., resultados=...` line from `OrderController.getPedidosPorEmail`, Revision 1, plus the existing `[ARCO+] Pedidos anonimizados por cancelación` line),
   - ms-envios (the new `[Envios] Consulta interna por pedidos — pedidoIds=..., resultados=...` line from `EnvioController.getPorPedidos`, Revision 1),
   - and ms-pagos only if the account had a payment (existing `[ARCO+] Pagos anonimizados por cancelación` line),

   in an order consistent with the 4 steps (iniciar → bloqueo check → anonimizar → finalizar).
4. **Retry visibility**: force a transient failure of the anonymization step (e.g., briefly `docker compose stop ms-pedidos` for under ~1s and restart, or point `pedidos.service.url` at an unreachable host briefly) so `retryWhen` fires at least once; confirm ms-pedidos's log for `/pedidos/interno/por-email/{email}/anonimizar` shows **more than one** invocation and every one of them carries the **same** correlation ID as the original api-gateway request.
5. **Webhook-originated flow**: `curl -X POST http://localhost:8080/api/pagos/webhook/flow?token=<sandbox-token>` (no `X-Correlation-Id` header) produces exactly one fresh UUID in ms-pagos's `[Webhook Flow]` log line, distinct from the correlation ID used for the earlier `POST /api/pagos/crear` call that created that payment — while `docker compose logs ms-pagos | grep <token-prefix>` still ties both calls together via the existing masked-token field.
6. **Log-injection rejection**: sending `X-Correlation-Id: abc\r\nFAKE: injected` (or a header value > 64 chars) results in the request being processed normally with a **freshly generated** UUID (the malformed value is neither echoed nor logged verbatim).
7. **Saga**: `POST /api/sagas/pedido` with a known `X-Correlation-Id` results in `saga_estado.correlation_id` (queryable directly against `pedidos_db`) matching that ID, and the same ID appears in ms-inventario, ms-pedidos, ms-envios (the new `[Envios] Envío creado: id=..., pedidoId=...` line from `EnvioController.crear`, Revision 1), and notification-service logs for that saga's steps.
8. **Rejected/unauthorized request still traceable**: a request to any protected endpoint with a missing/invalid JWT still produces a 401 whose corresponding log line (`AuthFilter`) includes a correlation ID.
9. **No regression**: existing test suites (`CancelacionControllerTest`, `SagaOrchestratorTest`, `SagaControllerTest`, `PagoControllerTest`, `InternalAuthFilterTest` per service) pass unmodified in their existing assertions; only new assertions/tests are added for header presence and MDC population.
10. **No new attack surface**: `docker-compose.yml` diff shows no new `ports:`, no new service, no change to `management.endpoints.web.exposure.include` in any `application.properties`.
11. **(Revision 1) Blocked path is traceable**: given a request to `POST /api/usuarios/me/cancelacion` with a known `X-Correlation-Id`, for an account with at least one non-terminal pedido or envío, the response is `409 CONFLICT`, and `docker compose logs api-gateway | grep <that-id>` includes `CancelacionController`'s new `log.info("[ARCO+] Cancelación bloqueada — email={}, pedidosBloqueantes={}", ...)` line, and the list of `pedidosBloqueantes` in that log line matches the `pedidosBloqueantes` array in the response body.
12. **(Revision 1) Saga compensation is traceable in ms-envios**: force a Saga failure after the envío-creation step succeeds (e.g., disable/point notification-service or a later step at an unreachable host so compensation fires), triggering `EnviosSagaClient.cancelarEnvio`; confirm ms-envios's new `log.info("[Envios] Envío {} cancelado (compensación saga={})", ...)` line (`EnvioController.cancelarEnvio`) carries the same correlation ID as the originating `POST /api/sagas/pedido` request.
   **Known-unreachable, confirmed by QA (2026), not blocking:** `NotifyStep` (the Saga's terminal step) catches and swallows every exception internally rather than throwing `SagaStepException`, and `CreateShipmentStep` can only fail *before* `ctx.setEnvioId()` is set — so `SagaOrchestrator`'s compensation phase can never actually be reached on any live path once envío creation has succeeded. QA independently confirmed this three ways (reading `NotifyStep.java`/`SagaOrchestrator.java`, a live forced-failure test that completed `COMPLETADA` with the envío left uncancelled, and the existing `SagaOrchestratorTest` assertion that `step4.compensate()` is never invoked). This is pre-existing Saga business logic this design's diff never touches — the new log line and header-capture code are provably correct wherever this path is ever reached, but this specific criterion cannot be demonstrated today. Tracked as a separate, out-of-scope follow-up (should a notification failure ever roll back an already-created envío/pedido? — that's a Saga design decision, not an observability one), not a defect in this feature.
13. **(Revision 1) No PII expansion**: diff review of the three new logging call sites (`CancelacionController`, `EnvioController`, `OrderController.getPedidosPorEmail`) confirms only `email` (already an established exception under the existing `[ARCO+]` convention elsewhere in the same services), numeric pedido/envío IDs, `sagaId`, and result counts are logged — no `destino`, `transportista`, card/payment data, password, or token is added to any log line by this revision, and the 400 (non-numeric `pedidoIds`) branch of `EnvioController.getPorPedidos` remains unlogged (out of scope — pre-existing input validation, not part of this gap).

## 12. Open questions

1. §5.2 step 3 (api-gateway's `HttpServletRequestWrapper` making the injected header visible to `HandlerFunctions.http(...)`'s reverse-proxy forwarding) is reasoned from how `AuthFilter`/`InternalTokenIssuerFilter` are proven to work today, but has not been executed. Verify with a real `curl` against a running `docker compose up` early in implementation, before building out every downstream filter — if header forwarding doesn't work exactly as reasoned, the fallback is to also add `.filter(correlationIdRouteFilter)` (a `HandlerFilterFunction` twin, same idiom as `AuthFilter`) to every `RouterFunction` bean in `GatewayConfig`, which is a larger, more repetitive diff.
2. Should `GET /sagas/{sagaId}` expose the new `correlation_id` column in its response body? Zero extra implementation cost since it's already on the entity, but it does change that endpoint's response shape slightly — leaving this to the developer/reviewer's judgment rather than mandating it.
3. The `@Async` `NotificationListener`/`PedidoAprobadoEvent` path (non-Saga `POST /pedidos` via `LogisticaFacade`) has the same MDC-thread-hop gap as the Saga's retry case but was not in the three named flows — flagging it here so the human can decide whether it's worth a follow-up `/dev-cycle` (would need a `TaskDecorator` on the `@Async` executor to copy MDC into the new thread, a slightly different fix than anything in this design).
4. No frontend requirement was given; if support/QA wants the correlation ID surfaced in the UI (e.g., on an error toast) for easier ticket reporting, that's a small separate frontend change not designed here.
5. **(Revision 1)** `EnvioController`'s other endpoints (`getAll`, `getById`, `updateStatus`, `health`) remain unlogged after this revision, same as most of the rest of the codebase's read/health endpoints — if a future incident needs those traced too, that's a separate, small follow-up, not implied by this design's three named flows.
6. **Found by QA (2026), pre-existing this design's diff:** every response that transits api-gateway's reverse-proxy (`HandlerFunctions.http`, used by every `RouterFunction` in `GatewayConfig`) carries `X-Correlation-Id` **twice** with identical values — confirmed on `/api/auth/login`, `/api/usuarios/me/cancelacion`, and `/api/pagos/webhook/flow`, likely because the proxy layer copies the downstream service's own echoed header onto the outer response that `CorrelationIdFilter` already set. Harmless for `grep`-based debugging (both copies carry the same value), but a strict HTTP client that folds duplicate headers per RFC 7230 would see a comma-joined string, not a bare UUID, technically undercutting AC1's wording for such a client. This isn't introduced by `CorrelationIdFilter`/`GatewayConfig` changes in this design — it's a property of the existing proxy mechanism newly made visible by adding a header worth inspecting. Worth a small, separate follow-up to fix the header-merge behavior; not blocking for this feature.

## Design gap found in QA (revision 1)

**VERDICT: FAIL — DESIGN GAP** (developer's implementation was correct against the design as written; the design itself has a false premise).

**What QA verified works correctly, live, via docker-compose:** the correlation-ID plumbing itself — filter generation/validation, MDC set/clear lifecycle, header propagation through both WebClient (including surviving `Retry.backoff()`'s thread hop onto `Schedulers.parallel()`) and RestTemplate (including surviving `@Async`'s executor-thread hop in `PagoService`), the `CorrelationIdFilter` → `InternalAuthFilter` ordering, the log-injection defenses (CRLF, oversized, non-ASCII/homoglyph, null-byte all correctly rejected), the `saga_estado.correlation_id` Flyway migration (V1→V2 adoption and fresh-DB boot both clean), and the webhook's fresh-ID generation. 269/269 tests pass across all 7 services, zero regressions.

**What actually fails, live:** Acceptance criteria 2, 3, and 7 require grepping a correlation ID out of specific services' logs for specific request paths — but those paths have **no pre-existing log statement to carry the value**:
- `CancelacionController` (api-gateway) has zero `log.*` calls on its happy path or its 409-blocked path (only inside two failure-`catch` blocks) — confirmed live, a full successful Cancelación and a blocked one both produced zero api-gateway log lines containing the ID.
- `ms-envios`'s `EnvioController` has **no logging anywhere in the entire service** (`grep -rn "log\." services/ms-envios/src/main/java` matches only `InternalAuthFilter`'s own rejection-path warning) — confirmed live for both the Saga's envío-creation step and the Cancelación bloqueo-check call.
- `ms-pedidos`'s `interno/por-email` endpoint (used by the Cancelación bloqueo check) also produced no log line in a live run.

This design's §3 "what already exists" review never checked whether `EnvioController` or `ms-pedidos`'s non-saga controllers had any logging to enrich — it only inspected logging in `CancelacionController`, `PagoService`, `SagaOrchestrator`, `NotifyStep`, and implicitly assumed (§5.4: "renders the real value for every request-scoped line") that request-scoped log lines already existed everywhere the acceptance criteria later required them. That assumption is false for the three call paths above.

**What's needed from this revision:** either (a) explicitly scope in a small, named set of new `log.info` statements at exactly the affected points (`CancelacionController`'s happy/blocked-path returns, `EnvioController`'s request-handling methods, `ms-pedidos`'s `interno/por-email` handler) — this is observability instrumentation, not business logic, and is the only way AC2/AC3/AC7 can actually be satisfied as written, since the feature's entire stated purpose ("debugging today means grepping per-service logs by hand") requires something to grep — or (b) revise AC2/AC3/AC7 to target only endpoints that already log today, and explicitly document the remaining gap (no log line exists yet for these paths, so the correlation ID's presence there is unverifiable-but-harmless until someone adds logging in a future change). Option (a) is very likely the right call given the feature's own motivation, but make the decision explicitly and say why, per this repo's usual practice — don't silently pick one.

Everything else in this design (the MDC/header mechanism, the `@Async` and `Retry.backoff()` fixes, the migration) needs no changes and should not be re-implemented — only the logging-statement scope needs resolving.
