#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
bash compilar.sh
TMP="$(mktemp -d)"
PIDS=()
cleanup() { for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done; wait 2>/dev/null || true; rm -rf "$TMP"; }
trap cleanup EXIT
C=br.ufc.hotelaria
FILA_PORT=15550
RPC_PORT=15551
broker() {
  java -cp out "$C.fila.FilaServidor" "$FILA_PORT" "$TMP/broker" 1200 > "$TMP/broker.log" 2>&1 &
  BROKER_PID=$!; PIDS+=("$BROKER_PID")
  for i in {1..40}; do
    if java -cp out "$C.fila.FilaPublicador" localhost "$FILA_PORT" ESTADO >/dev/null 2>&1; then return; fi
    sleep .2
  done
  cat "$TMP/broker.log"; exit 1
}
estado() { java -cp out "$C.fila.FilaPublicador" localhost "$FILA_PORT" ESTADO; }
pendentes() {
  for i in {1..50}; do if estado | grep -q "PENDENTES=$1 "; then return; fi; sleep .2; done
  echo "Esperado $1, recebido $(estado)"; exit 1
}
broker
java -cp out "$C.fila.FilaPublicador" localhost "$FILA_PORT" EVENTO_OFFLINE >/dev/null
pendentes 1
kill "$BROKER_PID"; wait "$BROKER_PID" 2>/dev/null || true
broker
java -cp out "$C.fila.FilaConsumidor" localhost "$FILA_PORT" --uma > "$TMP/offline.log"
grep -q EVENTO_OFFLINE "$TMP/offline.log"
grep -q CONFIRMADO "$TMP/offline.log"
pendentes 0
echo 'OK: consumidor offline, broker reiniciado e evento recuperado.'
java -cp out "$C.fila.FilaPublicador" localhost "$FILA_PORT" EVENTO_REENTREGA >/dev/null
java -cp out "$C.fila.FilaConsumidor" localhost "$FILA_PORT" --sem-ack > "$TMP/sem_ack.log"
grep -q SIMULACAO_FALHA "$TMP/sem_ack.log"
sleep 1.5
java -cp out "$C.fila.FilaConsumidor" localhost "$FILA_PORT" --uma > "$TMP/reentrega.log"
grep -q EVENTO_REENTREGA "$TMP/reentrega.log"
grep -q CONFIRMADO "$TMP/reentrega.log"
echo 'OK: reentrega após falha antes de ACK.'
HOTELARIA_FILA_HOST=localhost HOTELARIA_FILA_PORT="$FILA_PORT" HOTELARIA_OUTBOX_DIR="$TMP/outbox" java -cp out "$C.rpc.RpcServidor" "$RPC_PORT" > "$TMP/rpc.log" 2>&1 &
RPC_PID=$!; PIDS+=("$RPC_PID")
for i in {1..40}; do if java -cp out "$C.rpc.RpcCliente" localhost "$RPC_PORT" LISTAR >/dev/null 2>&1; then break; fi; sleep .2; done
java -cp out "$C.rpc.RpcCliente" localhost "$RPC_PORT" REGISTRAR 901 1 77 "Teste Trabalho4" 2027-07-10 2027-07-12 1 > "$TMP/reserva.log"
grep -q 'Reserva registrada' "$TMP/reserva.log"
pendentes 1
java -cp out "$C.fila.FilaConsumidor" localhost "$FILA_PORT" --uma > "$TMP/integracao.log"
grep -q 'RESERVA_CRIADA|reserva=901' "$TMP/integracao.log"
pendentes 0
echo 'OK: integração RPC -> outbox -> broker -> consumidor.'
kill "$BROKER_PID"; wait "$BROKER_PID" 2>/dev/null || true
java -cp out "$C.rpc.RpcCliente" localhost "$RPC_PORT" EFETIVAR 901 > "$TMP/efetivada.log"
grep -q 'Reserva efetivada' "$TMP/efetivada.log"
for i in {1..30}; do if find "$TMP/outbox" -name '*.evt' | grep -q .; then break; fi; sleep .2; done
find "$TMP/outbox" -name '*.evt' | grep -q .
broker
pendentes 1
java -cp out "$C.fila.FilaConsumidor" localhost "$FILA_PORT" --uma > "$TMP/recuperacao.log"
grep -q 'RESERVA_EFETIVADA|reserva=901' "$TMP/recuperacao.log"
pendentes 0
echo 'OK: broker offline; outbox recuperada.'
echo 'SUCESSO: Trabalho 4.'
