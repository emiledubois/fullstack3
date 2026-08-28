# Mapeo ISO/IEC 27001:2022 — SmartLogix

> **Disclaimer:** este documento es una guía de ingeniería que compara los controles técnicos ya implementados en SmartLogix contra el Anexo A de la norma ISO/IEC 27001:2022. **No es una certificación, ni una opinión legal, ni un reemplazo de una auditoría de certificación real.** ISO 27001 es una norma de gestión (un Sistema de Gestión de Seguridad de la Información, SGSI/ISMS) — certificarse exige mucho más que controles de código: una declaración de alcance, una metodología formal de evaluación de riesgos, compromiso de la dirección, un programa de auditoría interna, y una auditoría externa por un organismo acreditado. Nada de eso existe para este proyecto (portafolio de estudiante). El valor de este documento es **mostrar qué tan alineados están los controles técnicos ya construidos con el marco de referencia que la Ley 21.663 exige usar**, y dejar explícito qué falta para una certificación real. Cada afirmación "✅ implementado" fue verificada leyendo o grepeando el código real (no se tomó nada del `COMPLIANCE_CL.md` por hecho, aunque ese documento ya había sido auditado antes).
>
> **Fecha de referencia: 2026-08-27.**

## 1. ¿Qué es ISO/IEC 27001:2022 y por qué es relevante acá?

ISO/IEC 27001 es la norma internacional para Sistemas de Gestión de Seguridad de la Información (SGSI). Su **Anexo A** contiene un catálogo de **93 controles organizados en 4 temas** (revisión 2022, verificado vía búsqueda — la revisión 2013 tenía 114 controles en 14 dominios, por lo que cualquier mapeo hecho con numeración antigua ya no aplica):

| Tema | Rango | Cantidad |
|---|---|---|
| A.5 — Organizacionales | A.5.1–A.5.37 | 37 |
| A.6 — Personas | A.6.1–A.6.8 | 8 |
| A.7 — Físicos | A.7.1–A.7.14 | 14 |
| A.8 — Tecnológicos | A.8.1–A.8.34 | 34 |

SmartLogix, como toda la infraestructura es software (no hay oficina física, hardware propio, ni empleados con acceso físico a datacenters), tiene la mayoría de los controles A.7 (Físicos) marcados como **No aplicable** en la Sección 4 — no es una omisión, es una consecuencia real de ser una PYME 100% cloud/contenedores.

## 2. Relación entre ISO 27001 y las leyes chilenas ya mapeadas en `COMPLIANCE_CL.md`

Esto es más preciso de lo que `COMPLIANCE_CL.md` §3 dice hoy ("alineable con ISO 27001 a nivel de prácticas, sin exigir la certificación formal para una PYME no-OIV") — investigado específicamente para este documento:

- **La Ley 21.663 NO es un marco opcional tipo ISO 27001 — es una obligación legal con fiscalización y multas.** ISO 27001 no la reemplaza; un SGSI basado en ISO 27001 **ayuda a cumplirla**, pero cumplir la ley no se limita a "tener ISO 27001".
- La ANCI (Agencia Nacional de Ciberseguridad, creada por la misma ley) no solo fiscaliza — también establece lineamientos técnicos y normativos que las entidades reguladas deben seguir, y estos lineamientos están explícitamente pensados para alinearse con marcos reconocidos (ISO 27001, NIST CSF, NIS2 se mencionan como referencia).
- **Los Operadores de Importancia Vital (OIV)** — la categoría más alta de regulación bajo esta ley — están obligados a operar un SGSI alineado con ISO 27001, con auditorías periódicas y simulacros. SmartLogix hoy **no califica como OIV** (ver disclaimer de `COMPLIANCE_CL.md` §1 — la clasificación regulatoria real depende de hechos del negocio y debe confirmarla un abogado), pero si este proyecto evolucionara hacia clientes en sectores regulados (logística crítica, financiero), ese es el estándar contra el que terminaría siendo medido.
- Para la Ley 21.719 (Protección de Datos), ISO 27001 no es mencionado como obligatorio, pero el Anexo A cubre casi uno a uno el "deber de seguridad" (medidas técnicas y organizativas proporcionales al riesgo) que exige el artículo respectivo — de ahí el valor de este mapeo para esa ley también.

