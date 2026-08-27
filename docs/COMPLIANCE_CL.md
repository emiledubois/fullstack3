# Cumplimiento normativo (Chile) — SmartLogix

> **Disclaimer:** este documento es una guía de ingeniería para alinear el diseño técnico de SmartLogix con la legislación chilena de ciberseguridad y protección de datos personales. **No es asesoría legal.** La clasificación regulatoria real de una empresa (p. ej. si califica como Operador de Importancia Vital bajo la Ley 21.663, o los umbrales de "tratamiento a gran escala" bajo la Ley 21.719) depende de hechos específicos del negocio y debe confirmarla un abogado antes de cualquier lanzamiento en producción con usuarios reales. El valor de este documento es servir de **checklist de buenas prácticas verificable por el equipo de revisión (reviewer) y QA** dentro del loop de `dev-cycle`, no una certificación de cumplimiento.

## 1. Marco normativo relevante

| Ley | Nombre | Publicación | Entrada en vigencia | Por qué aplica a SmartLogix |
|---|---|---|---|---|
| **Ley 21.663** | Ley Marco de Ciberseguridad e Infraestructura Crítica | 08-04-2024 | Escalonada; obligaciones generales de gestión de ciberseguridad y reporte de incidentes ya vigentes/en despliegue por la ANCI (Agencia Nacional de Ciberseguridad e Infraestructura) durante 2025-2026, con reglamentos complementarios publicándose por tramos | SmartLogix procesa pagos y datos operacionales de PYMEs — aunque probablemente no califique como Operador de Importancia Vital (OIV) hoy, el estándar de "gestión de riesgos de ciberseguridad + reporte de incidentes" es la referencia de buenas prácticas exigible a cualquier proveedor de servicios digitales que quiera vender a clientes regulados (retail, logística, sector financiero) |
| **Ley 21.719** | Nueva Ley de Protección de Datos Personales (reemplaza/moderniza la Ley 19.628) | 13-12-2024 | **01-12-2026** (vacancia legal de 24 meses) | SmartLogix trata datos personales de clientes y usuarios (email, nombre, dirección de destino, historial de pedidos) en auth-service, ms-pedidos, ms-envios y ms-pagos — está directamente en el ámbito de aplicación de esta ley |

**Fecha de referencia de este documento: 2026-08-26.** La Ley 21.719 entra en vigor el **2026-12-01** — quedan ~3 meses. Cualquier feature nueva que toque datos de clientes debe pasar el checklist de la sección 4 desde ahora, no después de diciembre.

## 2. Inventario de datos personales/sensibles por servicio

| Servicio | Datos que procesa | Categoría |
|---|---|---|
| `auth-service` | email, hash de contraseña | Dato personal + credencial |
| `ms-pedidos` | nombre cliente, email, destino, historial de compra | Dato personal |
| `ms-envios` | dirección de destino, estado de envío | Dato personal (localización) |
| `ms-pagos` | referencia de orden Flow, estado de pago, monto | Dato de transacción (**no** número de tarjeta — Flow Chile procesa el PAN, SmartLogix nunca lo recibe ni almacena, lo que reduce el alcance de PCI-DSS y de "datos sensibles" bajo la Ley 21.719 a lo estrictamente transaccional) |
| `notification-service` | email destinatario, contenido de notificación | Dato personal (tránsito, sin persistencia — sin BD propia) |
| Logs de todos los servicios | potencialmente IPs, emails en payloads de error | Dato personal si no se sanitiza (ver 4.4) |

## 3. Qué exige cada ley (resumen operativo)

### Ley 21.663 — Marco de Ciberseguridad
- Gestión de riesgos de ciberseguridad basada en estándares reconocidos (alineable con OWASP ASVS / ISO 27001 a nivel de prácticas, sin exigir la certificación formal para una PYME no-OIV).
- **Reporte de incidentes** a la ANCI dentro de plazos definidos (fases de 3h/24h/72h según gravedad, para entidades reguladas) — para SmartLogix, el equivalente de buena práctica es tener **capacidad técnica de detectar, registrar y escalar un incidente rápido**, aunque no exista aún la obligación formal de reportar a la ANCI.
- Continuidad operacional y resiliencia (SmartLogix ya cubre parte de esto con Circuit Breaker/Resilience4j y compensaciones de Saga).
- Designación de un responsable de ciberseguridad (rol, no necesariamente un cargo formal en una PYME).

