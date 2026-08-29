# Security Baseline Remediation — Trivy/npm-audit CI Gate Hardening

## 1. Summary

`.github/workflows/security.yml` currently runs Trivy (filesystem + 8 image scans) and `npm audit` in report-only mode (`exit-code: '0'` / `npm audit ... || true`), so CI never fails on a CRITICAL/HIGH finding regardless of how bad it is. A baseline scan of the current `main` was run and synthesized (see task input): npm audit and the secrets scanner are clean; the misconfig scanner found two root-user Dockerfiles; the vuln scanners found a large but structurally simple cluster of CRITICAL/HIGH findings that trace almost entirely to all 7 backend services pinning `spring-boot-starter-parent: 3.4.5`, one minor behind current, plus an `api-gateway`-only Spring Cloud/BouncyCastle cluster and an `api-gateway`+`ms-pedidos`-only Netty cluster (both transitively resolved by the same two bumps), plus base-OS-image drift resolved by a fresh pull.

This design specifies the exact remediation — dependency version bumps (verified against actual Maven Central metadata and each service's real `pom.xml`, not assumed), two Dockerfile fixes, and the sequencing for flipping the CI gates live — so a developer agent can execute it without re-deriving any of the version research. **No application code changes are designed here** beyond dependency/Dockerfile/workflow edits; this is not a feature and introduces no new business logic, so most of the OWASP/pattern/compliance sections below are N/A by design, stated explicitly rather than skipped.

## 2. Affected services

| Service | Change | Why |
|---|---|---|
| `api-gateway`, `auth-service`, `ms-inventario`, `ms-pedidos`, `ms-envios`, `notification-service`, `ms-pagos` | `pom.xml`: bump `spring-boot-starter-parent` version. `api-gateway` additionally bumps its `spring-cloud.version` property. 5 of the 7 (`auth-service`, `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos` — the ones with `spring-boot-starter-data-jpa` + `postgresql`) additionally pin a `postgresql.version` property override. | Resolve the CRITICAL/HIGH CVE cluster; see §4 for the verified per-service diff. |
| `ms-pagos` | `Dockerfile`: add non-root `USER` directive (mirror `auth-service`). | Misconfig finding: only backend Dockerfile without a `USER` directive besides frontend. |
| `frontend/smartlogix-app` | `Dockerfile` + `nginx.conf`: switch to `nginxinc/nginx-unprivileged:alpine`, move internal listen port 80 → 8080. `docker-compose.yml`: update the frontend service's port mapping to `"5173:8080"`. | Misconfig finding: nginx final stage runs as root; stock `nginx:alpine` cannot bind port 80 as non-root without this base-image swap. |
| `.github/workflows/security.yml` | Flip all 9 Trivy steps to `exit-code: '1'`; remove `|| true` from `npm audit`. | The actual ask — done **after** a clean re-scan confirms the above, per §9 sequencing. |
| No other service/frontend app code changes. | | |

**Coordination classification.** This is **independent parallel changes**, not a Saga or a Facade. Each service's `pom.xml`/`Dockerfile` edit is self-contained (no service depends on another service's bump to build or deploy correctly, since there are no shared reactor/parent POM — confirmed below), and the workflow-file flip is a single independent config change gated on the others completing. There is no multi-step business transaction and no single service coordinating subsystems here.

