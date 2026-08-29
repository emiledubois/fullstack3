# Flyway Migration Baseline — Replacing `ddl-auto=update`

## 1. Summary

All 5 JPA-backed services (`auth-service`, `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos`) currently run `spring.jpa.hibernate.ddl-auto=update` in their single, unprofiled `application.properties`, with zero migration tooling anywhere in the repo. This means the actual production/dev schema is whatever Hibernate happened to infer from the entity graph the last time each service booted — it is undocumented, unreviewable in a diff, and already drifted at least once silently (the 5 ARCO+ columns added to `auth-service`'s `users` table for cancelación/oposición were never recorded anywhere except "Hibernate added them"). This design replaces `ddl-auto=update` with versioned Flyway migrations, seeded with a **baseline migration per service that is a byte-for-byte match of the schema Hibernate has already created** (verified live against each service's own database in this session, not inferred from entity annotations alone), and switches `ddl-auto` to `validate` so any future entity/schema drift becomes a loud boot failure instead of a silent, unreviewed `ALTER TABLE`. This is infrastructure/tooling work: no new endpoint, no new user-facing behavior, no schema content changes beyond capturing what already exists.

## 2. Affected services

| Service | Change | Why |
|---|---|---|
| `auth-service` | Add Flyway, `V1__baseline.sql`, `ddl-auto` → `validate` | Has its own `auth_db`; `users` table, already drifted once (ARCO+ columns) |
| `ms-inventario` | Same | Has its own `inventario_db`; `productos` table |
| `ms-pedidos` | Same | Has its own `pedidos_db`; **two** tables (`pedidos`, `saga_estado`) |
| `ms-envios` | Same | Has its own `envios_db`; `envios` table |
| `ms-pagos` | Same | Has its own `pagos_db`; `pagos` table (payment-adjacent, no card data) |
| `notification-service` | **No change** | Confirmed: no `spring-boot-starter-data-jpa`/`postgresql` dependency, no `@Entity` classes, no database of its own (README: "sin BD — Observer") |
| `api-gateway` | **No change** | No database |
| `frontend` | **No change** | Not touched by this change at all |

**Coordination classification: independent parallel changes, not a Saga or a Facade.** Each of the 5 services owns its own database and its own Flyway history table (`flyway_schema_history`) — there is no cross-service transaction, no shared migration state, and no orchestration needed between them. The only cross-cutting concern is that the *mechanics* (dependency, properties, baseline procedure) must be applied identically across all 5, which is a repetition/consistency concern for the developer agent, not a coordination concern for the runtime.

## 3. API contract

**N/A — no new or changed HTTP endpoints.** This is a data-layer/build-tooling change only. No controller, DTO, or auth requirement changes in any service.

## 4. Data model changes

### 4.1 Tool choice: Flyway (not Liquibase)