### Ley 21.719 — Protección de Datos Personales
- **Principios**: licitud, finalidad, proporcionalidad, calidad, seguridad, transparencia, responsabilidad demostrable (accountability).
- **Derechos ARCO+**: Acceso, Rectificación, Cancelación/supresión, Oposición, y **Portabilidad** (nuevo respecto a la Ley 19.628).
- **Deber de seguridad**: medidas técnicas y organizativas proporcionales al riesgo — cifrado, control de acceso, minimización.
- **Notificación de brechas**: a la Agencia de Protección de Datos Personales y, si hay riesgo para los titulares, a los propios titulares, "sin demora indebida".
- **Evaluación de impacto (DPIA)** para tratamientos de alto riesgo o a gran escala.
- **Privacidad desde el diseño y por defecto**.
- Bases de licitud para tratamiento (consentimiento, ejecución de contrato, interés legítimo, etc.) — relevante para el registro/login y para el checkout con Flow.

## 4. Checklist de controles (usar en `reviewer` y `qa` durante `dev-cycle`)

Marcar cada ítem como `✅ implementado`, `⚠️ parcial`, o `❌ pendiente`. Este estado debe revisarse cada vez que una feature nueva toque datos personales (el `architect` debe citar la sección aplicable en el diseño, ver `.claude/agents/architect.md` sección 7).

### 4.1 Seguridad técnica (deber de seguridad, Ley 21.719 + gestión de riesgo, Ley 21.663)
| Control | Estado | Evidencia / gap |
|---|---|---|
| Contraseñas con hash fuerte (BCrypt/Argon2), nunca en texto plano | ✅ `BCryptPasswordEncoder(12)` | `auth-service` — `SecurityConfig.java` |
| JWT sin secreto hardcodeado, falla al arrancar sin `JWT_SECRET` | ✅ + algoritmo fijado (HS256, `verifyWith(SecretKey)`, sin riesgo de algorithm confusion) | `api-gateway`, `auth-service` |
| Comparación de firma HMAC en tiempo constante (`MessageDigest.isEqual`) | ✅ confirmado en auditoría (2026-08-26) | `ms-pagos` |
| CORS sin wildcard `*` con credenciales | ✅ | `api-gateway` |
| Rate limiting en login | ✅ Resilience4j 5/60s | `auth-service` |
| `.env` fuera del repositorio, sin secretos commiteados | ✅ | raíz del repo |
| Sin servicios internos alcanzables saltándose el gateway | ✅ **corregido 2026-08-26** — `ms-inventario`, `ms-pedidos`, `ms-envios`, `ms-pagos`, `notification-service` no publicaban puerto autenticado propio y eran alcanzables directo desde el host (OWASP A01 crítico); ya no publican puertos al host | `docker-compose.yml` |
| Validación de entrada (Bean Validation) en todos los endpoints | ✅ **corregido 2026-08-26** — antes solo `ms-inventario` la tenía; se agregó a auth-service, ms-pedidos, ms-envios, ms-pagos | DTOs de request de todos los servicios |
| Sin contraseña de BD hardcodeada/por defecto | ✅ **corregido 2026-08-26** — `DB_PASS` sin fallback (antes `secret` hardcodeado en 5 bases) | `docker-compose.yml` |
| Cabeceras de seguridad HTTP (CSP, X-Frame-Options, etc.) | ✅ **corregido 2026-08-26** | `frontend/smartlogix-app/nginx.conf` |
| Cifrado en tránsito (HTTPS) en producción | ⚠️ pendiente — desarrollo local usa HTTP; documentar como requisito de despliegue (TLS termination en el reverse proxy de producción) | infraestructura |
| Minimización: no loggear PII/credenciales/datos de pago en texto plano | ✅ confirmado en auditoría — token de Flow enmascarado en logs (`token.substring(0,8)+"***"`), sin card data (Flow procesa el PAN, nunca llega a SmartLogix) | todos los servicios |
| Control de acceso por propietario, no solo autenticación (anti-IDOR) | N/A — modelo de dominio es single-tenant por PYME (pool de staff compartido, sin `tenant_id`/segregación por cliente), confirmado en auditoría que no aplica IDOR clásico hoy; revisar si el modelo evoluciona a multi-tenant | controllers de `ms-pedidos`, `ms-envios`, `ms-pagos` |
| Autenticación servicio-a-servicio interna (mTLS o JWT de servicio) | ✅ implementado — firma HMAC-SHA256 simétrica (`X-Internal-Service`/`X-Internal-Timestamp`/`X-Internal-Signature`), verificada por servicio con allowlist de emisor por endpoint (ver `docs/designs/internal-service-auth.md`); `POST /pedidos/{id}/confirmar-pago` ahora rechaza cualquier llamador que no firme como `ms-pagos` | comunicación inter-servicio |
| No exponer información de usuario en errores (anti user-enumeration) | ⚠️ pendiente — `AuthController.register()` revela si un email ya existe; severidad baja, requiere flujo de verificación por email para resolver bien | `auth-service` |
| JWT no accesible por JavaScript (anti-XSS token exfiltration) | ✅ **corregido 2026-08-26** — antes el JWT vivía en `localStorage`, legible por cualquier script inyectado; migrado a cookie `httpOnly`/`Secure` (condicional a `COOKIE_SECURE`)/`SameSite=Lax`, ver `docs/designs/jwt-httponly-cookie-migration.md` | `auth-service`, `api-gateway`, `frontend` |

