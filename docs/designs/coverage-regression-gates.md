# Coverage-Regression CI Gates (Backend JaCoCo + Frontend Vitest)

## 1. Summary

SmartLogix's CI currently *runs* tests for all 7 backend services and the frontend, but nothing
fails the build when test coverage regresses. `backend-ci.yml` runs `./mvnw -B clean verify` per
service with no coverage plugin bound to `check`; `frontend-ci.yml` runs `npm run test:coverage`
but `vite.config.js` has no `coverage.thresholds` block, so a coverage drop is invisible until a
human reads the log. This design wires JaCoCo `check` (bound to the `verify` phase, which CI
already runs) into all 7 backend `pom.xml` files, and a Vitest `coverage.thresholds` block into
`vite.config.js`, with floors set at each component's **actually measured** current coverage minus
a small fixed buffer — a regression gate, not an aspirational target. All coverage numbers below
were measured live in this session (JaCoCo XML/CSV reports from real `mvn test` runs against a
real Postgres, and a real `vitest run --coverage` run), not estimated. No test files or application
code were changed to produce this design; every experiment described in §4 (below) was performed in
a disposable scratch copy or was reverted immediately after empirical verification, and the tracked
repository is unmodified (`git status` clean).

## 2. Affected services

| Component | Change | Why |
|---|---|---|
| `api-gateway` | Add `jacoco-maven-plugin` (new — not present today) + `check` execution to `pom.xml` | No coverage gate today |
| `auth-service` | Same | Same |
| `ms-inventario` | Add `check` execution to its **existing** `jacoco-maven-plugin` block (already has `prepare-agent`+`report`) | Coverage already measured, never enforced |
| `ms-pedidos` | Same | Same |
| `ms-envios` | Same | Same |
| `notification-service` | Add `jacoco-maven-plugin` (new) + `check` execution | No coverage gate today |
| `ms-pagos` | Add `jacoco-maven-plugin` (new) + `check` execution | No coverage gate today |
| `frontend` | Add `coverage.thresholds` block to `vite.config.js`'s `test` config | Reporting-only today per `frontend-test-infrastructure.md` §5/§6 |
| `.github/workflows/backend-ci.yml` | **No change required** — see §4.4, empirically confirmed | `mvn clean verify` already executes the `verify` phase; binding `jacoco:check` there is sufficient |
| `.github/workflows/frontend-ci.yml` | **No change required** — see §5.4, empirically confirmed | `npm run test:coverage` already runs `vitest run --coverage`, which now exits non-zero on a threshold miss |

**Coordination classification: independent parallel changes, not a Saga or a Facade.** Each of the
7 backend services has its own `pom.xml`, its own build, and its own CI matrix leg — there is no
cross-service transaction or shared state. The frontend change is likewise self-contained. The only
cross-cutting concern is *consistency of mechanics* (same JaCoCo version, same rule shape, same
buffer policy) across the 7 backend services, which is a repetition concern for whoever implements
this, not a runtime coordination concern.

## 3. API contract

**N/A.** No new or changed HTTP endpoints. This is build-tooling/CI configuration only.

## 4. Backend: measured baseline, floors, and JaCoCo wiring

### 4.1 How coverage was actually measured (not estimated)

For each service, in a disposable scratch copy of the service source (the tracked `services/<n>`
directories were never written to — their `target/` directories were pre-existing and root-owned
from an earlier session, which is itself worth flagging to the human, see §9):

1. Started a real PostgreSQL instance reachable at `localhost:5432` (a pre-existing local Postgres
   service on this machine, not part of the SmartLogix stack) and created the 5 per-service
   databases (`auth_db`, `inventario_db`, `pedidos_db`, `envios_db`, `pagos_db`) that each JPA-backed
   service's `application.properties` expects — the same role CI's ephemeral
   `postgres:16-alpine` service container plays for `backend-ci.yml`.