**Conclusión operativa:** tratar ISO 27001 como el lenguaje común para demostrar las obligaciones de gestión de riesgo que la Ley 21.663 ya impone, y como evidencia de "medidas técnicas y organizativas" para la Ley 21.719 — no como un sustituto de ninguna de las dos, y sin declarar cumplimiento/certificación que no existe.

## 3. Statement of Applicability (SoA) simplificado

Formato real de ISO 27001 (Anexo A completo, decisión de aplicabilidad + estado). `Aplicable` refleja si el control tiene sentido para una PYME de software sin infraestructura física propia. Estado usa la misma convención que `COMPLIANCE_CL.md`: `✅ implementado`, `⚠️ parcial`, `❌ pendiente`, `N/A` (no aplicable).

### A.5 — Organizacionales (37 controles)

| Control | Título | Aplicable | Estado | Evidencia / justificación |
|---|---|---|---|---|
| A.5.1 | Políticas de seguridad de la información | Sí | ❌ | No existe una política de seguridad documentada y aprobada formalmente — el conocimiento vive en `COMPLIANCE_CL.md`/`AGENTIC_WORKFLOW.md`, que son guías de ingeniería, no políticas gobernadas |
| A.5.2 | Roles y responsabilidades de seguridad | Sí | ⚠️ | `COMPLIANCE_CL.md` §3 menciona "designación de responsable de ciberseguridad" como pendiente organizacional |
| A.5.3 | Segregación de funciones | Sí | ✅ | El propio flujo `dev-cycle` (architect/developer/reviewer/qa) implementa segregación de funciones en el desarrollo — ver `docs/AGENTIC_WORKFLOW.md` |
| A.5.4 | Responsabilidades de gestión | Sí | ❌ | No hay estructura de gestión formal (proyecto de un desarrollador) |
| A.5.5 | Contacto con autoridades | Sí | ❌ | `COMPLIANCE_CL.md` §4.3 — punto de contacto para reportar brechas a la ANCI/Agencia de Protección de Datos: ❌ pendiente |
| A.5.6 | Contacto con grupos de interés especial | Sí | ❌ | No hay membresía en foros de seguridad/CSIRT |
| A.5.7 | Inteligencia de amenazas | Sí | ⚠️ | Dependabot + Trivy (`security.yml`) consumen bases de datos de vulnerabilidades públicas — es inteligencia de amenazas automatizada, no un proceso formal de threat intel |
| A.5.8 | Seguridad de la información en gestión de proyectos | Sí | ✅ | El propio `architect` agent exige mapear OWASP + cumplimiento chileno en cada diseño antes de codear (`.claude/agents/architect.md`) |
| A.5.9 | Inventario de información y activos | Sí | ✅ | `COMPLIANCE_CL.md` §2 — inventario de datos personales/sensibles por servicio |
| A.5.10 | Uso aceptable de la información y activos | Sí | ❌ | No documentado |
| A.5.11 | Devolución de activos | No | N/A | No hay empleados con activos físicos que devolver |
| A.5.12 | Clasificación de la información | Sí | ⚠️ | Implícito en `COMPLIANCE_CL.md` §2 (distingue dato personal/credencial/transaccional) pero sin esquema formal de clasificación (público/interno/confidencial) |
| A.5.13 | Etiquetado de la información | Sí | ❌ | No implementado |
| A.5.14 | Transferencia de información | Sí | ✅ | HTTPS/TLS documentado como requisito de despliegue (`COMPLIANCE_CL.md` §4.1); comunicación interna firmada HMAC (`docs/designs/internal-service-auth.md`) |
| A.5.15 | Control de acceso | Sí | ✅ | Autenticación por cookie JWT (`docs/designs/jwt-httponly-cookie-migration.md`) + autenticación interna servicio-a-servicio (`docs/designs/internal-service-auth.md`) — verificado: `AuthFilter.java`, `InternalAuthFilter.java` en los 6 servicios |
| A.5.16 | Gestión de identidad | Sí | ✅ | `auth-service` — registro/login con email único, roles (`ROLE_USER`/`ROLE_ADMIN`) |
| A.5.17 | Información de autenticación | Sí | ✅ | Verificado: `BCryptPasswordEncoder(12)` en `SecurityConfig.java` (`auth-service`) — hash fuerte, nunca texto plano |
| A.5.18 | Derechos de acceso | Sí | ⚠️ | Roles existen pero no hay un proceso formal de alta/baja/revisión periódica de accesos (aplicable más a equipos con RR.HH., limitado en este contexto) |
| A.5.19 | Seguridad en relaciones con proveedores | Sí | ⚠️ | Flow Chile (pasarela de pago) es el único proveedor externo — verificado: `FLOW_API_KEY`/`FLOW_SECRET_KEY` vía variables de entorno sin hardcodear (`ms-pagos/application.properties`), pero sin un acuerdo/registro formal de proveedor |
| A.5.20 | Seguridad en acuerdos con proveedores | Sí | ❌ | No hay contrato/SLA formal con Flow más allá de sus términos de API pública |
| A.5.21 | Seguridad en la cadena de suministro TIC | Sí | ✅ | Dependabot (7 ecosistemas Maven + npm + Docker + GitHub Actions, `dependabot.yml`) + Trivy escaneo de imágenes (`security.yml`) — gestiona el riesgo de dependencias de terceros |
| A.5.22 | Monitoreo de servicios de proveedores | Sí | ❌ | No hay monitoreo activo del estado/seguridad de Flow como servicio |
| A.5.23 | Seguridad de la información para servicios en la nube | Sí | N/A | SmartLogix no usa servicios cloud gestionados hoy (todo corre en Docker Compose local) — reevaluar si se despliega en AWS/GCP/Azure |
| A.5.24 | Planificación de gestión de incidentes | Sí | ⚠️ | `COMPLIANCE_CL.md` §4.3 — runbook de respuesta a incidentes marcado como pendiente de crear |
| A.5.25 | Evaluación y decisión sobre eventos de seguridad | Sí | ❌ | No hay proceso formal — depende de lectura manual de logs |
| A.5.26 | Respuesta a incidentes de seguridad | Sí | ❌ | No hay runbook ejecutable |
| A.5.27 | Aprendizaje de incidentes | Sí | ❌ | No hay incidentes registrados/proceso de postmortem definido |
| A.5.28 | Recolección de evidencia | Sí | ⚠️ | Los logs de rechazo (`[InternalAuth] Rechazado...`, ver `InternalAuthFilter.java` en los 6 servicios) sirven como evidencia técnica, pero sin cadena de custodia/proceso forense definido |
| A.5.29 | Seguridad de la información durante la disrupción | Sí | ⚠️ | Circuit Breaker + Rate Limiter (Resilience4j) y compensaciones Saga (`ms-pedidos`) dan resiliencia a nivel de aplicación, pero no hay plan de continuidad de negocio formal |
| A.5.30 | Preparación TIC para la continuidad del negocio | Sí | ❌ | Verificado: no hay estrategia de backup de bases de datos ni de recuperación ante desastre documentada — brecha real, no solo falta de proceso |
| A.5.31 | Requisitos legales, estatutarios, regulatorios y contractuales | Sí | ✅ | `COMPLIANCE_CL.md` completo es la evidencia directa de este control — identifica Ley 21.663 y Ley 21.719 con fechas de vigencia |
| A.5.32 | Derechos de propiedad intelectual | No | N/A | Fuera del alcance de un proyecto de portafolio |
| A.5.33 | Protección de registros | Sí | ⚠️ | `COMPLIANCE_CL.md` §4.4 — retención de logs marcada como ❌ pendiente (política no definida) |
| A.5.34 | Privacidad y protección de datos personales (PII) | Sí | ✅ | Endpoint `GET /api/usuarios/me/datos` (derecho de acceso ARCO+, `docs/designs/arco-acceso-personal-data.md`) + página `Mis Datos` (`docs/designs/frontend-mis-datos-page.md`) — verificado en código, no solo diseñado |
| A.5.35 | Revisión independiente de la seguridad de la información | Sí | ✅ | El `reviewer` agent es, por diseño, una revisión independiente (no puede editar código) de cada cambio — ver `.claude/agents/reviewer.md`; en este ciclo específico, este mismo documento fue producido para que el `reviewer` lo audite (ver Sección 5) |
| A.5.36 | Cumplimiento de políticas, normas y estándares | Sí | ✅ | El checklist de `COMPLIANCE_CL.md` §4 se revisa en cada `reviewer`/`qa` del `dev-cycle`, por diseño (`COMPLIANCE_CL.md` §5) |
| A.5.37 | Procedimientos operativos documentados | Sí | ✅ | `README.md` (setup, variables de entorno, comandos), `docs/AGENTIC_WORKFLOW.md` (cómo correr `/dev-cycle`) |

