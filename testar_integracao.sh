#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./compilar.sh
mkdir -p evidencias
C=br.ufc.hotelaria
PIDS=()
cleanup() { for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done; wait 2>/dev/null || true; }
trap cleanup EXIT

# Exs. 1 e 2: streams com destino/origem padrão, arquivos e socket TCP.
java -cp out "$C.streams.StreamsDemo" escrever-arquivo evidencias/reservas.bin > evidencias/streams_arquivo.log
java -cp out "$C.streams.StreamsDemo" ler-arquivo evidencias/reservas.bin >> evidencias/streams_arquivo.log
java -cp out "$C.streams.StreamsDemo" escrever-console > evidencias/console.bin 2> evidencias/streams_console.log
java -cp out "$C.streams.StreamsDemo" ler-console < evidencias/console.bin >> evidencias/streams_console.log
java -cp out "$C.streams.StreamsDemo" servidor-tcp 15001 > evidencias/streams_servidor.log 2>&1 &
PIDS+=("$!"); PID_STREAMS=$!
sleep 1
java -cp out "$C.streams.StreamsDemo" cliente-tcp 127.0.0.1 15001 > evidencias/streams_cliente.log
wait "$PID_STREAMS"
grep -q 'Reserva{id=102' evidencias/streams_arquivo.log
grep -q 'Reserva{id=103' evidencias/streams_console.log
grep -q 'Reserva{id=102' evidencias/streams_servidor.log
printf 'OK: Streams em System.out / System.in, arquivo e TCP.\n'

# Ex. 3: cliente-servidor real com reserva, confirmação, recusa, cancelamento e disponibilidade.
java -cp out "$C.rpc.RpcServidor" 15002 > evidencias/rpc_servidor.log 2>&1 &
PIDS+=("$!")
sleep 1
{
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 LISTAR
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 REGISTRAR 601 1 71 "Ana Lima" 2027-07-10 2027-07-13 4
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 DISPONIBILIDADE 1 2027-07-11 2027-07-12
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 REGISTRAR 602 1 72 "Joao Silva" 2027-07-11 2027-07-12 2
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 EFETIVAR 601
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 CANCELAR 601
  java -cp out "$C.rpc.RpcCliente" 127.0.0.1 15002 DISPONIBILIDADE 1 2027-07-11 2027-07-12
} > evidencias/rpc_cliente.log
grep -q 'estrelas=4' evidencias/rpc_cliente.log
grep -q 'status=PENDENTE' evidencias/rpc_cliente.log
grep -Eq 'Unidades dispon.*: 1' evidencias/rpc_cliente.log
grep -q 'Unidades indispon' evidencias/rpc_cliente.log
grep -q 'status=EFETIVADA' evidencias/rpc_cliente.log
grep -q 'status=CANCELADA' evidencias/rpc_cliente.log
grep -Eq 'Unidades dispon.*: 5' evidencias/rpc_cliente.log
printf 'OK: RPC TCP (3 subclasses, interfaces de reserva e validação de capacidade).\n'

# Ex. 4: autenticação TCP e um datagrama UDP recebido em dois processos clientes.
( sleep 4; printf 'ALERTA|Ultima suite disponivel\n'; sleep 4; printf 'sair\n' ) |
  java -cp out "$C.multicast.MulticastServidor" > evidencias/multicast_servidor.log 2>&1 &
PIDS+=("$!")
sleep 1
( sleep 6; printf 'sair\n' ) | java -cp out "$C.multicast.MulticastCliente" localhost aluno 1234 \
  > evidencias/multicast_cliente1.log 2>&1 &
PIDS+=("$!"); PID_MULTI1=$!
( sleep 6; printf 'sair\n' ) | java -cp out "$C.multicast.MulticastCliente" localhost aluno 1234 \
  > evidencias/multicast_cliente2.log 2>&1 &
PIDS+=("$!"); PID_MULTI2=$!
wait "$PID_MULTI1"
wait "$PID_MULTI2"
grep -q 'Ultima suite disponivel' evidencias/multicast_cliente1.log
grep -q 'Ultima suite disponivel' evidencias/multicast_cliente2.log
grep -q 'leaveGroup' evidencias/multicast_cliente1.log
java -cp out "$C.multicast.MulticastCliente" localhost aluno senha_incorreta \
  > evidencias/multicast_login_negado.log 2>&1 || true
grep -q 'incorretos' evidencias/multicast_login_negado.log
printf 'OK: Multicast UDP (2 clientes, login TCP, leaveGroup e negativa de login).\n'

# Questão extra 6, módulo separado: XML TCP, administração, UDP e encerramento do prazo.
java -cp out "$C.extra.VotacaoServidor" 6 > evidencias/extra_servidor.log 2>&1 &
PIDS+=("$!")
sleep 1
( printf 'votar 1\nvotar 1\n'; sleep 4; printf 'sair\n' ) |
  java -cp out "$C.extra.VotacaoCliente" localhost eleitor1 1234 > evidencias/extra_eleitor1.log 2>&1 &
PIDS+=("$!"); PID_E1=$!
( printf 'votar 2\n'; sleep 4; printf 'sair\n' ) |
  java -cp out "$C.extra.VotacaoCliente" localhost eleitor2 1234 > evidencias/extra_eleitor2.log 2>&1 &
PIDS+=("$!"); PID_E2=$!
( sleep 2; printf 'adicionar Opcao C\nremover 3\nnota Aviso do administrador\nsair\n' ) |
  java -cp out "$C.extra.VotacaoCliente" localhost admin admin123 > evidencias/extra_admin.log 2>&1 &
PIDS+=("$!"); PID_ADMIN=$!
wait "$PID_E1"; wait "$PID_E2"; wait "$PID_ADMIN"
sleep 2
printf 'resultado\nsair\n' | java -cp out "$C.extra.VotacaoCliente" localhost admin admin123 \
  > evidencias/extra_resultado.log 2>&1
grep -q 'Voto registrado' evidencias/extra_eleitor1.log
grep -q 'Aviso do administrador' evidencias/extra_eleitor1.log
grep -q 'Aviso do administrador' evidencias/extra_eleitor2.log
grep -q 'total="2"' evidencias/extra_resultado.log
printf 'OK: módulo extra de votação.\n'

java -cp out "$C.testes.Testes"
printf '\nTodos os testes de integração passaram. Evidências em evidencias/.\n'