2. Ran each service's real test suite with JDK 17 (Temurin 17.0.19, matching CI's
   `actions/setup-java@v4` with `java-version: '17'`, `distribution: 'temurin'`) and JaCoCo attached:
   - `ms-inventario`, `ms-pedidos`, `ms-envios` already have `jacoco-maven-plugin` 0.8.11 bound to
     `prepare-agent` + `report@test` — a plain `mvn clean test` was sufficient to produce
     `target/site/jacoco/jacoco.csv`.
   - `api-gateway`, `auth-service`, `notification-service`, `ms-pagos` have no JaCoCo plugin at all
     today — coverage was measured by invoking the plugin directly from the CLI, with no `pom.xml`
     change needed for measurement: `mvn org.jacoco:jacoco-maven-plugin:0.8.11:prepare-agent test
     org.jacoco:jacoco-maven-plugin:0.8.11:report`.
3. Computed INSTRUCTION/LINE/BRANCH ratios from the generated `jacoco.csv` (sum of
   `*_COVERED`/(`*_COVERED`+`*_MISSED`) across all classes in the module).
4. All 7 services' full test suites passed (0 failures) during measurement.

### 4.2 Measured baseline (real numbers, this session)

| Service | Tests | Instruction cov. | Line cov. | Branch cov. |
|---|---|---|---|---|
| `api-gateway` | 47 | 82.27% | 81.94% | 67.27% |
| `auth-service` | 44 | 86.97% | 83.06% | 83.33% |
| `ms-inventario` | 17 | 56.36% | 59.89% | 60.42% |
| `ms-pedidos` | 35 | 44.81% | 42.54% | 47.50% |
| `ms-envios` | 19 | 67.39% | 65.94% | 66.67% |
| `notification-service` | 8 | 79.75% | 73.33% | 87.50% |
| `ms-pagos` | 22 | 57.89% | 60.31% | 48.53% |

**Finding worth flagging explicitly (see also §9 Open Questions):** `ms-pedidos` (44.81%
instruction / 42.54% line) and, to a lesser extent, `ms-inventario` (56.36%/59.89%) are the two
weakest-tested services. Neither is below the 20% "gate would be meaningless" threshold called out
in this task's brief, so this design does **not** propose skipping their gate — but a regression
floor at ~40-55% still leaves a lot of `ms-pedidos` code (notably large chunks of `SagaOrchestrator`
compensation-path branches, per the branch-coverage number) permanently un-forced-to-improve. This
is a real gap the human should be aware of, not something for the Architect to unilaterally fix by
setting an artificially high floor (which would immediately break CI on ms-pedidos) or an
artificially low one (which would defeat the purpose).

### 4.3 Margin policy: floor = floor(measured%) − 2 percentage points

For each metric and each service: round the measured percentage **down** to the nearest whole
percent, then subtract **2 percentage points**, floored at 0.

- **Why round down first:** avoids a floor that is *above* a borderline measurement due to normal
  floating-point/rounding noise between JaCoCo report runs (e.g. a measured 86.97% should not
  produce a floor of 87%).
- **Why a flat 2-point buffer (not a percentage-of-baseline buffer, not zero buffer):** a 2-point
  absolute buffer is large enough to absorb the kind of coverage "wobble" that comes from adding a
  handful of new lines in an unrelated PR that happen to include a branch nobody tests yet (e.g.
  fixing a typo in a validation message inside an `if` block adds one branch), without being so
  loose that a real regression (a developer accidentally deleting or disabling several tests, or
  adding a large untested method) slips through. This repo's smallest per-service instruction count
  is `notification-service` at 237 total instructions — at that size, 2 percentage points is
  roughly 4-5 instructions, tight enough to still catch a meaningfully-sized untested addition. A
  zero-point buffer (floor = exactly measured) was rejected because it would fail CI on the *very
  next* PR to any service if even one previously-covered branch/line becomes untested for an
  unrelated, legitimate reason (e.g. an `if (dev-only)` guard tightened by a security fix that
  removes a code path entirely, shifting the denominator).
- **Why INSTRUCTION and LINE, not BRANCH:** the two already-instrumented services
  (`ms-inventario`, `ms-pedidos`) declare `sonar.coverage.jacoco.xmlReportPaths` for a
  (not-yet-configured-here) SonarQube integration, which conventionally consumes
  instruction/line coverage — gating on the same two metrics keeps this design consistent with
  that existing convention rather than introducing a third metric convention. Branch coverage is
  reported in every service's JaCoCo HTML/XML output (visible to any human reviewing it) but is
  **not gated** in this design — several services (`ms-pedidos` 47.5%, `ms-pagos` 48.53%) have
  branch coverage meaningfully lower than their instruction/line numbers, largely from
  Saga-compensation and webhook-failure branches that are harder to reach in unit tests; gating on
  branch now, at these low measured values, would either be a near-meaningless floor or would
  immediately force disproportionate new test-writing effort as a side effect of an unrelated
  infra PR. This is called out as a deliverable trade-off, not silently dropped — see §9.