### A.6 — Personas (8 controles)

| Control | Título | Aplicable | Estado | Evidencia / justificación |
|---|---|---|---|---|
| A.6.1 | Selección de personal | No | N/A | Sin empleados que contratar |
| A.6.2 | Términos y condiciones de empleo | No | N/A | Sin empleados |
| A.6.3 | Concienciación, educación y capacitación en seguridad | No | N/A | Sin equipo humano al que capacitar (más allá del único desarrollador) |
| A.6.4 | Proceso disciplinario | No | N/A | Sin empleados |
| A.6.5 | Responsabilidades tras la finalización o cambio de empleo | No | N/A | Sin empleados |
| A.6.6 | Acuerdos de confidencialidad o no divulgación | No | N/A | Sin terceros con acceso a datos internos hoy |
| A.6.7 | Trabajo remoto | No | N/A | No aplica a un proyecto individual |
| A.6.8 | Reporte de eventos de seguridad de la información | Sí | ❌ | No hay canal formal de reporte (aplicaría si el equipo creciera) |

### A.7 — Físicos (14 controles)

Todos marcados **No aplicable** — SmartLogix no opera datacenters, oficinas ni hardware propio; corre en contenedores Docker sobre infraestructura que, en un despliegue real, sería de un proveedor cloud (cuya seguridad física es responsabilidad de ese proveedor, no de este proyecto). Si se despliega en un cloud gestionado, estos controles se heredan del certificado ISO 27001 del proveedor (AWS/GCP/Azure ya están certificados) — vale la pena citar ese certificado en un despliegue real en vez de reinventar controles físicos.