Flyway is confirmed correct for this stack, not just assumed:
- Spring Boot 3.5.16 autoconfigures Flyway out of the box (`FlywayAutoConfiguration`) — verified the parent BOM (`spring-boot-dependencies-3.5.16.pom`) manages `flyway.version=11.7.2`, so no explicit version pinning is needed in any service `pom.xml`.
- Every migration this repo needs is plain, single-database-per-service PostgreSQL DDL/DML — Flyway's plain-SQL model (`V<n>__description.sql`) is a direct, reviewable match for that; Liquibase's changelog abstraction (XML/YAML/JSON with a DB-agnostic diff format) buys nothing here since there is no multi-vendor-DB requirement anywhere in this stack (every service's `application.properties` hardcodes `org.postgresql.Driver`/`PostgreSQLDialect`) and would add an abstraction layer (changelog format, `databaseChangeLog` bookkeeping) the team doesn't need.
- Flyway's `baselineOnMigrate`/`baseline-version` mechanism (used below) is exactly shaped for "adopt migrations onto a DB Hibernate already built," which is precisely this task's hard requirement (§4.3).
- Flyway is already the more common default in the Spring Boot ecosystem for teams in this situation (single-DB-vendor, want minimal ceremony), which matters for a small team maintaining 5 near-identical service configs.

**Required dependencies, added identically to all 5 `pom.xml` files** (no explicit `<version>` — inherited from `spring-boot-starter-parent`):
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```
Note: as of Flyway 10+, PostgreSQL support was split out of `flyway-core` into `flyway-database-postgresql` — **both** artifacts are required, `flyway-core` alone will fail to recognize the `jdbc:postgresql:` URL at runtime.

### 4.2 Ground truth: how the baseline was generated (not inferred from memory)

The audit's claim was verified for real, not re-derived from Hibernate's default naming-strategy knowledge. Procedure used in this design session:
1. `docker compose up -d --build postgres-auth postgres-inventario postgres-pedidos postgres-envios postgres-pagos auth-service ms-inventario ms-pedidos ms-envios ms-pagos` — built and booted all 5 services against their real Postgres 15-alpine containers with the *current, unmodified* `ddl-auto=update` config, exactly as any dev/CI environment runs today.
2. All 5 services started cleanly (`Started XxxApplication in ... seconds`, no schema errors).
3. `docker compose exec <postgres-container> pg_dump -U postgres -d <db> --schema-only --no-owner --no-privileges` against each of the 5 databases — this is the literal DDL Hibernate produced, including exact column order, exact Hibernate-generated constraint names (e.g. `uk6dotkott2kjsp8vw4d0m25fb7`), exact types (`character varying(255)`, `double precision`, `timestamp(6) without time zone`, `jsonb`, `uuid`), and CHECK constraints Hibernate 6's Bean Validation integration silently added (all 5 `pom.xml`s carry `spring-boot-starter-validation`, which is why `@Min`/`@Enumerated(STRING)` fields got `CHECK` constraints without anyone writing SQL for them).
4. Cross-checked every dumped table against its `@Entity` class under each service's `model`/`saga` package to confirm the dump and the code agree (they do, in every case, including all 5 ARCO+ columns on `auth-service`'s `User`).
5. Also confirmed via `SELECT count(*)` that these databases already hold real (pre-existing, non-empty) data — see §4.5 — which is exactly the "existing dev DB" scenario this design must not break.
6. Torn the stack back down (`docker compose down`, **no** `-v`) — containers/network removed, named volumes (and their data) left exactly as found before this session.

### 4.3 Baseline migration per service (`V1__baseline.sql`)

**Location (per service, unchanged from Flyway's default — no reason to deviate):** `src/main/resources/db/migration/V1__baseline.sql`.

The following are the exact contents required, transcribed from the verified `pg_dump` output (session-metadata lines like `SET client_encoding`, `\restrict`/`\unrestrict`, and ownership/privilege lines stripped — Flyway migrations should contain only the DDL that matters, not `pg_dump` session noise).

**`services/auth-service/src/main/resources/db/migration/V1__baseline.sql`:**
```sql
CREATE TABLE users (
    id                          BIGINT GENERATED BY DEFAULT AS IDENTITY,
    cancelacion_completada_en   TIMESTAMP(6) WITHOUT TIME ZONE,
    cancelacion_solicitada_en   TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at                  TIMESTAMP(6) WITHOUT TIME ZONE,
    email                       VARCHAR(255) NOT NULL,
    oposicion_procesamiento     BOOLEAN,
    oposicion_registrada_en     TIMESTAMP(6) WITHOUT TIME ZONE,
    password                    VARCHAR(255) NOT NULL,
    role                        VARCHAR(255),
    status                      VARCHAR(255),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email)
);
```

**`services/ms-inventario/src/main/resources/db/migration/V1__baseline.sql`:**
```sql
CREATE TABLE productos (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY,
    bodega           VARCHAR(255),
    descripcion      VARCHAR(255),
    nombre           VARCHAR(255) NOT NULL,
    precio_unitario  DOUBLE PRECISION NOT NULL,
    sku              VARCHAR(255) NOT NULL,
    stock_actual     INTEGER NOT NULL,
    umbral_minimo    INTEGER NOT NULL,
    CONSTRAINT productos_pkey PRIMARY KEY (id),
    CONSTRAINT uk8bwvjlh8b1xi4cc4ar819q61y UNIQUE (sku),
    CONSTRAINT productos_stock_actual_check CHECK (stock_actual >= 0),
    CONSTRAINT productos_umbral_minimo_check CHECK (umbral_minimo >= 0)
);
```

**`services/ms-pedidos/src/main/resources/db/migration/V1__baseline.sql`** (two tables — this service's DB backs both the order model and Saga state):
```sql
CREATE TABLE pedidos (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY,
    cantidad        INTEGER,
    cliente_nombre  VARCHAR(255),
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE,
    destino         VARCHAR(255),
    observaciones   VARCHAR(255),
    producto_id     BIGINT,
    status          VARCHAR(255) NOT NULL,
    tipo_pedido     VARCHAR(255) NOT NULL,
    total           DOUBLE PRECISION NOT NULL,
    user_email      VARCHAR(255),
    user_id         BIGINT NOT NULL,
    CONSTRAINT pedidos_pkey PRIMARY KEY (id)
);