### 4.4 Computed floors

| Service | Instruction floor | Line floor |
|---|---|---|
| `api-gateway` | 0.80 (80%) | 0.79 (79%) |
| `auth-service` | 0.84 (84%) | 0.81 (81%) |
| `ms-inventario` | 0.54 (54%) | 0.57 (57%) |
| `ms-pedidos` | 0.42 (42%) | 0.40 (40%) |
| `ms-envios` | 0.65 (65%) | 0.63 (63%) |
| `notification-service` | 0.77 (77%) | 0.71 (71%) |
| `ms-pagos` | 0.55 (55%) | 0.58 (58%) |

### 4.5 JaCoCo wiring — exact XML

**Plugin version: `0.8.11`** — already the version pinned in `ms-inventario`/`ms-pedidos`/
`ms-envios`; reusing it everywhere avoids introducing a second JaCoCo version into the repo for no
reason. Confirmed compatible with JDK 17 and Spring Boot 3.5.16's Surefire setup — empirically, not
just by reading changelogs (see §4.6).

**For `api-gateway`, `auth-service`, `notification-service`, `ms-pagos`** (no existing JaCoCo
block) — add this plugin under `<build><plugins>`, with each service's own computed thresholds
substituted for `<INSTR_FLOOR>` / `<LINE_FLOOR>`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum><INSTR_FLOOR></minimum>
                            </limit>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum><LINE_FLOOR></minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**For `ms-inventario`, `ms-pedidos`, `ms-envios`** (already have `prepare-agent` + `report@test`) —
add only the third `<execution>` (`jacoco-check`, exact same shape as above, with that service's own
floors) inside the existing `<executions>` block. Do not duplicate the `prepare-agent`/`report`
executions.

**Why `check` is bound to `phase>verify</phase>` and not `test`:** `jacoco:check` must run *after*
the test-produced `target/jacoco.exec` exists (written during the `test` phase by the
`prepare-agent`-instrumented JVM) — binding it to `verify` (which runs immediately after `test` in
the default Maven lifecycle) is the standard, documented JaCoCo pattern, and is also the phase
`backend-ci.yml` already invokes (`clean verify`) — see §4.7.

**No `dataFile`/`destFile` overrides needed:** all three executions default to
`${project.build.directory}/jacoco.exec`, which was confirmed empirically to work with zero extra
configuration (see §4.6).

### 4.6 Empirical proof the mechanism works (not assumed)

Performed in a scratch copy of `auth-service` (chosen because it had *no* existing JaCoCo config —
the harder case) and cross-checked on a scratch copy of `ms-envios` (chosen because it already had
partial JaCoCo config in a different XML formatting style, to rule out a formatting-specific bug):

1. **Unreachable threshold → build fails.** Set `<minimum>0.99</minimum>` (auth-service measured
   ~87%) and ran `mvn clean verify`. Result: `BUILD FAILURE`, exit code 1, with the exact JaCoCo
   message:
   ```
   [WARNING] Rule violated for bundle auth-service: instructions covered ratio is 0.86, but expected minimum is 0.99
   [ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.11:check (jacoco-check) on project auth-service: Coverage checks have not been met.
   ```
   Same result confirmed for `ms-envios` at an unreachable `0.99` floor (measured ~67%): exit 1.

2. **Real computed floor → build passes.** Set `<minimum>0.84</minimum>` (auth-service's actual
   computed floor) and re-ran `mvn clean verify`: exit code 0, all tests still pass, no coverage
   error. Same confirmed for `ms-envios` at its real `0.65` floor: exit code 0.

3. **A concrete, file-free way to induce a regression for QA reproduction (see §8):** added a
   `<configuration><excludes><exclude>com/ecommerce/auth/service/AuthService.class</exclude>
   </excludes></configuration>` to the JaCoCo plugin (excluding a heavily-tested class from
   instrumentation, without touching any test file or the class itself) and re-ran `mvn clean
   verify` at the real `0.84` floor: build failed with
   `instructions covered ratio is 0.83, but expected minimum is 0.84` — i.e. excluding one
   well-covered class was enough to tip a passing build into a failing one, confirming the gate is
   sensitive at the margin, not just at extremes.

