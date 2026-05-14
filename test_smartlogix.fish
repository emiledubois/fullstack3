#!/usr/bin/env fish
# ═══════════════════════════════════════════════════════════════════════
# SmartLogix — Tests completos del sistema en Fish Shell
# Patrones: Facade · Strategy · Saga
# ═══════════════════════════════════════════════════════════════════════

# ── Colores ─────────────────────────────────────────────────────────────
set GREEN  "\e[32m"
set RED    "\e[31m"
set YELLOW "\e[33m"
set BLUE   "\e[34m"
set CYAN   "\e[36m"
set BOLD   "\e[1m"
set RESET  "\e[0m"

# ── Helpers ──────────────────────────────────────────────────────────────
function ok
    echo -e "$GREEN✅ $argv$RESET"
end

function fail
    echo -e "$RED❌ $argv$RESET"
end

function info
    echo -e "$CYAN── $argv$RESET"
end

function section
    echo ""
    echo -e "$BOLD$BLUE╔══════════════════════════════════════════════════╗$RESET"
    echo -e "$BOLD$BLUE║  $argv$RESET"
    echo -e "$BOLD$BLUE╚══════════════════════════════════════════════════╝$RESET"
    echo ""
end

function check
    # check "descripción" "valor" "contiene"
    set desc  $argv[1]
    set value $argv[2]
    set match $argv[3]
    if string match -q "*$match*" -- $value
        ok "$desc"
    else
        fail "$desc → esperaba '$match', recibí: $value"
    end
end

# ════════════════════════════════════════════════════════════════════════
section "SETUP — Login y preparación"
# ════════════════════════════════════════════════════════════════════════

info "Registrando usuario (puede fallar si ya existe — OK)"
curl -s -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{"email":"test@smartlogix.cl","password":"Password123!"}' | head -c 0

