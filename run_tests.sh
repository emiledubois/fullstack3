#!/usr/bin/env bash

# run_tests.sh - Script de pruebas para integración Flow en SmartLogix
# Uso: bash run_tests.sh

EMAIL="ag.mira@duocuc.cl"
PEDIDO_ID=99
MONTO=5000

echo "=========================================="
echo "Iniciando pruebas de integración con Flow"
echo "=========================================="

# 1. Login y obtener token JWT
echo -e "\n[1/8] Obteniendo token JWT..."
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@smartlogix.cl","password":"Password123!"}' | tr -d '"')

if [ -z "$TOKEN" ]; then
    echo "❌ Error: No se pudo obtener token JWT. ¿El api-gateway está corriendo?"
    exit 1
fi
echo "✅ Token obtenido: $TOKEN"

# 2. Crear un pago
echo -e "\n[2/8] Creando pago para pedido $PEDIDO_ID..."
RESPONSE=$(curl -s -X POST http://localhost:8086/pagos/crear \
  -H "Content-Type: application/json" \
  -d "{\"pedidoId\":$PEDIDO_ID,\"monto\":$MONTO,\"email\":\"$EMAIL\",\"descripcion\":\"Prueba automatizada\"}")

echo "$RESPONSE" | python3 -m json.tool

# Extraer urlPago y flowToken
URL_PAGO=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('urlPago', ''))")
FLOW_TOKEN=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('flowToken', ''))")

if [ -z "$URL_PAGO" ]; then
    echo "❌ Error: No se recibió urlPago. Revisa logs de ms-pagos."
    docker compose logs ms-pagos --tail=20
    exit 1
fi

echo "✅ Pago creado. URL para pagar: $URL_PAGO"
echo "✅ Flow Token: $FLOW_TOKEN"

# 3. Instrucción manual para pagar en el navegador
echo -e "\n[3/8] ⚠️  PASO MANUAL: Abre la siguiente URL en tu navegador y paga con tarjeta exitosa:"
echo "   URL: $URL_PAGO"
echo "   Tarjeta: 4051885600446623, RUT: 11.111.111-1, Clave: 123, Cuotas: 1"
echo "   Esperando a que completes el pago... (presiona Enter cuando hayas pagado)"
read -p "Presiona Enter para continuar..."

# 4. Verificar webhook en logs
echo -e "\n[4/8] Verificando logs de ms-pagos (últimos 15 líneas)..."
docker compose logs ms-pagos --tail=15

# 5. Verificar estado del pedido (debe ser PAGADO o CONFIRMADO)
echo -e "\n[5/8] Consultando estado del pedido $PEDIDO_ID..."
ORDER_STATUS=$(curl -s http://localhost:8080/api/pedidos/$PEDIDO_ID -H "Authorization: Bearer $TOKEN" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', ''))")

if [ "$ORDER_STATUS" = "PAGADO" ] || [ "$ORDER_STATUS" = "CONFIRMADO" ]; then
    echo "✅ Pedido actualizado a: $ORDER_STATUS"
else
    echo "⚠️  Estado actual del pedido: $ORDER_STATUS (se esperaba PAGADO o CONFIRMADO)"
fi

# 6. Verificar Saga en ms-pedidos
echo -e "\n[6/8] Buscando logs de Saga en ms-pedidos..."
docker compose logs ms-pedidos --tail=30 | grep -i saga

# 7. Test de pago rechazado (opcional, crea otro pedido)
echo -e "\n[7/8] Probando pago rechazado (pedido 98)..."
RESP_RECHAZO=$(curl -s -X POST http://localhost:8086/pagos/crear \
  -H "Content-Type: application/json" \
  -d "{\"pedidoId\":98,\"monto\":$MONTO,\"email\":\"$EMAIL\",\"descripcion\":\"Prueba rechazo\"}")

URL_RECHAZO=$(echo "$RESP_RECHAZO" | python3 -c "import sys, json; print(json.load(sys.stdin).get('urlPago', ''))")
echo "URL para pago rechazado: $URL_RECHAZO"
echo "   (Pagar con tarjeta: 4051885600446631)"
read -p "Presiona Enter después de pagar con la tarjeta de rechazo..."

echo -e "\n[8/8] Consultando estado del pedido 98 (debe ser PAGO_RECHAZADO)..."
curl -s http://localhost:8080/api/pedidos/98 -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 9. Verificar datos en bases de datos
echo -e "\n[9/8] Consultando tabla de pagos en postgres-pagos..."
docker compose exec postgres-pagos psql -U postgres pagos_db -c "SELECT id, pedido_id, estado, monto FROM pagos ORDER BY id DESC LIMIT 5;"

echo -e "\n=========================================="
echo "Pruebas completadas"
echo "Revisa manualmente que la Saga se haya ejecutado correctamente en ms-pedidos."
echo "=========================================="