All scratch-copy edits were made outside the tracked repository (`/tmp/.../scratchpad/coverage-build/`)
and never touched `services/*/pom.xml` in the real working tree — `git status` is clean.

### 4.7 CI wiring — empirically confirmed: no `backend-ci.yml` change needed

`backend-ci.yml`'s `Build and run tests` step already runs `./mvnw -B clean verify` (not `test`) for
every service in the matrix. Since `jacoco:check` is bound to the `verify` phase (§4.5), Maven's
default lifecycle guarantees it runs automatically as part of that existing command — no new step,
no new job, no matrix change. Once `pom.xml` gets the plugin block above, the *existing* CI command
starts enforcing the gate with no YAML edits. (Confirmed by extension of §4.6: the same `mvn clean
verify` invocation used there is exactly what CI already runs.)

The only other thing to double check when implementing (not requiring a design change, just a
sanity check the developer should perform once): confirm no `-DskipTests`/`-Djacoco.skip=true`
flag is added anywhere in the matrix `env:` or step `run:` in the future — none exists today.

## 5. Frontend: measured baseline, floors, and Vitest wiring

### 5.1 Current state re-confirmed

`vite.config.js`'s `test` block has no `coverage` key at all (confirmed by reading the file).
`frontend-ci.yml` already runs `npm run test:coverage` (`vitest run --coverage`) as a step between
`Lint` and `Build`. `package.json` already has `@vitest/coverage-v8` as a devDependency. This
matches the brief's assumption exactly.

### 5.2 Measured baseline (real numbers, this session)

Ran `npm run test:coverage` for real (Node 26.7.0 locally vs. the CI-pinned Node 22 — see §9 for why
this is flagged, not assumed identical):

```
Test Files  8 passed (8)
     Tests  55 passed (55)

Statements   : 92.5%  ( 321/347 )
Branches     : 72.22% ( 208/288 )
Functions    : 88.03% ( 103/117 )
Lines        : 93.5%  ( 288/308 )
```

Note: the frontend has grown well past the "3 baseline components" scope described in
`frontend-test-infrastructure.md` — 8 test files now exist (`App`, `Login`, `MisDatos`, `Dashboard`,
`Envios`, `Inventario`, `Pedidos`, `PagoResultado`), consistent with the later `frontend visual
design revamp` commit. This design measures and gates on the *actual current* state, not the older
doc's narrower scope.

### 5.3 Margin policy and computed floors

Same round-down-then-minus-2 policy as backend (§4.3), applied per metric (not a single blanket
number) because branch coverage (72.22%) is meaningfully lower than statements/lines (~92-93%) — a
single blanket threshold would either be too loose on statements/lines or immediately red on
branches.

| Metric | Measured | Floor |
|---|---|---|
| Statements | 92.5% | 90% |
| Branches | 72.22% | 70% |
| Functions | 88.03% | 86% |
| Lines | 93.5% | 91% |

**Global, not per-file.** Vitest's `coverage.thresholds` defaults to a global (whole-suite)
aggregate unless `perFile: true` is set. This design deliberately does **not** set `perFile: true`:
several existing files are already below the global floors on branches specifically (e.g.
`MisDatos.jsx` 65.3%, `Pedidos.jsx` 60.25%, `Dashboard.jsx` 65.38%) — a per-file gate would fail the
build today, on the very commit that introduces it, which contradicts "regression gate at current
baseline." A global aggregate gate matches the stated goal (catch a suite-wide regression) without
requiring every existing file to individually clear a bar it doesn't clear today.

### 5.4 Exact `vite.config.js` change

```js
test: {
  environment: "jsdom",
  globals: true,
  setupFiles: "./src/test/setup.js",
  css: false,
  coverage: {
    provider: "v8",
    thresholds: {
      lines: 91,
      branches: 70,
      functions: 86,
      statements: 90,
    },
  },
},
```

`provider: "v8"` is explicit even though it's already the default (via `@vitest/coverage-v8` being
the only coverage package installed) for readability/futureproofing — matches this repo's existing
style of being explicit about provider choices (see `frontend-test-infrastructure.md`'s stated
reasons for choosing jsdom/v8 explicitly rather than relying on defaults).