| Rango | Controles | Aplicable | Estado |
|---|---|---|---|
| A.7.1–A.7.14 | Perímetros de seguridad física, controles de entrada, protección de oficinas, protección contra amenazas ambientales, trabajo en áreas seguras, escritorio despejado, ubicación de equipos, seguridad del cableado, mantenimiento de equipos, eliminación segura, equipos fuera de instalaciones, medios de almacenamiento | No | N/A (heredado del proveedor cloud en despliegue real) |

### A.8 — Tecnológicos (34 controles)

| Control | Título | Aplicable | Estado | Evidencia / justificación |
|---|---|---|---|---|
| A.8.1 | Dispositivos de punto final de usuario | No | N/A | No hay dispositivos corporativos que gestionar |
| A.8.2 | Derechos de acceso privilegiado | Sí | ⚠️ | `ROLE_ADMIN` existe en `auth-service`, pero no hay revisión periódica de quién tiene ese rol ni cuentas separadas para tareas administrativas |
| A.8.3 | Restricción de acceso a la información | Sí | ✅ | Verificado: `GatewayConfig.java` excluye `/api/{auth,pedidos,envios}/interno/**` del enrutamiento público (`path(...).negate()`) — devuelven 404 sin importar el JWT, cerrando el IDOR que habría permitido pedir datos de otro usuario (`docs/designs/arco-acceso-personal-data.md`) |
| A.8.4 | Acceso al código fuente | Sí | ✅ | Repositorio Git con control de acceso de GitHub; `.gitignore` excluye `.env`/secretos |
| A.8.5 | Autenticación segura | Sí | ✅ | Cookie `httpOnly`/`Secure`/`SameSite=Lax` (no accesible por JS, mitiga robo de sesión vía XSS) — verificado en `AuthController.java`, `docs/designs/jwt-httponly-cookie-migration.md` |
| A.8.6 | Gestión de capacidad | Sí | ❌ | No hay monitoreo de capacidad/autoescalado |
| A.8.7 | Protección contra malware | Sí | ✅ | Trivy escanea imágenes Docker en busca de vulnerabilidades conocidas antes de build (`security.yml`, jobs `docker-image-scan`/`frontend-image-scan`) |
| A.8.8 | Gestión de vulnerabilidades técnicas | Sí | ✅ | Verificado: CodeQL (SAST, `codeql.yml`, java-kotlin + javascript-typescript), Trivy (filesystem + imágenes, severidad CRITICAL/HIGH), Gitleaks (secretos), Dependabot (7 ecosistemas) — pipeline completo de `.github/workflows/` |
| A.8.9 | Gestión de configuración | Sí | ✅ | Verificado: `validarConfiguracion()` (`@PostConstruct`) en los 9 componentes de firma/verificación HMAC interna (`InternalTokenSigner`/`InternalAuthFilter` en api-gateway, auth-service, ms-envios, ms-inventario, ms-pagos, ms-pedidos, notification-service) — falla al arrancar si `INTERNAL_SERVICE_SECRET` está vacío, mismo patrón que `JWT_SECRET`/`DB_PASS`/`FLOW_API_KEY` (`FlowService.java`) — configuración seguras por defecto, sin fallback inseguro |
| A.8.10 | Eliminación de información | Sí | ❌ | Ningún servicio implementa hoy cancelación/borrado de datos personales (Ley 21.719 "cancelación") — en diseño en un ciclo `/dev-cycle` paralelo a este documento |
| A.8.11 | Enmascaramiento de datos | Sí | ✅ | Verificado: tokens de Flow enmascarados en logs (`token.substring(0,8)+"***"`, `PagoService.java`); ningún log expone el valor de `INTERNAL_SERVICE_SECRET`/JWT completo (confirmado por grep en los 6 `InternalAuthFilter.java`) |
| A.8.12 | Prevención de fuga de datos (DLP) | Sí | ❌ | No hay herramienta de DLP — mitigado parcialmente por A.8.11 y por Flow procesando el PAN de tarjetas directamente (SmartLogix nunca recibe el número de tarjeta) |
| A.8.13 | Copias de seguridad de la información | Sí | ❌ | Verificado: no existe ninguna configuración de backup para los 5 volúmenes Postgres en `docker-compose.yml` — brecha real y concreta |
| A.8.14 | Redundancia de instalaciones de procesamiento | Sí | ❌ | Arquitectura de un solo host (Docker Compose) — sin redundancia/alta disponibilidad |
| A.8.15 | Registro (logging) | Sí | ✅ | Verificado: intentos de login (`AuthService.java`: "Login exitoso"/"Login fallido"), rechazos de autenticación interna (6 servicios), accesos ARCO+ (`UsuarioDatosController.java`: `[ARCO+] Solicitud de acceso...`) |
| A.8.16 | Actividades de monitoreo | Sí | ⚠️ | Hay logging estructurado pero no un sistema de monitoreo/alertamiento activo (SIEM, dashboards) que consuma esos logs en tiempo real |
| A.8.17 | Sincronización de reloj | Sí | ✅ | Relevante y verificado: el TTL de 30s de las firmas HMAC internas depende de reloj sincronizado — documentado explícitamente como supuesto en `docs/designs/internal-service-auth.md` §11 (mismo host Docker Compose hoy; riesgo señalado si se pasa a multi-host) |
| A.8.18 | Uso de programas de utilidad privilegiados | No | N/A | No aplica a este contexto de contenedores |
| A.8.19 | Instalación de software en sistemas operativos | No | N/A | Contenedores inmutables construidos desde `Dockerfile`, no hay instalación manual en producción |
| A.8.20 | Seguridad de redes | Sí | ✅ | Verificado: red interna `smartlogix-net` (Docker), `ms-inventario`/`ms-pedidos`/`ms-envios`/`ms-pagos`/`notification-service`/`auth-service` sin puerto publicado al host — solo `api-gateway` (8080) y `frontend` (nginx) expuestos |
| A.8.21 | Seguridad de los servicios de red | Sí | ✅ | HTTPS/TLS documentado como requisito de despliegue en `COMPLIANCE_CL.md` §4.1 (⚠️ pendiente en desarrollo local, por diseño) |
| A.8.22 | Segregación de redes | Sí | ✅ | La combinación de A.8.20 (puertos cerrados) + `docs/designs/internal-service-auth.md` (autenticación HMAC por servicio con allowlist de emisor por endpoint) es, en la práctica, segregación de red aplicada a nivel de aplicación — más estricta que solo aislar la red Docker |
| A.8.23 | Filtrado web | No | N/A | No aplica — SmartLogix no filtra tráfico saliente de usuarios |
| A.8.24 | Uso de criptografía | Sí | ✅ | Verificado: `BCryptPasswordEncoder(12)` (contraseñas), HMAC-SHA256 con `MessageDigest.isEqual` (comparación en tiempo constante, sin vulnerabilidad de timing attack) en los 6 `InternalAuthFilter.java` + `FlowService.java`, JWT firmado HS256 sin riesgo de "algorithm confusion" (`verifyWith(SecretKey)`) |
| A.8.25 | Ciclo de vida de desarrollo seguro | Sí | ✅ | El `dev-cycle` completo (architect → developer → reviewer → qa) es, literalmente, un SDLC seguro con puertas de calidad — ver `docs/AGENTIC_WORKFLOW.md` |
| A.8.26 | Requisitos de seguridad de aplicaciones | Sí | ✅ | El `architect` agent exige mapear OWASP Top 10 y cumplimiento chileno en cada diseño antes de implementar (`.claude/agents/architect.md`) |
| A.8.27 | Arquitectura segura y principios de ingeniería de sistemas | Sí | ✅ | Patrón BFF/API Gateway, defensa en profundidad (autenticación de usuario + autenticación interna servicio-a-servicio + allowlist por endpoint), principio de mínimo privilegio en el enrutamiento (`docs/designs/internal-service-auth.md` §5.2) |
| A.8.28 | Codificación segura | Sí | ✅ | Bean Validation en todos los DTOs de request (verificado en la auditoría OWASP previa), CodeQL SAST en cada push/PR |
| A.8.29 | Pruebas de seguridad en desarrollo y aceptación | Sí | ✅ | El `qa` agent prueba explícitamente casos de abuso (headers forjados, firmas inválidas, timestamps expirados, IDOR) como parte de los criterios de aceptación de cada ciclo — no solo el happy path (ver reportes QA de `internal-service-auth` y `arco-acceso-personal-data`) |
| A.8.30 | Pruebas de seguridad externalizadas | No | N/A | No hay pentesting externo contratado (esperable en un proyecto de portafolio) |
| A.8.31 | Separación de entornos de desarrollo, prueba y producción | Sí | ⚠️ | `docker-compose.yml` sirve tanto para desarrollo local como base de referencia — no hay un entorno de staging/producción separado con configuración distinta (más allá de `COOKIE_SECURE`/`FLOW_VERIFY_SIGNATURE` como flags condicionales) |
| A.8.32 | Gestión de cambios | Sí | ✅ | Cada cambio pasa por diseño documentado + revisión + pruebas antes de mergear a `main` (`dev-cycle`), con CI (`backend-ci.yml`/`frontend-ci.yml`) bloqueando builds rotos |
| A.8.33 | Información de prueba | Sí | ✅ | Verificado: `application-test`/valores dummy en tests (`ci-only-jwt-secret-do-not-use...` en `backend-ci.yml`) — nunca se usan secretos reales en pruebas |
| A.8.34 | Protección de sistemas de información durante pruebas de auditoría | No | N/A | No aplica sin auditorías externas activas hoy |

