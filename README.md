# Trabalho 1 — Comunicação entre processos: Sistema de Hotelaria

**Disciplina:** Sistemas Distribuídos. **Linguagem:** Java 17 ou superior, somente bibliotecas da JDK. **Tema do serviço remoto:** sistema de hotelaria com reservas em Hotel, Motel e Pousada vinculados a um Complexo Turístico.

O projeto implementa os **quatro exercícios obrigatórios** do enunciado e mantém a **questão extra de votação em um módulo separado** (`extra/`). O sistema principal é inteiramente de hotelaria. Dados são armazenados em memória enquanto o servidor executa. Não há dependência de nuvem, IDE, banco de dados ou bibliotecas de terceiros.

> Projeto de demonstração acadêmica: usuários de teste e senhas fixas, TCP sem TLS, UDP multicast sem controle criptográfico de membros, ausência de banco de dados e de gestão comercial real. As tarifas são exemplos. Não use como sistema de produção.

## 1. Modelagem orientada a objetos solicitada

| Relação especificada | Implementação em Java | Responsabilidade |
|---|---|---|
| Aplicação | Sistema de Hotelaria | Serviço remoto de consulta e gerenciamento de reservas. |
| Superclasse: Meios de Hospedagem | `modelo/MeioHospedagem.java` (singular, padrão de nome de classe) | Campos comuns: ID, nome, endereço, valor da diária e capacidade em unidades. |
| Subclasses | `Hotel`, `Motel`, `Pousada` (`extends MeioHospedagem`) | Hotel: estrelas; motel: garagem privativa; pousada: café da manhã incluso. |
| Agregação: Complexo Turístico | `ComplexoTuristico` | Reúne referências a estabelecimentos criados independentemente; desagregar não destrói o objeto. |
| Interface: Reservas | `contrato/Reservas.java` | Contrato: `registrarReserva`, `cancelarReserva`, `efetivarReserva`. |
| Implementação da interface | `servico/ReservaService.java` | Verifica disponibilidade e mantém estados PENDENTE, EFETIVADA e CANCELADA. |
| POJO adicional | `Hospede` e `Reserva` | Identificam a pessoa, as datas, o meio, a quantidade e o estado da reserva. |
| Segunda classe de serviço | `servico/HospedagemService.java` | Consulta e cadastra os estabelecimentos do complexo. |

**POJO:** objeto simples que representa dados da aplicação. `Hotel`, `Motel` e `Pousada` são objetos do domínio derivados da superclasse; `Hospede` e `Reserva` são outros POJOs. `MeioHospedagem` é abstrata porque não existe estabelecimento genérico instanciado na aplicação. O diagrama editável de classes está em `diagrama_classes.puml` (PlantUML).

### Regra funcional de disponibilidade

Uma reserva recebe `id`, `meioHospedagemId`, `Hospede`, data de entrada, data de saída e quantidade de unidades. `registrarReserva` confirma que existem unidades livres durante **todo** o intervalo. Reservas PENDENTES ou EFETIVADAS ocupam capacidade; CANCELADAS não ocupam. Dois intervalos conflitam se `entradaA < saidaB` **e** `saidaA > entradaB` (a data de saída é exclusiva). Por isso, a saída de um hóspede e a entrada do próximo no mesmo dia são compatíveis. `efetivarReserva` muda PENDENTE para EFETIVADA, e `cancelarReserva` libera unidades. O serviço usa `synchronized` para que dois clientes TCP concorrentes não reservem a mesma unidade duas vezes.

## 2. Mapeamento integral do enunciado

| Exercício | Arquivos / tecnologia | Demonstração |
|---|---|---|
| 1. Saída personalizada | `streams/MeioHospedagemOutputStream.java extends OutputStream` | Construtor recebe `OutputStream destino`, `Reserva[] objetos`, `int quantidade`; envia array do **outro POJO Reserva** para `System.out`, arquivo e socket TCP. |
| 2. Entrada personalizada | `streams/MeioHospedagemInputStream.java extends InputStream` | Construtor recebe `InputStream origem`; reconstrói `Reserva[]` usando `System.in`, arquivo e cliente TCP. |
| 3. Serialização e request/reply | `rpc/ProtocoloRpc.java`, `RpcCliente.java`, `RpcServidor.java` | Cliente empacota pedido, servidor desempacota e executa, servidor empacota resposta, cliente desempacota; transmite `Hotel`/`Motel`/`Pousada` e `Reserva` em bytes via TCP. |
| 4. Multicast | `multicast/*.java` | Login concorrente TCP 5003, notificações UDP multicast `230.0.0.1:5004`, `joinGroup`/`leaveGroup`, thread de recepção e thread de teclado em cada cliente; servidor com pools de threads. |
| Questão extra | `extra/*.java` | Sistema de votação didático **independente** do domínio de hotelaria, mantido para atender ao exercício opcional. Login/voto TCP, avisos UDP e apuração com prazo. |
| Testes e repositório | `testes/Testes.java`, `testar_integracao.sh`, `evidencias/` | Testes automatizados, demonstrações reais de dois processos e registros de execução. |