### 5.5 Empirical proof (performed for real, per the brief's explicit requirement)

Directly on the real `frontend/smartlogix-app/vite.config.js`, then reverted to its original
content immediately after (confirmed byte-identical via `diff` against a pre-change copy; `git
status` on `frontend/` is clean — this file is a deliverable for the developer agent to actually
apply, not something the Architect leaves committed):

1. **Unreachable threshold (100/100/100/100) → non-zero exit.** `npm run test:coverage` printed the
   real coverage table (unchanged: 92.5/72.22/88.03/93.5) followed by:
   ```
   ERROR: Coverage for lines (93.5%) does not meet global threshold (100%)
   ERROR: Coverage for functions (88.03%) does not meet global threshold (100%)
   ERROR: Coverage for statements (92.5%) does not meet global threshold (100%)
   ERROR: Coverage for branches (72.22%) does not meet global threshold (100%)
   ```
   Process exit code: **1**. This confirms a threshold miss genuinely fails `vitest run --coverage`
   (not just prints a warning), which is exactly the command `frontend-ci.yml`'s `Test` step already
   runs — so the existing step starts blocking on coverage with zero workflow changes.

2. **Real computed floors (91/70/86/90) → exit 0.** Same command, thresholds set to the values in
   §5.4: all four metrics reported as before, no `ERROR:` lines, exit code **0**.

3. **A concrete, file-free way to induce a regression for QA reproduction (see §8):** re-ran with
   `npx vitest run --coverage --exclude "**/Pedidos.test.jsx" --exclude "**/node_modules/**"` (the
   real thresholds from §5.4 still in place) — excluding one entire existing, unmodified test file
   from the run via a CLI flag, touching zero files. Result: coverage dropped to
   Statements 69.16% / Branches 55.9% / Functions 61.53% / Lines 70.12%, all four below their
   floors, exit code **1**, with one `ERROR:` line per metric.

## 6. Design pattern fit

**No GoF/architectural pattern applies, and none should be introduced.** This is CI/build-tooling
configuration — the same category as `flyway-migration-baseline.md` and
`frontend-test-infrastructure.md`, both of which correctly state "N/A" here rather than force-fit a
pattern. There is no request/response flow, no cross-service call, and no runtime behavior change
for this design to express via Repository/Factory/Strategy/Observer/Facade/Saga/Circuit Breaker —
those patterns govern how SmartLogix's *application code* is structured, not how its build pipeline
measures test coverage.

## 7. Security requirements (OWASP Top 10 2021)

- **A01 Broken Access Control:** N/A. No endpoint, no caller, no ownership check involved.
- **A02 Cryptographic Failures:** N/A. No secrets, PII, or payment data are touched, stored, or
  transmitted by this change. Coverage percentages and JaCoCo/Vitest HTML reports contain file
  names, class names, and line numbers from the (already-public-to-the-team) source tree — no
  runtime data.
- **A03 Injection:** N/A. No raw SQL, dynamic query, or shell/command construction is introduced.
  (The scratch-copy verification in §4.6/§5.5 used only static, hand-written XML/JS edits and
  standard Maven/npm CLI invocations — no string-built commands from external input.)
- **A04 Insecure Design — abuse cases (retry, double-submit, race condition):** N/A in the sense
  this brief's concern (retry/double-submit/race conditions on a live endpoint) doesn't apply to a
  CI gate. The closest analogous concern — "can a developer silently bypass the gate?" — is
  addressed structurally: the gate is bound to `mvn verify`/`vitest run --coverage`, the exact
  commands already required to pass CI; bypassing it would require either editing the threshold
  values (a visible, reviewable `pom.xml`/`vite.config.js` diff) or adding a `-Djacoco.skip=true`/
  equivalent flag to the CI workflow itself (also a visible, reviewable diff to a file this design
  explicitly does not need to touch — any future PR that *does* touch `backend-ci.yml` or
  `frontend-ci.yml` to add such a flag should draw reviewer scrutiny).
- **A05 Security Misconfiguration:** N/A. No new environment variables, no new exposed ports, no
  actuator endpoint changes. The only "configuration" surface is the coverage threshold numbers
  themselves, which are build-time constants checked into version control (not runtime config).
