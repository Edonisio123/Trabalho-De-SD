# Relatório Técnico — Trabalho 4: Comunicação Indireta

**Universidade Federal do Ceará — Campus Quixadá**  
**Disciplina:** Sistemas Distribuídos (QXD0043)  
**Projeto:** Sistema de Hotelaria  
**Escolha:** Opção C — Filas de Mensagens (Message Queues)

## 1. Justificativa
O projeto anterior usa RPC sobre TCP para gerenciar reservas e multicast UDP para avisos independentes. A inclusão de uma fila de mensagens permite que mudanças nas reservas sejam encaminhadas a serviços de notificação, auditoria ou relatórios mesmo quando esses consumidores estiverem desligados. A comunicação RPC original continua para comandos e respostas síncronos, e a distribuição de eventos passa a ser assíncrona e indireta.

## 2. Arquitetura
Fluxo: **Cliente RPC → Servidor de Reservas → Outbox local → Broker TCP → Fila em disco → Consumidor → ACK**.

O servidor publica eventos RESERVA_CRIADA, RESERVA_EFETIVADA e RESERVA_CANCELADA depois das respectivas operações. O publicador grava cada evento na outbox local, reenvia enquanto não houver confirmação do broker e remove o arquivo após receber a confirmação. O broker mantém uma mensagem por arquivo no diretório configurado e carrega mensagens pendentes ao reiniciar. O consumidor, processo separado, consulta a fila e confirma após tratar o evento. Se não confirmar, o prazo de lease expira e o item pode ser reentregue.

## 3. Propriedades e falhas
**Desacoplamento espacial:** o produtor não conhece IP ou porta dos consumidores, apenas do intermediário.  
**Desacoplamento temporal:** eventos podem ser publicados sem consumidores ativos, permanecendo no disco para consumo posterior. Se o próprio broker estiver offline, a outbox guarda os eventos do servidor RPC até a recuperação.  
**Recuperação:** mensagens pendentes são carregadas após reiniciar o broker com o mesmo diretório. Falha antes do ACK resulta em reentrega após expirar o lease.

A semântica é **pelo menos uma vez**. Uma falha entre a gravação no broker e a confirmação ao produtor pode causar duplicações no reenvio. Consumidores reais devem implementar idempotência. O sistema não garante exatamente uma vez, não possui replicação, fsync explícito, nem transação atômica entre reservas em memória e outbox. Essas limitações importam para implantações de produção.

## 4. Sobrecarga e mitigação
Em comparação à comunicação direta, a fila acrescenta uma conexão ao broker, serialização, escrita e leitura de arquivos, polling, gestão de leases e confirmações. Essa sobrecarga aumenta a latência e exige monitorar filas e espaço em disco. Em compensação, os produtores não dependem da disponibilidade dos consumidores. Possíveis otimizações incluem payloads pequenos, lotes de mensagens, pooling de conexões, métricas de fila, backoff progressivo e armazenamento transacional.

## 5. Demonstração e avaliação
1. Iniciar broker e servidor de reservas; manter o consumidor offline.
2. Registrar reserva via RPC e observar PENDENTES=1 na fila.
3. Iniciar consumidor, verificar o evento e ACK; PENDENTES=0.
4. Simular consumidor sem confirmação (--sem-ack), aguardar lease e verificar reentrega.
5. Publicar outra mensagem, reiniciar o broker com a mesma pasta e verificar sua persistência.
6. Desligar o broker e efetivar uma reserva. Verificar arquivo na outbox, religar o broker e observar entrega posterior.

Os passos verificam desacoplamento, falha/recuperação e comunicação indireta. O script testar_trabalho4.sh automatiza esse roteiro. Os dados de reservas continuam em memória e o broker é um único nó educacional, não uma solução tolerante à falha completa.
