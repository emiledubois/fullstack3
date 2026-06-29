#!/usr/bin/env fish
# ============================================================
# run_tests.fish — Pruebas de integración SmartLogix + Flow
# Uso: fish run_tests.fish
# ============================================================

set BASE_URL  "http://localhost:8080"
set EMAIL     "admin@smartlogix.cl"
set PASSWORD  "Password123!"
set TEST_EMAIL "ag.mira@duocuc.cl"
set MONTO     5990

# ── Helpers ─────────────────────────────────────────────────
function titulo
    echo ""
    echo "══════════════════════════════════════════"
    echo "  $argv"
    echo "══════════════════════════════════════════"
end

function ok;    echo "  ✅ $argv"; end
function fail;  echo "  ❌ $argv"; end
function info;  echo "  ℹ️  $argv"; end
function warn;  echo "  ⚠️  $argv"; end

function json_get
    echo $argv[1] | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$argv[2]',''))" 2>/dev/null
end

# ── Inicio ──────────────────────────────────────────────────
titulo "SmartLogix — Test integración Flow Chile"
echo "  $(date)"

# ────────────────────────────────────────────────────────────
# PASO 1 — Verificar que los servicios están arriba
# ────────────────────────────────────────────────────────────
titulo "PASO 1/8 — Health checks"

for svc in "api-gateway:8080" "ms-pagos:8086" "ms-pedidos:8083"
    set nombre (string split ":" $svc)[1]
    set puerto (string split ":" $svc)[2]
    set resp (curl -s --max-time 5 http://localhost:$puerto/actuator/health 2>/dev/null)
    if string match -q '*"UP"*' $resp
        ok "$nombre está UP"
    else
        fail "$nombre no responde. Ejecutar: docker compose up -d"
        exit 1
    end
end

# ────────────────────────────────────────────────────────────
# PASO 2 — Obtener JWT
# ────────────────────────────────────────────────────────────
titulo "PASO 2/8 — Login JWT"

set TOKEN (curl -s --max-time 10 -X POST $BASE_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | tr -d '"')

if test -z "$TOKEN"; or string match -q '*error*' $TOKEN
    fail "No se pudo obtener token. ¿Las credenciales son correctas?"
    exit 1
end
ok "Token JWT obtenido ($(string length $TOKEN) caracteres)"

# ────────────────────────────────────────────────────────────
# PASO 3 — Crear producto de prueba si no existe
# ────────────────────────────────────────────────────────────
titulo "PASO 3/8 — Verificar inventario"

set INV (curl -s --max-time 10 $BASE_URL/api/inventario \
  -H "Authorization: Bearer $TOKEN")
set TOTAL_PROD (echo $INV | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)

if test -z "$TOTAL_PROD"; or test "$TOTAL_PROD" = "0"
    info "Sin productos — creando uno de prueba..."
    set NUEVO (curl -s --max-time 10 -X POST $BASE_URL/api/inventario \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"sku":"TEST-FLOW","nombre":"Producto Test Flow","stockActual":100,"umbralMinimo":5,"precioUnitario":5990,"bodega":"Bodega Test"}')
    set PROD_ID (json_get $NUEVO "id")
    ok "Producto creado con ID: $PROD_ID"
else
    set PROD_ID (echo $INV | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])" 2>/dev/null)
    ok "$TOTAL_PROD producto(s) en inventario. Usando ID: $PROD_ID"
end

# ────────────────────────────────────────────────────────────
# PASO 4 — Crear pedido con estado PENDIENTE_PAGO
# ────────────────────────────────────────────────────────────
titulo "PASO 4/8 — Crear pedido"

set PEDIDO_RESP (curl -s --max-time 10 -X POST $BASE_URL/api/pedidos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": 1,
    \"userEmail\": \"$TEST_EMAIL\",
    \"clienteNombre\": \"Test Integración Flow\",
    \"total\": $MONTO,
    \"tipoPedido\": \"NACIONAL\",
    \"destino\": \"Santiago\",
    \"productoId\": $PROD_ID,
    \"cantidad\": 1
  }")