CREATE TABLE saga_estado (
    saga_id          UUID NOT NULL,
    actualizado_en   TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    creado_en        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    envio_id         BIGINT,
    estado           VARCHAR(20) NOT NULL,
    paso_actual      VARCHAR(50) NOT NULL,
    payload          JSONB NOT NULL,
    pedido_id        BIGINT,
    stock_reservado  BOOLEAN NOT NULL,
    tipo             VARCHAR(50) NOT NULL,
    ultimo_error     TEXT,
    CONSTRAINT saga_estado_pkey PRIMARY KEY (saga_id),
    CONSTRAINT saga_estado_estado_check CHECK (estado IN ('INICIADA','EN_PROGRESO','COMPLETADA','COMPENSANDO','FALLIDA'))
);
```

**`services/ms-envios/src/main/resources/db/migration/V1__baseline.sql`:**
```sql
CREATE TABLE envios (
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY,
    created_at               TIMESTAMP(6) WITHOUT TIME ZONE,
    destino                  VARCHAR(255),
    fecha_estimada_entrega   TIMESTAMP(6) WITHOUT TIME ZONE,
    guia_despecho            VARCHAR(255),
    pedido_id                BIGINT NOT NULL,
    ruta_descripcion         VARCHAR(255),
    status                   VARCHAR(255) NOT NULL,
    tipo_envio               VARCHAR(255) NOT NULL,
    transportista            VARCHAR(255),
    CONSTRAINT envios_pkey PRIMARY KEY (id)
);
```

**`services/ms-pagos/src/main/resources/db/migration/V1__baseline.sql`:**
```sql
CREATE TABLE pagos (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY,
    commerce_order   VARCHAR(255) NOT NULL,
    confirmado_en    TIMESTAMP(6) WITHOUT TIME ZONE,
    creado_en        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    email            VARCHAR(255),
    estado           VARCHAR(255) NOT NULL,
    flow_order       BIGINT,
    flow_token       VARCHAR(255),
    monto            DOUBLE PRECISION,
    pedido_id        BIGINT NOT NULL,
    url_pago         VARCHAR(255),
    CONSTRAINT pagos_pkey PRIMARY KEY (id),
    CONSTRAINT uki00kqwtk8bsukmuckeu0ywxts UNIQUE (commerce_order),
    CONSTRAINT pagos_estado_check CHECK (estado IN ('INICIADO','PENDIENTE','PAGADO','RECHAZADO','ANULADO'))
);
```

**Do not "clean up" these scripts** (rename the ugly Hibernate-generated constraint names, reorder columns alphabetically, etc.) beyond what's shown above. The developer agent must reproduce the live schema exactly, because `ddl-auto=validate` (§4.4) will compare the post-migration schema against the entity mappings, and any deviation (e.g., a renamed constraint that Hibernate itself didn't ask for) still needs to structurally match what Hibernate expects — matching the live dump verbatim is the lowest-risk way to guarantee that.

### 4.4 Adoption mechanics — `baselineOnMigrate` (the "don't drop anyone's local DB" requirement)

Add to each of the 5 services' `application.properties` (identical block, only the description varies if desired):
```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.baseline-description=ddl-auto=update baseline pre-Flyway
spring.jpa.hibernate.ddl-auto=validate
```

Why this specific pair of properties, and what each does:
- **`baseline-on-migrate=true`**: by default, Flyway refuses to run `migrate` against a database that already has tables but no `flyway_schema_history` table (exactly every existing dev/CI database today) — it throws `FlywayException: Found non-empty schema... without schema history table`. Setting this to `true` tells Flyway: "if you find a non-empty schema with no history table, insert a bookkeeping row for `baseline-version` **without executing that migration's SQL**, then proceed to apply anything with a *higher* version number." This is exactly the "adopt Flyway without dropping the DB" mechanism the task requires.
- **`baseline-version=1`**: matches the version number of the baseline script itself (`V1__baseline.sql`). This means: on an **existing** DB (has `users`/`productos`/etc. already), Flyway inserts a `V1` row marked `baseline` and does *not* re-run `V1__baseline.sql`'s `CREATE TABLE` (which would fail with "relation already exists" if it did) — there is nothing left to apply, so `migrate` completes as a no-op past bookkeeping. On a **fresh, empty** DB, the "non-empty schema" precondition for baselining is false, so Flyway ignores `baseline-on-migrate` entirely and just runs `V1__baseline.sql` for real, creating the tables from scratch. **This single config block is what makes both of QA's required scenarios (§8) work with the same properties file, with no manual per-environment branching.**
- **This is bookkeeping-only, never touches business data.** Baselining inserts one row into Flyway's own internal `flyway_schema_history` table; it never reads, writes, or drops any application table or row. This matters directly for §4.5 below: the pre-existing rows in `auth_db.users`, `pedidos_db.pedidos`, `envios_db.envios`, `inventario_db.productos` are untouched by this migration.
- **`spring.flyway.locations`** does not need to be set — Flyway's default (`classpath:db/migration`) already resolves to `src/main/resources/db/migration`, matching §4.3.

### 4.5 What `ddl-auto` becomes afterward: `validate`

`spring.jpa.hibernate.ddl-auto=validate` in all 5 services, replacing `update`. Rationale:
- `validate` makes Hibernate compare each `@Entity`'s expected table/column/type shape against what's actually in the database **at startup, before accepting traffic**, and throws `SchemaManagementException` (surfacing as a `BeanCreationException` that aborts Spring Boot startup) on any mismatch — e.g., an entity field with no corresponding column, or a column whose type Hibernate can't reconcile. This is the single most important behavior change in this design: an entity/schema drift that today would previously have been silently patched by `update` (exactly how the 5 undocumented ARCO+ columns got created) now **fails the build/boot loudly**, forcing whoever added the field to also write a migration.
- `validate` never issues `ALTER`/`CREATE`/`DROP` — it is read-only against the schema. All schema changes now flow exclusively through `Vn__description.sql` files, reviewable in a PR diff.
- Explicitly *not* `none`: `none` would silence Hibernate's startup schema check entirely, which throws away the "fail loud on drift" property that `validate` gives for free at zero extra engineering cost.
- **What happens under drift, concretely**: if a developer later adds a new `@Column` to, say, `Producto` without a matching `V2__*.sql` migration, `ms-inventario` will fail to start with a message naming the missing column (Hibernate's `SchemaValidator` output, e.g. `Schema-validation: missing column [nueva_columna] in table [productos]`) — the service does not boot, does not serve traffic with a half-correct mapping, and does not silently ignore the new field. This is the desired fail-closed behavior for this stack (see §6 A04's "retry after a failed migration" note below).

### 4.6 Data already in these databases (confirmed live, not hypothetical)

Live introspection during this design session found **real, non-empty data already present** in every dev database except `pagos_db`:

| Database | Table | Row count found |
|---|---|---|
| `auth_db` | `users` | 2 (smoke-test accounts) |
| `inventario_db` | `productos` | 1 |
| `pedidos_db` | `pedidos` | 2 |
| `envios_db` | `envios` | 2 |
| `pagos_db` | `pagos` | 0 |

This confirms the "naive baseline could conflict with existing data" risk named in the task is real, not hypothetical, in this exact environment — and confirms why `baselineOnMigrate` (bookkeeping-only, never touches rows) rather than "drop and recreate the DB" is the correct mechanism: a drop-and-recreate approach would have destroyed these rows the first time anyone ran it against their local Docker volume.

### 4.7 Non-JPA-managed schema elements

None found. Searched every `@Entity` class for `@Table(indexes=...)`/`uniqueConstraints=...` (none used — the two existing unique constraints on `users.email` and `productos.sku` and `pagos.commerce_order` all come from `@Column(unique = true)`, which Hibernate already reflected in the dumped DDL and which is captured in §4.3). No `data.sql`/`schema.sql`/`import.sql` seed files exist in any of the 5 services (confirmed via `find`), so there is no competing Spring-managed seeding mechanism to reconcile with Flyway. No views, triggers, or stored procedures exist in any of the 5 dumped schemas — every dump's DDL is exactly `CREATE TABLE` + `ALTER TABLE ... ADD CONSTRAINT`/`... ADD GENERATED ... AS IDENTITY`, nothing else.

## 5. Design pattern fit

This extends the existing **Repository** pattern's data-access layer, not a new pattern. README's pattern table already lists Repository ("Acceso a datos vía `JpaRepository`. Desacopla lógica de negocio de la BD") as present in all 5 services — Flyway is the natural infrastructure counterpart: it owns *schema* lifecycle the same way `JpaRepository` owns *data-access* lifecycle, and neither introduces new application-level abstraction. No GoF pattern (Strategy, Observer, Factory, Facade, Saga) applies to a schema-migration-tooling change, and none is introduced. A shared Maven module for migration tooling is deliberately **not** created: each service's migrations are specific to its own database-per-service schema (this is the whole point of database-per-service — no shared schema, so no shared migration content), and this repo has no existing shared-module precedent (confirmed in the prior `internal-service-auth.md` design, §7) — introducing one now for 5 files that will diverge immediately (different tables, different future columns) would be speculative structure this task doesn't need.

## 6. Security requirements (OWASP Top 10 2021)

- **A01 Broken Access Control:** N/A — no endpoint, ownership, or tenant-check change. Migration files are applied by the service itself at boot using the same `DB_USER`/`DB_PASS` credentials it already uses for all other JPA activity; no new credential or access path is introduced.
- **A02 Cryptographic Failures:** N/A for new secrets — no new credential is introduced. Note for completeness: the baseline SQL captures `password` (BCrypt hash) and `email` columns in `auth-service`'s schema, and payment-adjacent columns (`commerce_order`, `flow_token`, `monto`) in `ms-pagos`'s schema, but the migration *files* contain only `CREATE TABLE ... (column_name TYPE)` — column definitions, never actual row data (no `INSERT`/seed data for these tables is part of this design, confirmed in §4.7) — so no secret or PII value is ever committed to a migration script.
- **A03 Injection:** N/A. The migration files are static, developer-authored, version-controlled DDL, executed by Flyway with no runtime string interpolation from user input. No dynamic SQL construction is introduced anywhere in this design.
- **A04 Insecure Design (abuse cases specific to this change):**
  - **Startup race / double-migrate:** if two replicas of the same service start concurrently against the same database (not currently a docker-compose scenario — each service has exactly one replica — but worth stating for correctness), Flyway uses a database-level advisory lock (`flyway_schema_history` locking row on PostgreSQL) to serialize concurrent `migrate` calls; the second replica blocks until the first finishes, then sees "nothing to do." No special handling is required from the developer for this.
  - **Retry after a failed migration:** correction from QA (verified empirically against Postgres 16, not assumed from Flyway's generic docs): PostgreSQL supports transactional DDL, and Flyway wraps each migration script in a transaction by default (`spring.flyway.execute-in-transaction=true`), so a script that fails partway is **fully rolled back**, including its own `flyway_schema_history` bookkeeping row — no `success=false` row is ever recorded, unlike on a database without transactional DDL (e.g. MySQL), where a partial failure can leave such a row. Restarting without fixing the migration produces a plain re-attempt of the same script (same error), not a distinct "found failed migration" message. The fail-closed *property* this exists to guarantee still holds and is arguably stronger here — the service never boots against a half-applied schema and never silently retries past a bad migration, and there is no orphaned failed-migration row to manually `flyway repair` away — it just holds via atomic rollback rather than via a recorded failure row. See §8 item 9 for how this was verified.
  - **Double-submit/replay:** not applicable — there is no user-facing request here to double-submit; `migrate` is idempotent by construction (each version applies exactly once, tracked in `flyway_schema_history`).
- **A05 Security Misconfiguration:** No new environment variables, no new exposed ports, no new Actuator endpoint. `flyway_schema_history` is an ordinary table in each service's existing database, not separately exposed. `spring.flyway.baseline-on-migrate=true` is a **permanent** setting in this design, not a one-time flag to be removed after adoption — leaving it `true` is safe long-term precisely because baselining only fires when *no* `flyway_schema_history` table exists yet on a *non-empty* schema, a condition that becomes false forever the first time any environment successfully baselines (open question in §9 flags the alternative of setting it back to `false` post-adoption).
- **A07 Auth Failures:** N/A — no JWT/session/authentication code path is touched.
- **A08 Software/Data Integrity:** This is where Flyway adds a **new integrity guarantee** that didn't exist under `ddl-auto=update`: by default (`spring.flyway.validate-on-migrate=true`, Spring Boot default), Flyway checksums every applied migration script and fails startup if a previously-applied script's file content on disk no longer matches the checksum recorded in `flyway_schema_history` — i.e., **migration files become immutable once merged and applied**; editing `V1__baseline.sql` after it has run anywhere is now a hard startup failure everywhere else that already applied it, not a silent divergence. This directly closes the class of bug this task exists to fix (a schema change with no record of what changed or when). No deserialization of external input is introduced — SQL files are static classpath resources.
- **A09 Logging/Monitoring:** Flyway logs each migration it applies (`Migrating schema "public" to version "1 - baseline"`) and the baseline event itself (`Successfully baselined schema with version: 1`) at `INFO` on every service startup — this is useful, auditable evidence of *when* a schema changed and *what* changed (the migration file, in the git history), which is a direct improvement on `COMPLIANCE_CL.md` §4.4's "logging sufficient to reconstruct an incident" goal, applied to schema changes specifically (today: zero record of the 5 ARCO+ columns' origin beyond source-reading the entity class). Nothing new needs to be excluded from logs — migration content is DDL only (table/column definitions), never secrets or PII values.
- **A10 SSRF:** N/A. No outbound HTTP calls are introduced or changed.

## 7. Chilean compliance touchpoints

This is infrastructure tooling, not a feature that creates, stores, exposes, or deletes personal data — but it directly touches how the *schema* holding personal data (per `COMPLIANCE_CL.md` §2: `auth-service` holds email + password hash; `ms-pedidos` holds cliente_nombre/email/destino; `ms-envios` holds destino; `ms-pagos` holds pedido/monto references) is changed and recorded going forward:

- **Ley 21.719, deber de seguridad / accountability (§3 of the compliance doc):** this design is a direct, concrete improvement to "responsabilidad demostrable" for schema changes specifically — every future column added to a table holding personal data (e.g., another ARCO+-driven column on `users`) will now exist as a named, dated, git-blamed `Vn__description.sql` file instead of an untracked side effect of `ddl-auto=update`. This closes exactly the gap this task was opened to fix.
- **Ley 21.663, trazabilidad:** Flyway's own startup logging (§6, A09) gives a permanent, timestamped record of every schema change applied to every service's database, in every environment — useful evidence for reconstructing "what changed and when" in the unlikely event a schema change is later implicated in an incident.
- **Retention / ARCO+ rights (§4.2, §4.4 of the compliance doc):** N/A for this specific change — no personal data field is added, removed, exposed, or retained differently by this design. The baseline migration is a snapshot of the *current* schema (including the already-live ARCO+ columns), not a new data-processing capability.

## 8. Acceptance criteria

QA must test both scenarios below **for each of the 5 services** (`auth-service`, `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos`), using `docker compose`, not in the abstract.

**Scenario A — fresh, empty database (migrations run from scratch):**
1. `docker compose down -v` (removes named volumes — **only** acceptable use of `-v` here, to guarantee true emptiness for this specific test) then `docker compose up -d --build postgres-auth postgres-inventario postgres-pedidos postgres-envios postgres-pagos auth-service ms-inventario ms-pedidos ms-envios ms-pagos`.
2. Each of the 5 services' logs (`docker compose logs <service>`) must show `Successfully applied 1 migration to schema "public"` (or equivalent Flyway "migrating to version 1" line), followed by normal `Started XxxApplication` — no `SchemaManagementException`, no `FlywayException`.
3. `docker compose exec postgres-<db> psql -U postgres -d <db> -c '\dt'` must list the expected table(s) (`users`; `productos`; `pedidos`+`saga_estado`; `envios`; `pagos`) plus Flyway's own `flyway_schema_history`.
4. `docker compose exec postgres-<db> psql -U postgres -d <db> -c 'select version, description, success from flyway_schema_history;'` must show exactly one row: version `1`, description `baseline`, `success = t` (note: on a fresh DB, `V1` runs as a real migration, **not** a baseline row — `baseline_on_migrate` does not activate against an empty schema, per §4.4 — so this table shows a normal applied-migration entry, not a `<< Flyway Baseline >>` entry).
5. A basic write against each service (e.g., `POST /api/inventario` a product via the gateway, or the equivalent smoke test already used elsewhere in this repo) succeeds, proving the fresh schema is fully functional, not just present.

**Scenario B — existing database with the current `ddl-auto`-created schema (baseline kicks in, no duplicate-table errors):**
1. Starting from the **pre-migration** state (services still on `ddl-auto=update`, no Flyway dependency), run `docker compose up -d --build` for all 5 services once, to populate each database exactly as today (this reproduces the "existing dev DB" condition without needing to hand-craft one).
2. Without touching the database/volumes, apply the Flyway change (dependency + `V1__baseline.sql` + properties from this design) to all 5 services and rebuild: `docker compose up -d --build auth-service ms-inventario ms-pedidos ms-envios ms-pagos` (deliberately **not** `down -v` — the point of this scenario is the volumes/data survive).
3. Each service's logs must show `Successfully baselined schema with version: 1` (or equivalent), **not** a `CREATE TABLE`/`relation already exists` error, followed by normal `Started XxxApplication`.
4. `select version, description, success, type from flyway_schema_history;` must show exactly one row: version `1`, `type = BASELINE` (not `SQL`), `success = t`.
5. Pre-existing rows in each table (verified in this design session: 2 rows in `auth_db.users`, 1 in `inventario_db.productos`, 2 in `pedidos_db.pedidos`, 2 in `envios_db.envios`) are **unchanged** — same row count, same values, before and after the migration is applied.
6. Every service reaches `Started XxxApplication` with no `SchemaManagementException` from the new `ddl-auto=validate` (proving the baseline's captured schema and the live entity mappings agree exactly).

**Negative / drift / failure cases (at least one per gotcha named in this design):**
7. With `ddl-auto=validate` in place, add a throwaway `@Column private String temp;` to any one entity (e.g. `Producto`) **without** a corresponding `V2__*.sql` migration, rebuild, and confirm the service **fails to start** with a Hibernate `SchemaManagementException`/`Schema-validation: missing column [temp]` error surfaced in the logs — proving drift is fail-closed, not silently tolerated. Revert the change after confirming.
8. Manually corrupt one applied migration's checksum (edit a single character in a migration file that has already run against a test database, without bumping its version number) and confirm the affected service **fails to start** with a Flyway checksum-mismatch error (`Migration checksum mismatch for migration version 1`) — proving migrations are immutable once applied, not silently re-appliable with different content.
9. Force a migration to fail partway (e.g., temporarily add a syntactically invalid statement to a scratch `V2__broken.sql` on a throwaway branch/test DB) and confirm: (a) the service fails to start, and (b) restarting the service **without fixing anything** fails again with the same error rather than silently retrying or skipping past it. **Verified by QA (2026):** on this stack, PostgreSQL's transactional DDL means Flyway rolls the failed script back atomically — no `success = f` row is ever recorded in `flyway_schema_history` (that behavior is specific to databases without transactional DDL, e.g. MySQL), and the restart is a plain re-attempt of the same script, not a distinct "found failed migration" message. The underlying fail-closed property (never boots with a half-applied schema, never silently retries/skips past a bad migration) still holds — see §6 A04. Remove the broken script after confirming.
10. Run the existing CI job (`backend-ci.yml`, `./mvnw -B clean verify`) for all 5 affected services against a fresh ephemeral Postgres container (the job's existing `postgres:16-alpine` service container is already empty per job by construction) and confirm all currently-passing tests still pass — this is Scenario A automatically re-exercised on every PR going forward, no CI workflow changes required.

## 9. Open questions

1. **Postgres version mismatch, pre-existing and unrelated to this design but worth flagging:** `docker-compose.yml` runs `postgres:15-alpine` while `backend-ci.yml`'s test service runs `postgres:16-alpine`. Both were confirmed compatible with the `V1__baseline.sql` DDL used here (no version-specific syntax), so this design does not need to resolve the mismatch, but it's an inconsistency the team may want to align separately.
2. **Should `spring.flyway.baseline-on-migrate` stay `true` forever, or be flipped to `false` after every environment has baselined once?** This design recommends leaving it `true` permanently (§6, A05) since it is a no-op once `flyway_schema_history` exists, and flipping it back would require coordinating "has every environment (including everyone's local machine) baselined yet?" — a question this design has no reliable way to answer. If the team later wants the stricter guarantee that Flyway can never silently baseline a schema it shouldn't (e.g., a genuinely corrupt/out-of-band-modified DB), that's a deliberate follow-up decision, not a default here.
3. **`ms-pedidos` has two tables in one baseline file (`pedidos` + `saga_estado`).** This design keeps them in a single `V1__baseline.sql` since they were both created by the same `ddl-auto=update` process in the same database and splitting them into `V1`/`V2` would misrepresent history (they were never applied separately). Confirm the developer agent doesn't split these for "one table per file" tidiness — that would be inventing history that didn't happen.
4. **Naming convention for future migrations** (not exercised by this design, but should be agreed before the first post-baseline change lands): `V<n>__<snake_case_description>.sql`, sequential per service (each service's version numbers are independent of the others'), e.g. `V2__add_email_verificado_to_users.sql`. Not enforced by tooling in this design (no `flyway.validate-migration-naming` equivalent exists) — relies on reviewer discipline, same as any other code-style convention in this repo today.