## 3. Preparar e compilar

Instale o **JDK 17+** e confirme `java -version` e `javac -version`. Extraia o ZIP e abra um terminal na pasta `trabalho_hotelaria_comunicacao`.

Linux / macOS:

```bash
./compilar.sh
java -cp out br.ufc.hotelaria.testes.Testes
```

Windows — Prompt de Comando (cmd):

```bat
compilar.bat
java -cp out br.ufc.hotelaria.testes.Testes
```

No PowerShell, execute `./compilar.bat`. Se o Windows exibir `?` no lugar de acentos, use `chcp 65001` no `cmd`. Os testes de integração completos podem ser executados em Linux/macOS ou Git Bash (`./testar_integracao.sh`).

## 4. Exercícios 1 e 2 — Streams, arquivos e TCP

A classe `MeioHospedagemOutputStream` se chama assim por derivar o nome da superclasse escolhida; os dados efetivamente enviados são um array do POJO diferente `Reserva`. A classe `MeioHospedagemInputStream` faz a leitura compatível.

Formato binário do lote:

```text
int  MAGIC = 0x48525331  // "HRS1"
int  quantidadeDeReservas
para cada Reserva:
    int  reservaId
    int  meioHospedagemId
    int  hospedeId
    UTF  nomeHospede
    long entradaEpochDay
    long saidaEpochDay
    int  unidades
    UTF  status
```

O `DataOutputStream` e o `DataInputStream` escrevem/leem a mesma sequência e tipos. Os métodos de envio não fecham automaticamente a saída padrão ou o socket do chamador; o código que abriu o recurso controla seu fechamento.

**Teste com arquivo (`FileOutputStream` → `FileInputStream`):**

```bash
java -cp out br.ufc.hotelaria.streams.StreamsDemo escrever-arquivo reservas.bin
java -cp out br.ufc.hotelaria.streams.StreamsDemo ler-arquivo reservas.bin
```

**Teste com saída e entrada padrão (`System.out` → `System.in`):**

```bash
java -cp out br.ufc.hotelaria.streams.StreamsDemo escrever-console > console.bin
java -cp out br.ufc.hotelaria.streams.StreamsDemo ler-console < console.bin
```

O conteúdo de `console.bin` é **binário** e não deve ser aberto como um texto. O primeiro programa escreve avisos em `System.err` para não misturar texto aos bytes. Os redirecionamentos `<` e `>` mostrados acima funcionam no `cmd` e em shells Unix; para PowerShell, prefira o `cmd`.

**Teste remoto com TCP (abrir dois terminais):**

Terminal 1:

```bash
java -cp out br.ufc.hotelaria.streams.StreamsDemo servidor-tcp 5001
```

Terminal 2:

```bash
java -cp out br.ufc.hotelaria.streams.StreamsDemo cliente-tcp 127.0.0.1 5001
```

O servidor imprime as três reservas reconstruídas e encerra depois de atender a conexão de demonstração.

## 5. Exercício 3 — Serviço remoto de reservas via TCP

**Terminal 1 (servidor):**

```bash
java -cp out br.ufc.hotelaria.rpc.RpcServidor 5002
```

**Terminal 2 (cliente, um comando de cada vez):**

```bash
# Ver os três tipos concretos do Complexo Turístico.
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 LISTAR

# Ver Hotel de ID 1 e suas características.
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 CONSULTAR 1

# Conferir vagas para um período.
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 DISPONIBILIDADE 1 2027-10-10 2027-10-13

# Registrar 2 quartos do Hotel 1 para a hóspede Ana, por 3 diárias.
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 REGISTRAR 801 1 51 "Ana Lima" 2027-10-10 2027-10-13 2

# Efetivar a reserva e, em seguida, consultar ou cancelar.
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 EFETIVAR 801
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 CONSULTAR_RESERVA 801
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 CANCELAR 801
java -cp out br.ufc.hotelaria.rpc.RpcCliente localhost 5002 RESERVAS
```

**Como demonstrar erro de capacidade:** reinicie o servidor; registre cinco unidades do Hotel 1 no mesmo período e tente registrar mais uma em outra reserva. A segunda solicitação retorna `Sucesso: false` com a mensagem de unidades indisponíveis. Cancelar a primeira devolve a disponibilidade.

**Serialização:** a requisição usa `HRQ1`, nome da operação, IDs, nome da pessoa, datas em `epochDay` e quantidade. A resposta usa `HRP1`, sucesso/mensagem, disponibilidade, preço e listas de meios e reservas. Para `MeioHospedagem`, o protocolo envia um discriminador `HOTEL`, `MOTEL` ou `POUSADA`, depois os atributos comuns e o atributo específico da subclasse. Assim o cliente reconstrói o **subtipo correto**; para `Reserva`, reaproveita o `ReservaCodec`. O servidor atende múltiplos sockets TCP por um pool de oito threads.