set PEDIDO_ID (json_get $PEDIDO_RESP "id")

if test -z "$PEDIDO_ID"; or test "$PEDIDO_ID" = "None"
    fail "No se pudo crear el pedido."
    echo $PEDIDO_RESP | python3 -m json.tool
    exit 1
end
ok "Pedido creado con ID: $PEDIDO_ID"

# ────────────────────────────────────────────────────────────
# PASO 5 — Iniciar pago con Flow
# ────────────────────────────────────────────────────────────
titulo "PASO 5/8 — Crear orden de pago en Flow"

set PAGO_RESP (curl -s --max-time 30 -X POST $BASE_URL/api/pagos/crear \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"pedidoId\": $PEDIDO_ID,
    \"monto\": $MONTO,
    \"email\": \"$TEST_EMAIL\",
    \"descripcion\": \"Pedido SmartLogix #$PEDIDO_ID\"
  }")

set URL_PAGO   (json_get $PAGO_RESP "urlPago")
set FLOW_TOKEN (json_get $PAGO_RESP "flowToken")
set FLOW_ORDER (json_get $PAGO_RESP "flowOrder")
set PAGO_ID    (json_get $PAGO_RESP "pagoId")

if test -z "$URL_PAGO"; or test "$URL_PAGO" = "None"
    fail "No se recibió urlPago. Revisa que el contenedor tenga acceso a internet:"
    info "docker compose exec ms-pagos curl -s --max-time 5 https://sandbox.flow.cl/api"
    echo ""
    echo $PAGO_RESP | python3 -m json.tool 2>/dev/null; or echo $PAGO_RESP
    docker compose logs ms-pagos --tail=10
    exit 1
end

ok "Pago iniciado:"
info "  Pago ID:     $PAGO_ID"
info "  Flow Token:  $FLOW_TOKEN"
info "  Flow Order:  $FLOW_ORDER"
echo ""
echo "  ┌─────────────────────────────────────────────────┐"
echo "  │  URL DE PAGO (abrir en el navegador):           │"
echo "  │  $URL_PAGO"
echo "  └─────────────────────────────────────────────────┘"

# ────────────────────────────────────────────────────────────
# PASO 6 — Pago manual con tarjeta exitosa
# ────────────────────────────────────────────────────────────
titulo "PASO 6/8 — Pagar en Flow (acción manual)"
echo ""
echo "  Abre la URL anterior en tu navegador y paga con:"
echo ""
echo "  Tarjeta EXITOSA:  4051885600446623"
echo "  RUT:              11.111.111-1"
echo "  Clave:            123"
echo "  Cuotas:           1"
echo ""
echo "  Después de pagar, Flow te redirigirá a:"
echo "  http://localhost:5173/pago-resultado"
echo ""
read -P "  [Presiona Enter cuando hayas completado el pago] "

# ────────────────────────────────────────────────────────────
# PASO 7 — Verificar que el webhook actualizó el pedido
# ────────────────────────────────────────────────────────────
titulo "PASO 7/8 — Verificar resultado"

info "Esperando 3 segundos para que el webhook procese..."
sleep 3

# Estado del pago
set PAGO_ESTADO_RESP (curl -s --max-time 10 $BASE_URL/api/pagos/$PAGO_ID \
  -H "Authorization: Bearer $TOKEN")
set PAGO_ESTADO (json_get $PAGO_ESTADO_RESP "estado")
info "Estado del pago en pagos_db: $PAGO_ESTADO"

# Estado del pedido
set PEDIDO_STATUS_RESP (curl -s --max-time 10 $BASE_URL/api/pedidos/$PEDIDO_ID \
  -H "Authorization: Bearer $TOKEN")
set PEDIDO_STATUS (json_get $PEDIDO_STATUS_RESP "status")
info "Estado del pedido en pedidos_db: $PEDIDO_STATUS"

if test "$PAGO_ESTADO" = "PAGADO"
    ok "Pago confirmado por Flow correctamente"
else
    warn "Estado de pago inesperado: $PAGO_ESTADO"
    info "Revisa los logs: docker compose logs ms-pagos --tail=20"
end