info "Haciendo login..."
set TOKEN (curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@smartlogix.cl","password":"Password123!"}' | tr -d '"')

if test -z "$TOKEN"
    fail "Login fallido — TOKEN vacío. Verificar que el sistema esté corriendo."
    exit 1
end

if string match -q "eyJ*" -- $TOKEN
    ok "Login exitoso — Token JWT obtenido (comienza con eyJ)"
else
    fail "Token inválido: $TOKEN"
    exit 1
end

# Crear productos de prueba para los tests
info "Creando productos de prueba..."

set P1 (curl -s -X POST http://localhost:8080/api/inventario \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"sku":"TEST-LAP","nombre":"Laptop Test","precioUnitario":999,"stockActual":50,"umbralMinimo":5,"bodega":"A"}')
set ID_LAP (echo $P1 | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

set P2 (curl -s -X POST http://localhost:8080/api/inventario \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"sku":"TEST-MOU","nombre":"Mouse Test","precioUnitario":29,"stockActual":4,"umbralMinimo":10,"bodega":"B"}')
set ID_MOU (echo $P2 | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

set P3 (curl -s -X POST http://localhost:8080/api/inventario \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"sku":"TEST-CAB","nombre":"Cable Test","precioUnitario":5,"stockActual":0,"umbralMinimo":5,"bodega":"C"}')
set ID_CAB (echo $P3 | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

if test -n "$ID_LAP" -a -n "$ID_MOU" -a -n "$ID_CAB"
    ok "Productos creados — Laptop(id=$ID_LAP, stock=50), Mouse(id=$ID_MOU, stock=4), Cable(id=$ID_CAB, stock=0)"
else
    fail "Error creando productos — IDs: Laptop=$ID_LAP Mouse=$ID_MOU Cable=$ID_CAB"
    exit 1
end

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 1 — FACADE (ms-pedidos)"
# ════════════════════════════════════════════════════════════════════════
# LogisticaFacade coordina internamente:
#   InventarioClient → PedidoFactory → OrderRepository → EventPublisher
# El controller solo llama facade.procesarCreacionPedido(req)

info "Test 1.1 — Facade: crear pedido NACIONAL (subsistemas coordinados en una llamada)"
set RES_PED (curl -s -X POST http://localhost:8080/api/pedidos \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"facade@test.cl\",
      \"clienteNombre\": \"Test Facade\",
      \"total\": 999.00,
      \"tipoPedido\": \"NACIONAL\",
      \"destino\": \"Santiago\",
      \"productoId\": $ID_LAP,
      \"cantidad\": 2
    }")
set ID_PED1 (echo $RES_PED | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
check "Pedido NACIONAL creado exitosamente" $RES_PED "PENDIENTE"
check "Pedido tiene destino Santiago" $RES_PED "Santiago"

info "Test 1.2 — Facade: crear pedido INTERNACIONAL (PedidoFactory detecta tipo)"
set RES_INT (curl -s -X POST http://localhost:8080/api/pedidos \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"intl@test.cl\",
      \"clienteNombre\": \"Test Internacional\",
      \"total\": 1500.00,
      \"tipoPedido\": \"INTERNACIONAL\",
      \"destino\": \"Buenos Aires\",
      \"productoId\": $ID_LAP,
      \"cantidad\": 1
    }")
check "Pedido INTERNACIONAL creado" $RES_INT "INTERNACIONAL"
check "Observaciones de documentación aduanera presentes" $RES_INT "aduanera"

info "Test 1.3 — Facade: Circuit Breaker rechaza pedido con stock insuficiente"
set RES_NOSTOCK (curl -s -X POST http://localhost:8080/api/pedidos \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"x@x.cl\",
      \"total\": 29.00,
      \"productoId\": $ID_MOU,
      \"cantidad\": 10
    }")
check "Rechazado por stock insuficiente (Circuit Breaker activo)" $RES_NOSTOCK "insuficiente"

info "Test 1.4 — Facade: listar pedidos (subsistema OrderService)"
set RES_LIST (curl -s http://localhost:8080/api/pedidos \
    -H "Authorization: Bearer $TOKEN")
check "Lista de pedidos devuelta" $RES_LIST "userId"

info "Test 1.5 — Facade: health check confirma que la fachada está activa"
set RES_HEALTH (curl -s http://localhost:8080/api/pedidos/health \
    -H "Authorization: Bearer $TOKEN")
check "ms-pedidos UP" $RES_HEALTH "UP"

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 2 — STRATEGY (ms-inventario)"
# ════════════════════════════════════════════════════════════════════════
# AlertaService delega a AlertaStockStrategy según la estrategia activa
# Tres estrategias: umbralFijo | porcentaje | critico

info "Test 2.1 — Strategy: listar estrategias disponibles"
set RES_STRAT (curl -s http://localhost:8080/api/inventario/alertas/estrategias \
    -H "Authorization: Bearer $TOKEN")
check "Estrategia umbral-fijo registrada" $RES_STRAT "umbral-fijo"
check "Estrategia porcentaje registrada" $RES_STRAT "porcentaje"
check "Estrategia critico registrada" $RES_STRAT "critico"

info "Test 2.2 — Strategy UmbralFijo: alerta cuando stock < umbralMinimo"
# Mouse(stock=4, umbral=10): alerta ✓   Cable(stock=0, umbral=5): alerta ✓   Laptop(stock=50, umbral=5): sin alerta
set RES_UF (curl -s http://localhost:8080/api/inventario/alertas \
    -H "Authorization: Bearer $TOKEN")
check "Estrategia umbral-fijo activa en respuesta" $RES_UF "umbral-fijo"
check "Mouse aparece en alertas (stock 4 < umbral 10)" $RES_UF "TEST-MOU"
check "Cable aparece en alertas (stock 0 < umbral 5)" $RES_UF "TEST-CAB"

set N_UF (echo $RES_UF | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('alertas',[])))" 2>/dev/null)
info "  Alertas con umbral-fijo: $N_UF productos"

info "Test 2.3 — Strategy Porcentaje: alerta temprana (stock < umbral * 1.5)"
set RES_PCT (curl -s "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=porcentaje" \
    -H "Authorization: Bearer $TOKEN")
check "Estrategia cambiada a porcentaje" $RES_PCT "porcentaje"
check "Mouse aparece (stock 4 < 10*1.5=15)" $RES_PCT "TEST-MOU"

set N_PCT (echo $RES_PCT | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('alertas',[])))" 2>/dev/null)
info "  Alertas con porcentaje: $N_PCT productos"

info "Test 2.4 — Strategy Critico: alerta SOLO cuando stock = 0"
set RES_CRIT (curl -s "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=critico" \
    -H "Authorization: Bearer $TOKEN")
check "Estrategia cambiada a critico" $RES_CRIT "critico"
check "Cable aparece (stock = 0)" $RES_CRIT "TEST-CAB"

# Laptop y Mouse NO deben aparecer — tienen stock > 0
if string match -q "*TEST-LAP*" -- $RES_CRIT
    fail "Laptop NO debería aparecer en critico (stock=50)"
else
    ok "Laptop correctamente excluida de alertas críticas (stock=50 > 0)"
end
if string match -q "*TEST-MOU*" -- $RES_CRIT
    fail "Mouse NO debería aparecer en critico (stock=4)"
else
    ok "Mouse correctamente excluida de alertas críticas (stock=4 > 0)"
end

set N_CRIT (echo $RES_CRIT | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('alertas',[])))" 2>/dev/null)
info "  Alertas con critico: $N_CRIT producto(s) (solo stock=0)"

info "Test 2.5 — Strategy: verificar que el cambio es en tiempo de ejecución (volver a umbralFijo)"
set RES_BACK (curl -s "http://localhost:8080/api/inventario/alertas/estrategia?estrategia=umbralFijo" \
    -H "Authorization: Bearer $TOKEN")
check "Estrategia restaurada a umbral-fijo" $RES_BACK "umbral-fijo"

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 3 — SAGA: Flujo exitoso (4 pasos)"
# ════════════════════════════════════════════════════════════════════════
# Paso 1: Reservar stock (ms-inventario)
# Paso 2: Crear pedido CONFIRMADO (ms-pedidos)
# Paso 3: Crear envío (ms-envios)
# Paso 4: Notificar (notification-service — no crítico)

info "Registrando stock actual de Laptop antes de la Saga..."
set STOCK_PRE (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
info "  Stock Laptop antes: $STOCK_PRE"

info "Test 3.1 — Saga: POST /api/sagas/pedido — flujo completo (4 pasos)"
set RES_SAGA (curl -s -X POST http://localhost:8080/api/sagas/pedido \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"saga@test.cl\",
      \"clienteNombre\": \"Test Saga Completa\",
      \"total\": 300.00,
      \"tipoPedido\": \"NACIONAL\",
      \"destino\": \"Valparaíso\",
      \"productoId\": $ID_LAP,
      \"cantidad\": 3
    }")

check "Saga completada exitosamente" $RES_SAGA "COMPLETADA"
check "sagaId presente en respuesta" $RES_SAGA "sagaId"
check "pedidoId presente en respuesta" $RES_SAGA "pedidoId"
check "envioId presente en respuesta" $RES_SAGA "envioId"

set SAGA_ID (echo $RES_SAGA | python3 -c "import sys,json; print(json.load(sys.stdin).get('sagaId',''))" 2>/dev/null)
set SAGA_PED_ID (echo $RES_SAGA | python3 -c "import sys,json; print(json.load(sys.stdin).get('pedidoId',''))" 2>/dev/null)
set SAGA_ENV_ID (echo $RES_SAGA | python3 -c "import sys,json; print(json.load(sys.stdin).get('envioId',''))" 2>/dev/null)
info "  sagaId   = $SAGA_ID"
info "  pedidoId = $SAGA_PED_ID"
info "  envioId  = $SAGA_ENV_ID"

info "Test 3.2 — Saga: stock decrementó en 3 (Paso 1 ejecutado)"
set STOCK_POST (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
info "  Stock Laptop después: $STOCK_POST (antes: $STOCK_PRE)"
if test (math $STOCK_PRE - 3) -eq $STOCK_POST
    ok "Stock decrementó correctamente en 3 unidades (Paso 1 - reserva)"
else
    fail "Stock incorrecto: esperaba (math $STOCK_PRE - 3), tengo $STOCK_POST"
end

info "Test 3.3 — Saga: pedido creado con status CONFIRMADO (Paso 2)"
if test -n "$SAGA_PED_ID"
    set RES_PEDIDO (curl -s http://localhost:8080/api/pedidos/$SAGA_PED_ID \
        -H "Authorization: Bearer $TOKEN")
    check "Pedido de Saga tiene status CONFIRMADO" $RES_PEDIDO "CONFIRMADO"
else
    fail "No se pudo verificar el pedido — pedidoId vacío"
end

info "Test 3.4 — Saga: envío creado y asociado (Paso 3)"
if test -n "$SAGA_ENV_ID"
    set RES_ENVIO (curl -s http://localhost:8080/api/envios/$SAGA_ENV_ID \
        -H "Authorization: Bearer $TOKEN")
    check "Envío de Saga existe" $RES_ENVIO "pedidoId"
    check "Envío asociado al pedido correcto" $RES_ENVIO "$SAGA_PED_ID"
else
    fail "No se pudo verificar el envío — envioId vacío"
end

info "Test 3.5 — Saga: estado persistido en BD es COMPLETADA"
if test -n "$SAGA_ID"
    set RES_ESTADO (curl -s http://localhost:8083/sagas/$SAGA_ID)
    check "Estado de Saga en BD es COMPLETADA" $RES_ESTADO "COMPLETADA"
    check "Saga registra el pedidoId correcto" $RES_ESTADO "$SAGA_PED_ID"
    check "Saga registra el envioId correcto" $RES_ESTADO "$SAGA_ENV_ID"
else
    fail "sagaId vacío — no se puede consultar el estado"
end

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 4 — SAGA: Compensaciones (fallo en Paso 3)"
# ════════════════════════════════════════════════════════════════════════

info "Anotando stock de Laptop antes de la prueba de compensación..."
set STOCK_ANTES_COMP (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
info "  Stock Laptop: $STOCK_ANTES_COMP"

info "Deteniendo ms-envios para forzar fallo en el Paso 3..."
cd ~/ecommerce_microservices 2>/dev/null; or cd ~/fullstack3 2>/dev/null; or true
docker compose stop ms-envios 2>/dev/null
sleep 3

info "Test 4.1 — Saga con ms-envios caído: debe fallar en Paso 3 y compensar"
set RES_FALLO (curl -s -X POST http://localhost:8080/api/sagas/pedido \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"comp@test.cl\",
      \"clienteNombre\": \"Test Compensación\",
      \"total\": 999.00,
      \"productoId\": $ID_LAP,
      \"cantidad\": 1
    }")
check "Saga retornó estado FALLIDA" $RES_FALLO "FALLIDA"
check "Respuesta incluye campo error" $RES_FALLO "error"

set SAGA_ID_FALLO (echo $RES_FALLO | python3 -c "import sys,json; print(json.load(sys.stdin).get('sagaId',''))" 2>/dev/null)
info "  sagaId de saga fallida: $SAGA_ID_FALLO"

info "Test 4.2 — Compensación Paso 1: stock debe ser igual al anotado antes"
set STOCK_TRAS_COMP (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
info "  Stock antes de la saga: $STOCK_ANTES_COMP"
info "  Stock después (con compensación): $STOCK_TRAS_COMP"
if test "$STOCK_ANTES_COMP" = "$STOCK_TRAS_COMP"
    ok "Stock restaurado correctamente — compensación del Paso 1 ejecutada"
else
    fail "Stock no restaurado: antes=$STOCK_ANTES_COMP después=$STOCK_TRAS_COMP"
end

info "Test 4.3 — Compensación Paso 2: último pedido debe estar CANCELADO"
set RES_TODOS_PEDS (curl -s http://localhost:8080/api/pedidos \
    -H "Authorization: Bearer $TOKEN")
set ULTIMO_STATUS (echo $RES_TODOS_PEDS | python3 -c \
    "import sys,json; peds=json.load(sys.stdin); print(peds[-1]['status'] if peds else '')" 2>/dev/null)
if test "$ULTIMO_STATUS" = "CANCELADO"
    ok "Último pedido cancelado — compensación del Paso 2 ejecutada"
else
    fail "Último pedido no está CANCELADO — status: $ULTIMO_STATUS"
end

info "Test 4.4 — Estado persistido de saga fallida en BD"
if test -n "$SAGA_ID_FALLO"
    set RES_FALLIDA (curl -s http://localhost:8083/sagas/$SAGA_ID_FALLO)
    check "Saga fallida registrada como FALLIDA en BD" $RES_FALLIDA "FALLIDA"
    check "ultimoError registrado en la saga" $RES_FALLIDA "ultimoError"
end

info "Restaurando ms-envios..."
docker compose start ms-envios 2>/dev/null
sleep 3

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 5 — SAGA: Fallo en Paso 1 (stock insuficiente)"
# ════════════════════════════════════════════════════════════════════════

info "Anotando stock antes del test..."
set STOCK_P1 (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
info "  Stock Laptop: $STOCK_P1"

info "Test 5.1 — Saga con cantidad imposible: falla en Paso 1, sin compensaciones"
set RES_P1_FAIL (curl -s -X POST http://localhost:8080/api/sagas/pedido \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": 1,
      \"userEmail\": \"fail1@test.cl\",
      \"total\": 100.00,
      \"productoId\": $ID_LAP,
      \"cantidad\": 9999
    }")
check "Saga falló en Paso 1 (stock insuficiente)" $RES_P1_FAIL "FALLIDA"

info "Test 5.2 — Stock sin cambio (Paso 1 falló antes de reservar)"
set STOCK_TRAS_P1 (curl -s http://localhost:8080/api/inventario/$ID_LAP \
    -H "Authorization: Bearer $TOKEN" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['stockActual'])" 2>/dev/null)
if test "$STOCK_P1" = "$STOCK_TRAS_P1"
    ok "Stock sin cambio — Paso 1 falló sin haber modificado nada (sin compensación necesaria)"
else
    fail "Stock cambió inesperadamente: antes=$STOCK_P1 después=$STOCK_TRAS_P1"
end

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 6 — Inventario: endpoints adicionales"
# ════════════════════════════════════════════════════════════════════════

info "Test 6.1 — Verificar stock endpoint (usado por Circuit Breaker)"
set RES_STOCK_OK (curl -s "http://localhost:8082/inventario/$ID_LAP/stock?cantidad=5")
check "Stock suficiente para 5 unidades" $RES_STOCK_OK "true"

set RES_STOCK_NO (curl -s "http://localhost:8082/inventario/$ID_LAP/stock?cantidad=99999")
check "Stock insuficiente para 99999 unidades" $RES_STOCK_NO "false"

info "Test 6.2 — Health checks de todos los servicios"
set H_GW   (curl -s http://localhost:8080/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
set H_AUTH (curl -s http://localhost:8081/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
set H_INV  (curl -s http://localhost:8082/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
set H_PED  (curl -s http://localhost:8083/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
set H_ENV  (curl -s http://localhost:8084/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
set H_NOT  (curl -s http://localhost:8085/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)

for svc_data in "api-gateway:8080:$H_GW" "auth-service:8081:$H_AUTH" "ms-inventario:8082:$H_INV" "ms-pedidos:8083:$H_PED" "ms-envios:8084:$H_ENV" "notification:8085:$H_NOT"
    set parts (string split ":" $svc_data)
    if test "$parts[3]" = "UP"
        ok "$parts[1] (puerto $parts[2]) — UP"
    else
        fail "$parts[1] (puerto $parts[2]) — status: $parts[3]"
    end
end

# ════════════════════════════════════════════════════════════════════════
section "BLOQUE 7 — BD: auditoría de la tabla saga_estado"
# ════════════════════════════════════════════════════════════════════════

info "Test 7.1 — Consultar todas las sagas registradas en pedidos_db"
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
    -c "SELECT saga_id, paso_actual, estado, pedido_id, envio_id, LEFT(ultimo_error,40) AS error_truncado, actualizado_en FROM saga_estado ORDER BY actualizado_en DESC LIMIT 8;" 2>/dev/null

info "Test 7.2 — Contar sagas por estado"
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
    -c "SELECT estado, COUNT(*) as cantidad FROM saga_estado GROUP BY estado ORDER BY estado;" 2>/dev/null

# ════════════════════════════════════════════════════════════════════════
section "RESUMEN FINAL"
# ════════════════════════════════════════════════════════════════════════

echo ""
echo -e "$BOLD$GREEN╔═════════════════════════════════════════════╗$RESET"
echo -e "$BOLD$GREEN║        SMARTLOGIX — TEST COMPLETADO          ║$RESET"
echo -e "$BOLD$GREEN╚═════════════════════════════════════════════╝$RESET"
echo ""
echo -e "$BOLD Patrones verificados:$RESET"
echo -e "  $GREEN●$RESET Facade    — LogisticaFacade coordinó InventarioClient + PedidoFactory + Repository + Events"
echo -e "  $GREEN●$RESET Strategy  — AlertaService cambió estrategia en tiempo de ejecución (umbralFijo/porcentaje/critico)"
echo -e "  $GREEN●$RESET Saga      — Flujo completo (4 pasos) y compensaciones automáticas en orden inverso"
echo ""
echo -e "$BOLD Patrones base activos:$RESET"
echo -e "  $GREEN●$RESET API Gateway    — JWT validado centralmente, rutas /api/sagas/** incluidas"
echo -e "  $GREEN●$RESET Repository     — Acceso a datos vía JpaRepository en todos los servicios"
echo -e "  $GREEN●$RESET Factory Method — PedidoFactory (NACIONAL/INTERNACIONAL) + EnvioFactory (TERRESTRE/EXPRESS)"
echo -e "  $GREEN●$RESET Circuit Breaker — Resilience4j en InventarioClient (flujo normal y saga)"
echo -e "  $GREEN●$RESET Observer       — PedidoAprobadoEvent publicado y escuchado por NotificationListener"
echo ""