## 6. Exercício 4 — Autenticação TCP e multicast UDP

Abra quatro terminais. No **servidor**:

```bash
java -cp out br.ufc.hotelaria.multicast.MulticastServidor
```

Nos **clientes 1 e 2**, execute o mesmo comando em terminais diferentes:

```bash
java -cp out br.ufc.hotelaria.multicast.MulticastCliente localhost aluno 1234
```

Após os dois logins, digite no terminal do servidor:

```text
ALERTA|Última suíte disponível na pousada
NOTIFICACAO|Nova reserva registrada no complexo
ATUALIZACAO|Horários de entrada atualizados
```

Os dois clientes exibem o mesmo JSON em tempo real, como `{"tipo":"ALERTA","mensagem":"Última suíte disponível na pousada","timestamp":...}`. O servidor também envia uma atualização automática periódica: ela é produzida em thread diferente das mensagens digitadas. Em cada cliente, digite `sair`: a thread de teclado informa à thread UDP para encerrar e o cliente chama `leaveGroup`. Digite `sair` no servidor para encerrá-lo.

Portas e grupo: **5003/TCP** para login; **230.0.0.1:5004/UDP** para avisos. O endereço `230.0.0.1` pertence à faixa IPv4 multicast (classe D). TTL 1 limita o envio à rede local. Se estiver em VPN ou rede que bloqueia multicast, execute os processos na mesma máquina. Login é validação demonstrativa na aplicação cliente: por si só, UDP multicast não restringe um programa externo que conheça o grupo.

## 7. Questão extra — módulo isolado de votação

O PDF pede uma **questão extra 6** que não pertence ao serviço de hotelaria. Para cobrir também essa parte, o código existente em `extra/` implementa um sistema de votação de demonstração com contas fictícias, TCP/ XML para login, lista de opções, envio de votos e administração, e UDP multicast exclusivamente para avisos do administrador. Resultados só são liberados após o prazo configurado; votos duplicados são recusados. Este módulo é independente: não compartilha reservas, hospedagens ou portas com a aplicação principal.

Servidor (prazo de 40 segundos):

```bash
java -cp out br.ufc.hotelaria.extra.VotacaoServidor 40
```

Três clientes, cada um em seu terminal:

```bash
java -cp out br.ufc.hotelaria.extra.VotacaoCliente localhost eleitor1 1234
java -cp out br.ufc.hotelaria.extra.VotacaoCliente localhost eleitor2 1234
java -cp out br.ufc.hotelaria.extra.VotacaoCliente localhost admin admin123
```

Eleitores: `listar`, `votar 1`, `resultado`. Administrador: `adicionar Opcao C`, `remover 3`, `nota A votação encerra em breve`. Digite `sair` nos clientes; encerre o servidor com Ctrl+C. O servidor usa **5010/TCP** e **230.0.0.2:5011/UDP**. A figura da página 3 do enunciado ilustra servidor, armazenamento, eleitores e administrador; aqui os dados ficam em memória, pois o texto não exige um SGBD.

## 8. Testes e evidências

```bash
./testar_integracao.sh
```

O script compila e testa os streams nos três tipos de destino/origem, RPC com reserva/efetivação/cancelamento/erro de capacidade, dois clientes multicast e login recusado, além da questão extra. Gera arquivos em `evidencias/` (logs e dois arquivos binários). `java -cp out br.ufc.hotelaria.testes.Testes` executa **34 verificações** de regras OO, agregação, concorrência, serialização, polimorfismo e XML. Os testes foram executados neste ambiente e passaram; condições de rede do laboratório podem alterar a disponibilidade de multicast em outras máquinas.

### Limitações e possíveis evoluções

Sem persistência: reiniciar os processos restaura os dados de exemplo. As reservas trabalham com dias completos e quantidade agregada de unidades (não com números de quartos específicos, pagamentos ou documentos). Para operação real seriam necessários banco de dados transacional, autenticação/autorização robusta, TLS, observabilidade e política consistente de reservas e cancelamentos.

## 9. Organização do repositório

```text
trabalho_hotelaria_comunicacao/
├── src/br/ufc/hotelaria/
│   ├── modelo/      MeioHospedagem, Hotel, Motel, Pousada, ComplexoTuristico, Hospede, Reserva
│   ├── contrato/     Reservas
│   ├── servico/      HospedagemService, ReservaService
│   ├── streams/      MeioHospedagemOutputStream, MeioHospedagemInputStream, ReservaCodec, StreamsDemo
│   ├── rpc/          ProtocoloRpc, RpcServidor, RpcCliente
│   ├── multicast/    MulticastConfig, MulticastServidor, MulticastCliente
│   ├── extra/        Questão extra independente
│   └── testes/       Testes
├── diagrama_classes.puml
├── ROTEIRO_APRESENTACAO.md
├── README.md
├── compilar.sh / compilar.bat
├── testar_integracao.sh
└── evidencias/
```