### 4.2 Derechos ARCO+ (Ley 21.719, art. sobre derechos de los titulares)
| Derecho | Estado | Nota |
|---|---|---|
| Acceso (ver qué datos propios tiene SmartLogix) | ❌ pendiente | Requiere endpoint `GET /api/usuarios/me/datos` que agregue datos del titular desde auth-service + ms-pedidos + ms-envios — candidato natural para un ciclo `/dev-cycle` futuro, con `architect` diseñando el contrato BFF análogo al `/api/dashboard` existente |
| Rectificación | ❌ pendiente | Endpoint de actualización de perfil |
| Cancelación / supresión | ❌ pendiente | Requiere estrategia de borrado o anonimización cross-servicio (database-per-service complica el borrado atómico — buen caso de uso para el patrón Saga ya usado en `ms-pedidos`) |
| Oposición | ❌ pendiente | Depende de qué tratamientos opcionales existan (p. ej. notificaciones de marketing, si se agregan) |
| Portabilidad | ❌ pendiente | Export en formato estructurado (JSON) de los datos del titular |

> Nota de alcance: el equipo decidió para esta iteración un **checklist + mapeo de controles**, no la implementación completa de estos endpoints. Quedan listados aquí como trabajo futuro priorizable con `/dev-cycle` antes de diciembre 2026.

### 4.3 Notificación de brechas (ambas leyes)
| Control | Estado |
|---|---|
| Runbook de respuesta a incidentes documentado | ⚠️ ver `docs/INCIDENT_RESPONSE.md` (a crear si el equipo decide implementarlo) |
| Logging suficiente para reconstruir un incidente (quién, qué, cuándo) sin loggear secretos | Verificar en auditoría viva |
| Punto de contacto/responsable designado para reportar una brecha | ❌ pendiente — es una decisión organizacional, no técnica |

### 4.4 Logging y auditoría (Ley 21.663 — trazabilidad; Ley 21.719 — accountability)
| Control | Estado |
|---|---|
| Eventos de seguridad relevantes logueados (login fallido, rechazo de auth, cambios de estado de pago) | Verificar por servicio |
| Logs no contienen contraseñas, tokens JWT completos, ni número de tarjeta | Verificar en auditoría viva |
| Retención de logs definida (no indefinida) | ❌ pendiente — definir política, p. ej. 90 días, antes de producción |

## 5. Cómo se usa este documento en el loop agéntico

- **architect**: al diseñar cualquier feature que cree, almacene, exponga o borre datos personales, debe citar los ítems relevantes de la sección 4 en el diseño (`docs/designs/<slug>.md`, sección "Chilean compliance touchpoints").
- **reviewer**: al revisar el diff, referencia la sección 4.1 (controles técnicos) como parte del checklist OWASP — un `BLOCKING` de A01/A02/A09 en `reviewer.md` normalmente corresponde a un gap aquí.
- **qa**: si una feature toca derechos ARCO+ o notificación de brechas, sus criterios de aceptación deben probar explícitamente ese flujo, no solo el happy path técnico.
- Este documento **debe actualizarse** cada vez que un hallazgo de seguridad confirmado (ver auditoría OWASP) tenga relevancia de cumplimiento — mover el ítem de `❌`/`⚠️` a `✅` con referencia al commit/PR que lo corrigió.

## 6. Priorización sugerida antes de diciembre 2026

1. Cerrar los `⚠️`/`❌` de la sección 4.1 (controles técnicos base) — son prerequisito de todo lo demás.
2. Definir política de retención de logs y runbook de incidentes (organizacional, bajo costo).
3. Implementar al menos **Acceso** y **Cancelación** de ARCO+ (los dos derechos más comúnmente auditados) vía `/dev-cycle` antes de cualquier despliegue con datos reales de clientes.
4. Evaluar con un abogado si el volumen/naturaleza de datos de un despliegue real activa obligaciones adicionales (DPIA, registro de actividades de tratamiento) bajo la Ley 21.719.