**Resumen cuantitativo** (de los 93 controles): **~46 aplicables e implementados (✅)**, **~14 aplicables pero parciales (⚠️)**, **~13 aplicables y pendientes (❌)**, **~20 no aplicables (N/A)** a este contexto. Para un proyecto de portafolio sin infraestructura física ni equipo humano, esta proporción es razonable — los controles ❌ son casi todos organizacionales/de gobernanza (backup, continuidad de negocio, runbooks), no técnicos de código, que es exactamente donde se esperaría la brecha en un proyecto de un solo desarrollador.

## 4. Brechas priorizadas (más allá de lo ya listado en `COMPLIANCE_CL.md` §4)

Estas son brechas que este mapeo hizo visibles y que **no estaban explícitas** en `COMPLIANCE_CL.md` antes de este documento:

1. **A.5.30 / A.8.13 — Sin backup de bases de datos ni plan de continuidad TIC.** Verificado: ningún volumen Postgres en `docker-compose.yml` tiene estrategia de respaldo. Es una brecha real, no solo de proceso — si el volumen Docker se corrompe, no hay forma de recuperar datos de clientes. Prioridad alta para cualquier despliegue con datos reales.
2. **A.8.31 — Sin separación real de entornos.** Un solo `docker-compose.yml` para todo. Antes de producción, se necesitaría al menos una configuración de staging separada.
3. **A.5.24–A.5.27 — Sin runbook de incidentes ejecutable.** Ya señalado en `COMPLIANCE_CL.md` §4.3 como pendiente organizacional; este mapeo confirma que es, de hecho, un clúster de 4 controles Annex A relacionados, no uno solo.