**Verified project structure fact (load-bearing for the whole plan):** there is **no Maven multi-module reactor and no shared internal parent POM**. Each of the 7 `pom.xml` files independently declares `<parent><artifactId>spring-boot-starter-parent</artifactId><version>3.4.5</version></parent>`. This means the version bump must be applied **7 times, once per file**, with no shared property to change in one place. `api-gateway` is the only service with an explicit `<dependencyManagement>` block (importing `spring-cloud-dependencies`); none of the 7 pin `bcprov-jdk18on` or `postgresql` directly in `<dependencies>` today (verified by reading all 7 files in full) — `postgresql` is a bare `<dependency>` with no `<version>`, entirely inherited from the Boot parent's dependency management, and `bcprov-jdk18on` doesn't appear in any `pom.xml` at all (it's two hops transitive, see §4.2).

## 3. API contract

N/A. No endpoints are added, changed, or removed by this work.

## 4. Dependency version bump plan (verified against Maven Central + local `pom.xml` contents)

### 4.1 Spring Boot parent — target version: **3.5.16**

Verified via `spring-boot-starter-parent`'s `maven-metadata.xml` on Maven Central (queried live, current as of 2026-08-28): the 3.5.x line's latest patch is **3.5.16** (3.5.12 through 3.5.16 all post-date the baseline scan; 4.0.x exists but is a new major — see risk note below). `spring-boot-dependencies-3.5.16.pom` was pulled and inspected directly; it manages:

| Artifact | Version managed by Boot 3.5.16 | Baseline's minimum target | Meets/exceeds target? |
|---|---|---|---|
| `tomcat-embed-core` (`tomcat.version`) | 10.1.55 | 10.1.55 | Exact match |
| `jackson-bom` (covers `jackson-core`/`jackson-databind`) | 2.21.4 | 2.18.8 | Exceeds |
| `micrometer-core` (`micrometer.version`) | 1.15.12 | 1.15.12 | Exact match |
| `spring-security-bom` (`spring-security.version`) | 6.5.11 | 6.5.9 (api-gateway/auth-service only) | Exceeds |
| `spring-core`/`spring-webmvc`/`spring-expression` | (Spring Framework version pulled in transitively by Boot 3.5.16) | 6.2.11 / 6.2.19 | Confirmed exceeded — Boot 3.5.x lines pull Spring Framework 6.2.x patch releases well past 6.2.19 as of this Boot patch; developer should confirm exact resolved version in the build log but no manual override is needed |
| `netty.version` | 4.1.135.Final | 4.1.135/136.Final (api-gateway, ms-pedidos only) | Meets |
| `postgresql.version` | **42.7.11** | 42.7.12 | **Does not meet — needs explicit override, see §4.3** |
| `spring-data-*` (via `spring-data-bom`) | (3.5.x-line Spring Data release train) | 3.5.12 | Confirmed part of the Boot 3.5.x train, exceeds the 3.4.5-line version that produced the finding |

**Recommendation: stay on the 3.5.x line, do not jump to Boot 4.0.x.** Boot 4.0.x exists on Maven Central (up to 4.0.8 as of today) but is a new major version — it drops Java 17 as a baseline in places, changes some auto-configuration and actuator defaults, and (relevant here) pairs only with the Spring Cloud 2025.1.x train, which is itself still in early releases relative to 2025.0.x. A major-version jump is materially higher compatibility risk than this task's mandate (patch a known CVE list), and the 3.5.x line already resolves every finding in the baseline at or beyond the required version. This is a deliberate, stated scope boundary, not an oversight.

**Exact edit, all 7 files** (`services/api-gateway/pom.xml`, `services/auth-service/pom.xml`, `services/ms-inventario/pom.xml`, `services/ms-pedidos/pom.xml`, `services/ms-envios/pom.xml`, `services/notification-service/pom.xml`, `services/ms-pagos/pom.xml`):

```diff
   <parent>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-starter-parent</artifactId>
-    <version>3.4.5</version>
+    <version>3.5.16</version>
     <relativePath/> <!-- lookup parent from repository -->
   </parent>
```
(`ms-envios`, `notification-service`, `ms-pagos` have the identical `<parent>` block but without the `<relativePath/>` comment/whitespace formatting — same one-line version bump, no other structural change.)

### 4.2 `api-gateway`-specific bumps

`api-gateway`'s `pom.xml` has its own `<properties><spring-cloud.version>2024.0.1</spring-cloud.version></properties>` and a `<dependencyManagement>` import of `spring-cloud-dependencies`. This is the mechanism that must move, **not** a direct pin on `bcprov-jdk18on` or `spring-cloud-gateway-server-mvc` (neither appears anywhere in the pom).

**Verified transitive origin of `bcprov-jdk18on`** (via `mvn dependency:tree` against the current pom, not guessed):
```
spring-cloud-starter:4.2.1
  └─ spring-cloud-context:4.2.1
       ├─ spring-security-crypto:6.4.5
       └─ bcprov-jdk18on:1.78.1   (compile scope — this is the finding)
```
This confirms the baseline's note that it's "not from the Boot parent" — it comes from `spring-cloud-context`, which is part of the Spring Cloud release train's `spring-cloud-commons` module, itself pulled by `spring-cloud.version`.

**Verified fix, single property change:**
```diff
   <properties>
     <java.version>17</java.version>
-    <spring-cloud.version>2024.0.1</spring-cloud.version>
+    <spring-cloud.version>2025.0.3</spring-cloud.version>
   </properties>
```

**Compatibility verification (this is the one non-mechanical judgment call in this whole plan and was checked directly, not assumed):** Spring Cloud publishes a release-train-to-Boot compatibility statement in each train's reference docs. Pulled directly from GitHub (`spring-cloud/spring-cloud-release`, branch `2025.0.x`): `:spring-boot-version: 3.5.15` — i.e. the **2025.0.x train is the one built/tested against Spring Boot 3.5.x**, which is exactly the parent-bump target in §4.1 (2024.0.x, api-gateway's *current* train, pairs with Boot 3.4.x — matching what's in the repo today; the `main`/next train pairs with Boot 4.0.x, confirming 2025.1.x should **not** be used here since we are deliberately staying off Boot 4.0.x). **2025.0.3 is the latest 2025.0.x patch on Maven Central as of today.**

Resolved versions under `spring-cloud-dependencies:2025.0.3` (pulled and inspected directly, not inferred):
- `spring-cloud-gateway-dependencies` → `4.3.5`, which manages `spring-cloud-gateway-server-mvc:4.3.5` and (critically) still manages the exact artifact ID api-gateway currently depends on, `spring-cloud-starter-gateway-mvc` — **no artifact-ID rename is required**, it is deprecated-but-present in this train alongside the newer `spring-cloud-starter-gateway-server-webmvc`. 4.3.5 exceeds the baseline's flagged fix version (4.2.3) — no manual override needed, the property bump alone resolves it.
- `spring-cloud-commons` → `4.3.3` → `spring-cloud-context:4.3.3`, whose own POM was inspected directly: it now pins `bcprov-jdk18on` to **`1.80.2`** — exactly the baseline's target version. No explicit `bcprov-jdk18on` override is needed in `api-gateway`'s `pom.xml`; the property bump alone resolves it.
- `spring-security-crypto` also moves to `6.5.11` via this same chain, consistent with the Boot-driven Spring Security bump in §4.1 (no version conflict between the two BOMs — both converge above the target).

No other `api-gateway`-specific pom edits are needed for the CVE list.

### 4.3 `postgresql` explicit bump — 5 services

Boot 3.5.16 manages `postgresql.version` at `42.7.11`, one patch below the baseline's `42.7.12` target. `org.postgresql:postgresql` is not pinned anywhere in any of the 7 `pom.xml` files today (confirmed — bare `<dependency>` with no `<version>`), so this needs a property override, not a `<dependency><version>` addition (overriding via the Boot-recognized property is the standard mechanism and avoids fighting the BOM). Latest available on Maven Central today: `42.7.13`.

Affects the 5 services with `spring-boot-starter-data-jpa` + a `postgresql` runtime dependency: `auth-service`, `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos`. **`notification-service` is confirmed to have no `postgresql` dependency at all** (its `pom.xml` has no `spring-boot-starter-data-jpa`, no `postgresql` entry — verified by reading the file; matches the baseline's flag) — no change needed there for this specific CVE.

Exact edit, in each of the 5 services' `<properties>` block:
```diff
   <properties>
     <java.version>17</java.version>
+    <postgresql.version>42.7.13</postgresql.version>
     ...
   </properties>
```

### 4.4 Summary table — exact line changed per file

| File | Line(s) changed |
|---|---|
| `services/api-gateway/pom.xml` | `<version>3.4.5</version>` → `3.5.16` (parent); `<spring-cloud.version>2024.0.1</spring-cloud.version>` → `2025.0.3` |
| `services/auth-service/pom.xml` | parent `3.5.16`; add `<postgresql.version>42.7.13</postgresql.version>` |
| `services/ms-inventario/pom.xml` | parent `3.5.16`; add `<postgresql.version>42.7.13</postgresql.version>` |
| `services/ms-pedidos/pom.xml` | parent `3.5.16`; add `<postgresql.version>42.7.13</postgresql.version>` |
| `services/ms-envios/pom.xml` | parent `3.5.16`; add `<postgresql.version>42.7.13</postgresql.version>` |
| `services/ms-pagos/pom.xml` | parent `3.5.16`; add `<postgresql.version>42.7.13</postgresql.version>` |
| `services/notification-service/pom.xml` | parent `3.5.16` only |

No other `<dependency>` version tags need to change (`jjwt-*`, `resilience4j-spring-boot3`, `hypersistence-utils-hibernate-63`, `springdoc-openapi-starter-webmvc-ui`, jacoco/lombok — none appear in the baseline's finding list and none are declared with a version affected by this bump; they are independent explicit pins already, and out of this task's scope since the baseline didn't flag them).

## 5. Dockerfile fixes

### 5.1 `services/ms-pagos/Dockerfile` — mechanical, mirrors `auth-service`

Confirmed by reading both files: `ms-pagos`'s runtime stage is `eclipse-temurin:17-jre-alpine`, structurally identical to `auth-service`'s. No app-level reason found not to run as non-root (no root-owned file writes at runtime — `ms-pagos` talks to Postgres over the network and to the Flow API over HTTP; no local file/volume writes in `docker-compose.yml`'s `ms-pagos` service definition).

```diff
 FROM eclipse-temurin:17-jre-alpine
 WORKDIR /app
 COPY --from=builder /app/target/*.jar app.jar
+RUN addgroup -S spring && adduser -S spring -G spring
+USER spring
 EXPOSE 8086
 ENTRYPOINT ["java","-jar","app.jar"]
```

### 5.2 `frontend/smartlogix-app/Dockerfile` — real decision: switch to `nginxinc/nginx-unprivileged:alpine`

**Decision: option (a) from the task brief** — switch the final stage's base image and move the internal listen port from 80 to 8080, rather than trying to make stock `nginx:alpine`'s built-in `nginx` (uid 101) user bind port 80 (which would require `setcap`/capabilities plumbing this Dockerfile has no precedent for, and is the less standard path when a maintained unprivileged image already exists).

**Verified this is safe to do without breaking the deployed topology**, by reading all three relevant files:
- `docker-compose.yml`: frontend service maps **host** `5173` → **container** `80` (`"5173:80"`). Only the container-internal side needs to change; the host-facing `5173` (referenced from `README.md`, `nginx.conf`'s own internal redirect, and the `FLOW_URL_RETURN` env var) is untouched by this fix.
- `nginx.conf`: `listen 80;` is the only place a port number appears inside the config; the `/pago-resultado` redirect and the `/api/` proxy target `api-gateway:8080` (a different container, unaffected).
- No other file (CI workflow, other Dockerfiles, other compose services) references frontend container port 80 internally — grepped the full repo for `:80\b` and `EXPOSE 80` and found only this Dockerfile, this compose line, and this nginx.conf line.

`nginxinc/nginx-unprivileged:alpine`'s stock config listens on 8080 by default and runs as the `nginx` user out of the box — using it means we don't need custom `setcap` or a self-built low-privilege config; we already override its default config with our own `nginx.conf` (via `COPY nginx.conf /etc/nginx/conf.d/default.conf`), so we only need to change our own `listen` directive to match.

```diff
--- a/frontend/smartlogix-app/Dockerfile
+++ b/frontend/smartlogix-app/Dockerfile
@@ -6,9 +6,9 @@ COPY . .
 RUN npm run build

 # Stage 2: serve with nginx
-FROM nginx:alpine
+FROM nginxinc/nginx-unprivileged:alpine
 # Copy built files
 COPY --from=build /app/dist /usr/share/nginx/html
 # Copy nginx config for SPA routing
 COPY nginx.conf /etc/nginx/conf.d/default.conf
-EXPOSE 80
+EXPOSE 8080
 CMD ["nginx", "-g", "daemon off;"]
```

```diff
--- a/frontend/smartlogix-app/nginx.conf
+++ b/frontend/smartlogix-app/nginx.conf
@@ -1,5 +1,5 @@
 server {
-    listen 80;
+    listen 8080;
     server_name localhost;
     root /usr/share/nginx/html;
     index index.html;
```

```diff
--- a/docker-compose.yml
+++ b/docker-compose.yml
@@ -188,7 +188,7 @@
   frontend:
     build: ./frontend/smartlogix-app
     ports:
-      - "5173:80"
+      - "5173:8080"
     depends_on:
       - api-gateway
     networks:
```

No change needed to `README.md`'s `http://localhost:5173` references, `FLOW_URL_RETURN`, or anything else host-facing — the host port is unchanged, only the container-internal port moved. `nginxinc/nginx-unprivileged:alpine` (no version pin, mirroring the existing mutable-tag convention used for every other base image in this repo — a deliberate consistency choice, not an oversight; see §7 open question if the developer/reviewer wants pinning treated separately).

## 6. Design pattern fit

N/A / no new pattern. This is CI/build-pipeline and dependency-version hardening — it does not touch application code, does not add a new class of coordination between services, and does not extend or introduce any of the GoF/architectural patterns catalogued in `README.md` (Factory Method, Circuit Breaker, Observer, Strategy, Facade, Saga). Stated explicitly per this doc's own instructions rather than skipped.

## 7. Security requirements (OWASP Top 10 2021)

- **A01 Broken Access Control**: N/A. No endpoint, role, or ownership logic changes.
- **A02 Cryptographic Failures**: N/A directly — no secret/PII/payment-data handling code changes. Indirectly, the `spring-security-crypto`/BouncyCastle bump (§4.2) is itself a fix for cryptographic-library CVEs, which is the point of this whole change.
- **A03 Injection**: N/A. No query/shell/command construction changes.
- **A04 Insecure Design**: N/A for the dependency/Dockerfile changes themselves (no new abuse surface). The CI-gate flip itself has an insecure-design-adjacent consideration addressed in §9 (sequencing) — flipping `exit-code: '1'` before the baseline is actually clean would make every subsequent PR fail on pre-existing findings, training the team to bypass or ignore the gate, which defeats its purpose. This is why sequencing is designed explicitly rather than left implicit.
- **A05 Security Misconfiguration**: **Directly in scope.** This design *is* two A05 fixes (root-running containers) plus removing a security-control misconfiguration (report-only scanning gates). No new env vars, ports, or actuator exposure are introduced; the frontend's host-facing port (`5173`) is unchanged, only the internal container port moves.
- **A07 Auth Failures**: N/A. No JWT/session code changes. (Note for the developer, not a requirement: the Spring Security 6.4.5→6.5.11 bump is a *dependency* change, not a design change to `AuthFilter`/`InternalAuthFilter`; see §8 for what must be re-verified.)
- **A08 Software/Data Integrity**: **Directly relevant to the goal of this task.** The entire premise of flipping Trivy/`npm audit` to blocking is strengthening supply-chain integrity verification in CI. No deserialization changes are introduced by the version bumps themselves.
- **A09 Logging/Monitoring**: No change to what the application logs. Operationally, once CI blocks on these scans, a failed security-scan CI run *is* itself a signal worth treating as a monitored event (recommend the team watch the GitHub Security tab / Actions failures the same way `COMPLIANCE_CL.md` §4.4 already asks other security-relevant events to be watched) — not a new logging requirement in application code.
- **A10 SSRF**: N/A.

## 8. Compatibility risk assessment (verification-heavy — read before merging)

This is **not** a pure mechanical patch bump for two of the moving parts, and the developer should treat the acceptance bar as "full test suites pass + a live `docker-compose up --build` smoke test," not just "it compiles":

1. **Spring Security 6.4.5 → 6.5.11 (`api-gateway`, `auth-service`).** Spring Security minor version bumps have, in prior Spring Security release-note history, changed default behavior around things like: CSRF token handling defaults, `SecurityFilterChain` bean ordering edge cases, and CORS pre-flight handling nuances. This codebase leans on **custom filter chains** (`AuthFilter`, `InternalAuthFilter` per `internal-service-auth.md`) rather than Spring Security's stock form-login/session machinery, which is exactly the kind of setup most likely to be affected by a minor bump's default-behavior shifts, since custom filters interact with whatever the current default filter ordering/CORS/CSRF posture is. **Action for developer:** after bumping, run `auth-service`'s and `api-gateway`'s full test suites, then manually re-verify (via the existing smoke-test steps already documented in `jwt-httponly-cookie-migration.md` and `internal-service-auth.md`) that: login still sets the `sl_jwt` cookie with the same attributes, `AuthFilter` still rejects missing/expired/invalid tokens with the same status codes, `InternalAuthFilter` still accepts/rejects HMAC-signed internal calls correctly, and CORS preflight from the frontend origin still succeeds. No code is expected to need to change, but this must be *proven*, not assumed.
2. **Spring Boot 3.4 → 3.5 (all 7 services).** This is a minor version bump, not a patch — Spring Boot minor releases can rename/deprecate configuration properties and shift some auto-configuration defaults (e.g. actuator endpoint exposure defaults, observability/tracing auto-config). **Action for developer:** grep each service's `application.properties`/`application.yml` for any property Spring Boot 3.5's release notes/deprecation list flags (the developer should check the official 3.5 release notes and "Spring Boot 3.5 migration guide" at implementation time, since this design doc should not assume specific property names without reading each service's config file at that time); run each service's existing test suite; and specifically re-verify Resilience4j (`auth-service`, `ms-pedidos`) rate-limiter/circuit-breaker configuration still activates as expected (`resilience4j-spring-boot3:2.2.0` is an explicit, unaffected pin — not bumped by this design — but its *integration* with Boot's auto-configuration mechanism is worth a smoke test given the minor-version boundary).
3. **`spring-cloud.version` 2024.0.1 → 2025.0.3 (`api-gateway` only).** Two full Spring Cloud release trains at once. The gateway-server-mvc module itself moved from 4.2.1 → 4.3.5 (not just the CVE-fix patch 4.2.3) — a larger jump than the baseline's minimum target implied, because it's driven by the release-train alignment needed for the Boot 3.5 pairing (§4.2), not by hand-picking a version. **Action for developer:** run `api-gateway`'s full test suite (including the existing `RectificacionController`/`UsuarioDatosController`/gateway routing tests) and manually re-exercise every documented route in `README.md`'s gateway route table via `docker-compose up --build`, since gateway routing predicate/filter behavior is the most likely place a two-train jump could introduce a subtle behavior change.
4. **`postgresql` driver bump (5 services).** Low risk — this is a patch-level JDBC driver bump (42.7.11-class → 42.7.13), historically low-risk for this kind of point release. Still covered by each service's existing repository/integration tests.
5. **Frontend base image swap (`nginx:alpine` → `nginxinc/nginx-unprivileged:alpine`).** Behaviorally should be a no-op for anything this app relies on (same nginx build, different default user/port, and our own `nginx.conf` fully overrides the served config) — but the developer must verify via `docker-compose up --build` that: the frontend container starts successfully as the unprivileged user, serves `index.html`/static assets, the `/api/` reverse-proxy to `api-gateway:8080` still works, SPA routing (`try_files ... /index.html`) still works, and the security headers (`Content-Security-Policy` etc.) are still present on responses — none of these should be affected by the base-image/port change, but this is exactly the kind of "should be a no-op" claim that needs a live smoke test rather than being taken on faith.

**Recommended acceptance bar for the developer's PR** (beyond what's in §10): full `mvn test` (or equivalent) green on all 7 services, `npm test` green on frontend (unaffected by this change but should stay green), and one full `docker compose up --build` cycle exercising: register → login → an authenticated gateway-routed call → the Saga flow (`POST /api/sagas/pedido`) end-to-end → a Flow webhook-triggered payment confirmation, per `README.md`'s own documented smoke-test commands. This is explicitly heavier than a normal dependency-bump PR because two of the five moving parts are minor/train jumps, not patches.

## 9. Chilean compliance touchpoints

This change does not itself create, store, expose, or delete personal or payment data — it patches libraries and hardens CI. However, per `COMPLIANCE_CL.md` §3 (Ley 21.663, Marco de Ciberseguridad): "Gestión de riesgos de ciberseguridad basada en estándares reconocidos" and "continuidad operacional y resiliencia" are exactly the obligations this change advances — moving from report-only vulnerability scanning to a blocking CI gate is a direct, concrete instance of the "capacidad técnica de detectar, registrar y escalar" posture that document recommends SmartLogix maintain even absent a formal ANCI reporting obligation today. No `COMPLIANCE_CL.md` checklist row needs to flip to `✅ implementado`/`⚠️ parcial` as a result of this specific design (the checklist's items are about data-handling controls, not CI tooling), but it is reasonable for the merging PR to note in its description that it advances the Ley 21.663 risk-management posture referenced in §3 of that document. No Ley 21.719 (ARCO+, retention, breach-notification) obligations apply here — no personal-data code path changes.

## 10. Acceptance criteria

1. `mvn test` (or the project's standard test invocation) passes with zero failures on all 7 backend services after the `pom.xml` bumps in §4, using each service's real existing test suite (no test deletions/skips introduced by this change).
2. `npm test` continues to pass on the frontend (unaffected dependency-wise, but must not regress from the Dockerfile/compose edits since the app itself is unchanged).
3. `docker compose up --build` succeeds for all 8 containers (7 backend + frontend) with no crash-loop, and the full smoke test in §8's final paragraph completes successfully (register → login → authenticated call → Saga → Flow webhook confirmation).
4. `ms-pagos` container, once running, is confirmed to be executing as a non-root user (e.g. `docker compose exec ms-pagos whoami` returns `spring`, not `root`).
5. `frontend` container is confirmed to be executing as a non-root user (`docker compose exec frontend whoami` returns `nginx`, not `root`), and `curl http://localhost:5173/` from the host still returns the SPA's `index.html` with all six existing security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Content-Security-Policy`) present and unchanged in value.
6. A fresh, local re-run of the exact same three scans used for the baseline (Trivy fs scan with `vuln,secret,misconfig` scanners; Trivy image scan on all 8 freshly-rebuilt images using the exact CI build commands; `npm audit --audit-level=high`) shows **zero CRITICAL/HIGH findings**, OR every remaining finding has a documented suppression in the PR description with: the specific CVE ID, why it's a false positive or not exploitable in this deployment context, and — if using Trivy's `.trivyignore` mechanism — an inline comment in that file citing the same reasoning. A finding must not be silently ignored by lowering severity thresholds or narrowing scan scope.
7. Negative/abuse case — **gate actually blocks**: after the workflow-file flip (§11), a PR is opened that reintroduces a single known-CRITICAL dependency version (e.g. temporarily reverting one service's `pom.xml` parent version in a throwaway test branch) and CI is confirmed to **fail** the `dependency-scan`/`docker-image-scan` job, not just report it — proving `exit-code: '1'` is actually wired, not just changed in text.
8. Negative/abuse case — **npm audit gate actually blocks**: a throwaway test branch adds a single package with a known HIGH-severity advisory to `package.json`/`package-lock.json`, and the `frontend-audit` CI job is confirmed to fail (non-zero exit), not silently pass, proving `|| true` was actually removed and not just visually deleted while some other pass-through remains.
9. `git diff` for this change touches exactly: 7 `pom.xml` files, `services/ms-pagos/Dockerfile`, `frontend/smartlogix-app/Dockerfile`, `frontend/smartlogix-app/nginx.conf`, `docker-compose.yml`, and `.github/workflows/security.yml` — no application source files (`.java`, `.tsx`/`.ts`, etc.) are touched, confirming this stayed a dependency/infra-only change as scoped.
10. `README.md`'s references to `localhost:5173` continue to work unchanged (host port untouched) — verified as part of criterion 3's smoke test, not a separate doc change.

## 11. Sequencing (explicit design, per the user's stated requirement)

**The dependency/Dockerfile bumps and the `security.yml` gate flip must land in that order, verified between them — not in the same change, and not gate-first.**

1. **Step 1 (this design's primary deliverable):** land the `pom.xml`, `Dockerfile`, and `docker-compose.yml`/`nginx.conf` changes from §4–§5, with `security.yml` left untouched (`exit-code: '0'`, `|| true` still present). This lets CI run the *existing* report-only scans against the *fixed* dependency set without risk of the PR itself being blocked by a gate that isn't proven clean yet — and gives a real CI-run baseline-vs-fixed diff to look at in the Security tab.
2. **Step 2 (verification gate):** re-run the exact same three scans that produced the original baseline (Trivy fs, all 8 Trivy image scans, `npm audit`) against the branch from Step 1, either by inspecting the Step-1 PR's own (still report-only) CI run in the GitHub Security tab, or by re-running locally with the same flags. Confirm acceptance criterion 6 (zero CRITICAL/HIGH, or every remaining item explicitly suppressed with documented reasoning).
3. **Step 3 (the actual ask):** only once Step 2 confirms a clean (or fully, defensibly suppressed) result, land a second, separate change that flips all 9 Trivy `exit-code` values to `'1'` and removes `npm audit`'s `|| true`. Validate with acceptance criteria 7–8 (the gate must be proven to actually block, not just edited).

**Why not do it in one PR:** if Step 1 and Step 3 are combined and something in the baseline synthesis was slightly stale, wrong, or missed a finding (e.g. a new CVE published between the baseline scan and this PR merging — the schedule already re-scans daily per the workflow's cron), the very same PR that's supposed to turn the gate on would be the first thing it blocks, with no clean prior state to fall back to and diagnose from. Splitting into two changes means Step 3 is a low-risk, easily-revertable, single-purpose CI config change landing against an already-verified-clean baseline — matching this repo's existing pattern of keeping diffs reviewable and single-purpose (see e.g. `arco-remaining-rights.md`'s own scope-splitting rationale).

## 12. Open questions

1. **Should `nginxinc/nginx-unprivileged:alpine` be pinned to a specific nginx version rather than tracking `:alpine`?** This design keeps the existing mutable-tag convention (every current base image in this repo, backend and frontend, uses an unpinned/mutable tag) for consistency, but pinning would make the "fresh pull resolves OS-package CVEs" remediation step from the baseline more reproducible/auditable at the cost of needing a recurring manual bump. Not decided here — flagging for the human/orchestrator since it's a repo-wide convention question, not specific to this fix.
2. **Exact resolved Spring Framework / Spring Data patch versions under Boot 3.5.16** were not individually pinned down artifact-by-artifact in this design (only confirmed they're part of the 3.5.x train and therefore exceed the baseline's flagged minimums) — the developer should confirm via `mvn dependency:tree` per service after the bump that no individual artifact (e.g. through an unusual exclusion or an explicit pin this design missed) ends up below the baseline's target, since a BOM bump guarantees the *managed* version moves but not that every service's resolved tree has no stray override. None were found during this design's `pom.xml` review, but this is worth a final `dependency:tree` diff spot-check per service before merging Step 1.
3. **Whether to also rename `api-gateway`'s `spring-cloud-starter-gateway-mvc` dependency to the newer `spring-cloud-starter-gateway-server-webmvc` artifact ID** — not required (the old artifact ID still resolves correctly in the 2025.0.3 train, confirmed in §4.2), but Spring Cloud's own docs mark the old name deprecated. Out of scope for a security-only PR; flagging as a reasonable future cleanup, not bundled here to keep this change minimal and reviewable.
4. **Whether `resilience4j-spring-boot3:2.2.0` (explicit pin in `auth-service`/`ms-pedidos`) should also be bumped** for its own sake — it wasn't flagged in the baseline's finding list, so this design leaves it untouched, but the developer should note in the PR if `mvn test` surfaces any Resilience4j/Boot-3.5-autoconfiguration friction (§8 point 2) that might indicate its own version needs to move too.