- **A07 Auth Failures:** N/A. No JWT/session handling is touched.
- **A08 Software/Data Integrity:** N/A. No webhook signature/HMAC verification or external-input
  deserialization is touched. (JaCoCo/Vitest coverage instrumentation operates on the project's own
  compiled bytecode/transformed source, not on external or user-supplied input.)
- **A09 Logging/Monitoring:** N/A for audit-relevant logging (`docs/COMPLIANCE_CL.md` §4.4 concerns
  are about *application* logs reconstructing an incident, not CI build logs). One practical note,
  not a compliance requirement: CI logs and JaCoCo HTML reports render full source file contents —
  this is no different from what `git blame`/the repo browser already exposes to anyone with repo
  access, so it introduces no new disclosure. Nothing here should ever log passwords, tokens, or
  card numbers, and this design does not touch any code path that does.
- **A10 SSRF:** N/A. No outbound HTTP call is added or modified by this change.

## 8. Chilean compliance touchpoints

**N/A — explicitly.** This design creates no new data flow, storage, or processing of personal or
payment data. It does not touch `auth-service`'s ARCO+ endpoints, `ms-pagos`'s payment data, or any
table that stores customer/PYME data. `docs/COMPLIANCE_CL.md`'s obligations (Ley 21.719
access/deletion rights, Ley 21.663 incident-reporting logging, retention limits) are unaffected
because no personal data is created, stored, exposed, or deleted by adding a coverage gate to the
build pipeline.

## 9. Acceptance criteria

For **each of the 7 backend services**, QA must verify both of the following (using the same
environment CI uses: JDK 17 Temurin, a real Postgres reachable as `${DB_HOST}:5432` with the
service's own database created — CI already provides this via the `postgres:16-alpine` service
container; locally, `docker compose up -d postgres-<n>` plus the service's own env vars is
equivalent):

1. **Positive (current coverage passes).**
   - Apply the `pom.xml` change from §4.5 (correct floors for that service, from §4.4) with no
     other changes.
   - Run `./mvnw -B clean verify`.
   - **Pass condition:** exit code 0, `BUILD SUCCESS`, and `target/site/jacoco/jacoco.html` exists
     and reflects the same ballpark instruction/line percentages as §4.2 (allowing for natural
     drift since measurement — the point is "still comfortably above floor," not an exact match).
2. **Negative (induced drop actually fails, with a clear message).**
   - Without touching any test file or the class under test, temporarily add an `<excludes>` entry
     to the service's `jacoco-maven-plugin` `<configuration>` (plugin-level, so it applies to both
     `report` and `check`) naming one of that service's most-tested classes — for each service, use
     the class backing its largest test file per §4.2's "Tests" column context (e.g.
     `com/ecommerce/auth/service/AuthService.class` for `auth-service`,
     `com/smartlogix/pedidos/saga/SagaOrchestrator.class` for `ms-pedidos`,
     `com/smartlogix/inventario/service/AlertaService.class` for `ms-inventario`, and the
     equivalent primary service/orchestrator class for the other 4 services — QA should identify
     the analogous class via `grep -l` on the largest `*Test.java`/`*Tests.java` file's
     `@InjectMocks`/`@Autowired` target if unsure).
     **Caveat found by QA (2026):** the excluded class must be covered *above* the bundle's overall
     ratio, not merely "large" or "has the most test lines" — excluding a class whose own coverage
     is *below* the bundle average actually raises the aggregate ratio (removes a below-average
     number from both numerator and denominator), producing a false negative where the build still
     passes and the gate looks broken when it isn't. This bit `ms-envios` (`EnvioService`) and
     `notification-service` (`NotificationListener`) specifically; `InternalAuthFilter` — the
     largest, best-covered class in both — worked correctly. Before excluding a class, sanity-check
     that its own line/instruction coverage in the last `jacoco.html` run is at or above the
     service's overall percentage.
   - Run `./mvnw -B clean verify` again.
   - **Pass condition (i.e. the *gate* is working, even though the build fails):** exit code
     non-zero, `BUILD FAILURE`, and the log contains a line matching
     `Rule violated for bundle <artifactId>: instructions covered ratio is 0.NN, but expected
     minimum is 0.MM` (or the equivalent `LINE` counter line) followed by
     `Failed to execute goal org.jacoco:jacoco-maven-plugin:...:check ... Coverage checks have not
     been met.` A silent pass, a build failure from an unrelated cause (e.g. compilation error), or
     no coverage-specific message in the log is a **fail** of this acceptance criterion.
   - Revert the temporary `<excludes>` addition before merging anything.