Ninguna de estas tres brechas es nueva para el proyecto — todas ya estaban implícitas en `COMPLIANCE_CL.md` §4.3/§6 — pero este documento las hace **explícitas contra un estándar externo verificable**, que es justamente el valor de hacer el ejercicio de mapeo.

## 5. Camino hacia una certificación ISO 27001 real

Lo implementado en código (Sección 3) es **necesario pero no suficiente**. Una certificación real exigiría, como mínimo:

1. **Declaración de alcance del SGSI** — qué procesos/activos/ubicaciones cubre el sistema de gestión (no solo el código).
2. **Metodología formal de evaluación de riesgos** (p. ej. ISO/IEC 27005) — hoy los "riesgos" en este proyecto se identifican ad-hoc durante el diseño de cada feature (`architect` agent), no mediante una metodología de riesgo documentada y repetible.
3. **Compromiso documentado de la dirección** — no aplica de la misma forma a un proyecto de un solo desarrollador, pero sería un requisito real en cualquier empresa.
4. **Statement of Applicability formal** — este documento es una versión simplificada; el SoA real de una certificación incluye justificación detallada control por control, aprobada por la dirección.
5. **Programa de auditoría interna** — revisiones periódicas del propio SGSI, independientes del equipo que lo opera (parcialmente análogo al rol `reviewer`, pero ese audita código, no el sistema de gestión completo).
6. **Revisión por la dirección** — ciclo PDCA (Plan-Do-Check-Act) formal, con reuniones y actas.
7. **Auditoría de certificación externa** — por un organismo de certificación acreditado (p. ej. bajo IAS/ANAB), en dos etapas (revisión documental + auditoría in situ), y auditorías de mantención anuales.