if test "$PEDIDO_STATUS" = "PAGADO"; or test "$PEDIDO_STATUS" = "CONFIRMADO"
    ok "Pedido actualizado a: $PEDIDO_STATUS"
else
    warn "Estado de pedido inesperado: $PEDIDO_STATUS"
    info "Revisa los logs: docker compose logs ms-pedidos --tail=20"
end

# Buscar saga ejecutada
info "Logs de Saga en ms-pedidos:"
docker compose logs ms-pedidos --tail=20 | grep -i "saga\|pago" 2>/dev/null; or info "  (sin logs de saga aún)"

# ────────────────────────────────────────────────────────────
# PASO 8 — Test de pago RECHAZADO
# ────────────────────────────────────────────────────────────
titulo "PASO 8/8 — Test pago rechazado (opcional)"
read -P "  ¿Quieres probar el flujo de rechazo? [s/N] " CONFIRMAR

if test "$CONFIRMAR" = "s"; or test "$CONFIRMAR" = "S"

    set PEDIDO_R (curl -s --max-time 10 -X POST $BASE_URL/api/pedidos \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"userId\": 1,
        \"userEmail\": \"$TEST_EMAIL\",
        \"clienteNombre\": \"Test Rechazo\",
        \"total\": $MONTO,
        \"tipoPedido\": \"NACIONAL\",
        \"destino\": \"Valparaíso\",
        \"productoId\": $PROD_ID,
        \"cantidad\": 1
      }")
    set PEDIDO_R_ID (json_get $PEDIDO_R "id")

    set PAGO_R (curl -s --max-time 30 -X POST $BASE_URL/api/pagos/crear \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"pedidoId\": $PEDIDO_R_ID,
        \"monto\": $MONTO,
        \"email\": \"$TEST_EMAIL\",
        \"descripcion\": \"Test rechazo #$PEDIDO_R_ID\"
      }")
    set URL_R    (json_get $PAGO_R "urlPago")
    set PAGO_R_ID (json_get $PAGO_R "pagoId")

    echo ""
    echo "  ┌─────────────────────────────────────────────────┐"
    echo "  │  URL DE PAGO RECHAZADO:                         │"
    echo "  │  $URL_R"
    echo "  └─────────────────────────────────────────────────┘"
    echo ""
    echo "  Paga con tarjeta de RECHAZO: 4051885600446631"
    echo "  RUT: 11.111.111-1  Clave: 123"
    echo ""
    read -P "  [Presiona Enter después de pagar con la tarjeta de rechazo] "

    sleep 3

    set R_PAGO (curl -s --max-time 10 $BASE_URL/api/pagos/$PAGO_R_ID \
      -H "Authorization: Bearer $TOKEN")
    set R_ESTADO (json_get $R_PAGO "estado")

    if test "$R_ESTADO" = "RECHAZADO"
        ok "Pago rechazado manejado correctamente: $R_ESTADO"
    else
        warn "Estado inesperado para rechazo: $R_ESTADO"
    end
else
    info "Test de rechazo omitido."
end

# ────────────────────────────────────────────────────────────
# RESUMEN FINAL en BD
# ────────────────────────────────────────────────────────────
titulo "RESUMEN — Auditoría en base de datos"

info "Últimos pagos en pagos_db:"
docker compose exec postgres-pagos psql -U postgres pagos_db \
  -c "SELECT id, pedido_id, estado, monto, created_at FROM pagos ORDER BY id DESC LIMIT 5;" \
  2>/dev/null; or docker compose exec postgres-pagos psql -U postgres pagos_db \
  -c "SELECT id, pedido_id, estado, monto FROM pagos ORDER BY id DESC LIMIT 5;" 2>/dev/null

echo ""
info "Últimas Sagas en pedidos_db:"
docker compose exec postgres-pedidos psql -U postgres pedidos_db \
  -c "SELECT saga_id, estado, pedido_id, envio_id FROM saga_estado ORDER BY actualizado_en DESC LIMIT 5;" 2>/dev/null

echo ""
titulo "Pruebas completadas"
ok "Revisa los resultados arriba para confirmar el flujo completo."