For the **frontend**:

3. **Positive (current coverage passes).**
   - Apply the `vite.config.js` change from §5.4 with no other changes.
   - Run `npm run test:coverage`.
   - **Pass condition:** exit code 0, no `ERROR: Coverage for ... does not meet ... threshold` lines
     in the output.
4. **Negative (induced drop actually fails, with a clear message).**
   - Without editing any file, run
     `npx vitest run --coverage --exclude "**/Pedidos.test.jsx" --exclude "**/node_modules/**"`
     (or substitute any other single existing `*.test.jsx` file — `Pedidos.test.jsx` is a good
     choice because it is one of the larger suites, per §5.5).
   - **Pass condition (the *gate* is working):** exit code non-zero, and the output contains at
     least one `ERROR: Coverage for <metric> (<measured>%) does not meet global threshold
     (<floor>%)` line. A silent pass is a **fail** of this acceptance criterion.

**Cross-cutting:**

5. No `*.test.jsx`, `*Test.java`, or `*Tests.java` file exists in the diff that implements this
   design — verified via `git diff --name-only <base>...<head> -- '**/*Test*.java' '**/*.test.jsx'`
   returning empty (aside from the temporary, reverted-before-merge exclusion edits used to prove
   criteria 2/4, which must never appear in the merged diff).
6. Neither `backend-ci.yml` nor `frontend-ci.yml` is modified by this change (§4.7, §5.5 confirmed
   this is unnecessary) — verified via `git diff --name-only` on the PR.
7. All 7 backend `pom.xml` files and `vite.config.js` are the only files changed (plus this design
   doc).

## 10. Open questions

1. **`ms-pedidos` and `ms-inventario` have the weakest measured coverage (44.81%/56.36%
   instruction).** This design gates them at their own current (low) baseline rather than raising
   the bar, per the brief's explicit instruction not to unilaterally decide to skip or inflate a
   service's gate. The human should decide whether a separate, later initiative should mandate
   raising `ms-pedidos`' Saga-compensation-path coverage specifically, since that's the service with
   the most business-critical untested branches (compensation logic) per §4.2's branch-coverage
   column.
2. **Branch coverage is measured and visible in every report but not gated.** If the team later
   wants a branch-coverage gate too, `ms-pedidos` (47.5%) and `ms-pagos` (48.53%) would need real
   new tests first — their current branch coverage is well below even a generous floor. Flagging
   this now so it isn't assumed "free" to add later.
3. **Frontend coverage was measured with Node 26.7.0 locally; CI pins Node 22 (`frontend-ci.yml`,
   `package.json`'s `engines.node: "22.x"`).** V8 coverage instrumentation is generally stable
   across recent Node versions, and the *mechanism* (thresholds causing non-zero exit) was verified
   independent of the exact Node version, but the human/developer implementing this should re-run
   `npm run test:coverage` once in the actual Node-22 CI environment before merging, in case the
   exact percentages shift by a fraction of a point (which would only matter if a metric happens to
   land exactly on its floor boundary — none do currently; the closest margin is branches at
   72.22% measured vs. 70% floor, a 2.22-point cushion beyond the buffer itself).
4. **No policy exists yet for *raising* a floor when a PR legitimately improves coverage.** This
   design only prevents regressions; it doesn't ratchet up over time. Worth a lightweight follow-up
   convention (e.g. "if your PR's own coverage report shows a new comfortable margin above the
   floor, bump the floor in the same PR") but that's a process decision for the team, not something
   this design should mandate unilaterally.
5. **Root-owned `target/` directories were found in all 7 backend service directories** at the
   start of this session (likely a residue of a prior Docker-based build running as root against a
   bind-mounted repo), which blocked a normal `mvn clean` in place and forced this session's
   measurement to happen in a scratch copy instead. This is unrelated to the coverage-gate design
   itself but is an environment-hygiene issue the human may want cleaned up (`sudo rm -rf
   services/*/target`) before a developer agent tries to build locally and hits the same permission
   error.