Para un proyecto de portafolio, lo relevante no es simular tener estos siete puntos, sino **poder explicar en una entrevista la diferencia entre "tengo controles técnicos alineados con Annex A" y "estoy certificado ISO 27001"** — son cosas distintas, y la brecha entre ambas es principalmente de gobernanza/proceso, no de código.

## 6. Cómo se usa este documento en el loop agéntico

Mismo patrón que `COMPLIANCE_CL.md` §5:
- **architect**: al diseñar una feature con superficie de seguridad relevante (autenticación, criptografía, manejo de datos personales), puede citar el control Annex A correspondiente de la Sección 3 además de OWASP/`COMPLIANCE_CL.md`.
- **reviewer**: puede usar la Sección 3 como checklist adicional para verificar que un cambio no debilite un control ya marcado ✅ (p. ej. si un futuro diff cambia cómo se comparan firmas HMAC, debe seguir marcado A.8.24 ✅, no regresar a `.equals()`).
- Este documento debe actualizarse cuando una feature nueva mueva un control de la Sección 3 de `❌`/`⚠️` a `✅`, igual que `COMPLIANCE_CL.md` §5.

## Fuentes consultadas

- [ISO 27001 Annex A Controls List: All 93 Controls by Theme](https://gaicc.org/blog/iso-27001-annex-a-controls-list/)
- [ISO 27001:2022 Annex A Controls Reference Guide — High Table](https://hightable.io/iso-27001-annex-a-controls-reference-guide/)
- [Control 8.24 – Use of Cryptography — ISMS.online](https://www.isms.online/iso-27002/control-8-24-use-of-cryptography/)
- [Control 8.16 — Monitoring activities](https://copla.com/blog/compliance-regulations/iso-27001-2022-controls-changes/)
- [Control 8.20/8.21/8.22 — Network security / Segregation of networks — ISMS.online / Aviso Consultancy](https://www.avisoconsultancy.co.uk/iso-27001-2022-annex-a/8-22-segregation-of-networks)
- [Control 5.30 — ICT readiness for business continuity — High Table](https://hightable.io/iso-27001-annex-a-5-30-ict-readiness-for-business-continuity/)
- [Control 8.8 — Management of technical vulnerabilities — Advisera](https://advisera.com/iso27001/control-8-8-management-of-technical-vulnerabilities/)
- [Control 5.19/5.20/5.21 — Supplier relationships / ICT supply chain — URM Consulting](https://www.urmconsulting.com/blog/iso-27001-2022-a-5-organisational-controls-supplier-management)
- [Control 5.15 — Access Control — ISMS.online](https://www.isms.online/iso-27001/annex-a-2022/5-15-access-control-2022/)
- [Ley Marco de Ciberseguridad Chile (21.663): guía 2026 — Prey Project](https://preyproject.com/es/blog/ley-21663-marco-de-ciberseguridad-en-chile)
- [Cómo cumplir con la Ley 21.663: guía práctica del SGSI e ISO 27001 — Prey Project](https://preyproject.com/es/blog/sgsi-iso27001-ley-21663-ciberseguridad-chile)
- [Ley Marco de Ciberseguridad e ISO 27001 — Cómo cumplir en Chile — Confiden360](https://confiden360.com/guia-iso-27001/ley-marco-ciberseguridad/)
