# Trabalho 1 — Comunicação entre processos (Sistemas Distribuídos)

**Projeto:** serviço remoto de estoque de roupas artesanais, em Java 17+ e API padrão do JDK.

Este repositório implementa os **exercícios obrigatórios 1–4** e, em `extra/`, a **questão extra 6 (votação didática)**. Não são necessárias bibliotecas externas, IDE específica, banco de dados ou acesso à nuvem. A aplicação deve ser compilada antes de executar os comandos abaixo.

> Escopo didático: contas e senhas de exemplo, dados em memória, TCP sem TLS e grupos UDP sem criptografia. Não use este código como sistema comercial ou eleitoral real.

## 1. Estrutura e relação com o enunciado

| Exigência | Implementação |
|---|---|
| Serviço remoto escolhido | Controle de estoque e vendas de peças artesanais |
| POJO 1 | `modelo/Produto.java`: ID, nome, preço em centavos e quantidade |
| POJO 2 | `modelo/Movimentacao.java`: ID do produto, tipo ENTRADA/SAIDA, quantidade e timestamp |
| Classe de serviço 1 | `servico/EstoqueService.java`: cadastrar, consultar, listar, registrar entrada, vender, histórico |
| Classe de serviço 2 | `servico/RelatorioService.java`: valor total e produtos com estoque baixo |
| Exercício 1 | `streams/ProdutoOutputStream.java`: `extends OutputStream`; transmite array de **Movimentacao**, com quantidade selecionada |
| Exercício 2 | `streams/ProdutoInputStream.java`: `extends InputStream`; reconstrói o array |
| Testes 1 e 2 | `streams/StreamsDemo.java`: `System.out`, `FileOutputStream`, socket TCP, `System.in`, `FileInputStream` e socket TCP no servidor |
| Exercício 3 | `rpc/ProtocoloRpc.java`, `RpcServidor.java`, `RpcCliente.java`: serialização explícita de pedido, resposta e POJO Produto, cliente-servidor TCP |
| Exercício 4 | `multicast/`: autenticação TCP; `joinGroup`, `leaveGroup`, mensagens JSON UDP multicast e múltiplas threads |
| Questão extra 6 | `extra/`: votação com XML sobre TCP, login, papéis eleitor/admin, prazo, apuração e notas UDP multicast |
| Testes e demonstração | `testes/Testes.java`, `testar_integracao.sh` e logs em `evidencias/` |

### Arquitetura resumida

```text
Ex. 1 e 2
[StreamsDemo cliente] --TCP 5001: lote de Movimentacao--> [StreamsDemo servidor]
        |--System.out / FileOutputStream               |--System.in / FileInputStream

Ex. 3
[RpcCliente] --TCP 5002: request binário--> [RpcServidor] --> [EstoqueService]
             <--TCP 5002: reply binário---        --> [RelatorioService]

Ex. 4
[MulticastCliente 1] --TCP 5003: login-->
[MulticastCliente 2] --TCP 5003: login--> [MulticastServidor]
[MulticastCliente 1] <--UDP 230.0.0.1:5004--  [MulticastServidor]
[MulticastCliente 2] <--UDP 230.0.0.1:5004--  [MulticastServidor]

Extra
[VotacaoCliente eleitor/admin] <--TCP 5010: XML--> [VotacaoServidor]
[VotacaoCliente eleitores]     <--UDP 230.0.0.2:5011: notas do admin--
```

## 2. Compilação

É necessário **JDK 17 ou superior** (`java -version` e `javac -version`). Os exemplos supõem que o terminal está dentro da pasta extraída.

**Linux/macOS:**

```bash
./compilar.sh
java -cp out br.ufc.comunicacao.testes.Testes
```

**Windows (Prompt de Comando/cmd):**

```bat
compilar.bat
java -cp out br.ufc.comunicacao.testes.Testes
```

No PowerShell, chame `./compilar.bat` ou `cmd /c compilar.bat`. Para redirecionamento de arquivos binários (`<` e `>`), os exemplos abaixo foram escritos para **cmd** ou shells de Linux/macOS. Em Windows, se os acentos aparecem incorretamente, execute `chcp 65001` no cmd antes de iniciar as aplicações.

## 3. Exercícios 1 e 2 — Sockets e Streams

O objeto `Produto` fornece o nome à subclasse `ProdutoOutputStream`; o **array transmitido é de `Movimentacao`**, o outro POJO. O construtor recebe `(OutputStream destino, Movimentacao[] objetos, int quantidade)`. `enviar()` escreve o cabeçalho e apenas os primeiros `quantidade` elementos. `ProdutoInputStream(InputStream origem)` reconstrói os objetos com `lerMovimentacoes()`.

O formato do lote é binário e verificável:

```text
4 bytes: identificador mágico "MOV1"
4 bytes: quantidade de registros N
N vezes:
    4 bytes: produtoId (int)
    campo UTF: tipo (writeUTF / readUTF)
    4 bytes: quantidade (int)
    8 bytes: timestamp (long)
```

Os métodos `writeUTF`/`readUTF` usam o formato UTF modificado definido pelo Java; remetente e receptor usam a mesma API. `DataOutputStream` e `DataInputStream` escrevem/leem os números na mesma ordem e representação. `flush()` garante o envio; os streams padrão não são fechados pelo método `enviar()`.

### Teste A — arquivo (`FileOutputStream` / `FileInputStream`)

```bash
java -cp out br.ufc.comunicacao.streams.StreamsDemo escrever-arquivo movimentos.bin
java -cp out br.ufc.comunicacao.streams.StreamsDemo ler-arquivo movimentos.bin
```

### Teste B — saída e entrada padrão (`System.out` / `System.in`)

```bash
java -cp out br.ufc.comunicacao.streams.StreamsDemo escrever-console > stdout.bin
java -cp out br.ufc.comunicacao.streams.StreamsDemo ler-console < stdout.bin
```

**Importante:** a saída do primeiro comando é *binária*, não texto legível; o aviso é escrito em `System.err` para não misturar texto no lote.

### Teste C — socket remoto TCP (`OutputStream` / `InputStream`)

Abra dois terminais:

```bash
# Terminal 1 (primeiro)
java -cp out br.ufc.comunicacao.streams.StreamsDemo servidor-tcp 5001
```

```bash
# Terminal 2
java -cp out br.ufc.comunicacao.streams.StreamsDemo cliente-tcp 127.0.0.1 5001
```

O cliente transmite três movimentações, o servidor recebe os bytes via `Socket.getInputStream()` e imprime os três objetos reconstruídos. O servidor deste exercício encerra após atender a conexão de demonstração.

## 4. Exercício 3 — Serialização e request/reply

Inicie o servidor em um terminal:

```bash
java -cp out br.ufc.comunicacao.rpc.RpcServidor
```

Em outro, execute os pedidos:

```bash
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost LISTAR
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost CONSULTAR 1
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost VENDER 1 2
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost ENTRADA 2 3
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost RELATORIO
java -cp out br.ufc.comunicacao.rpc.RpcCliente localhost VENDER 1 999
```

Último comando: retorna uma **resposta de erro** sem deixar o estoque negativo. O servidor mantém o estado em memória enquanto estiver executando; reiniciá-lo restaura os dados de demonstração.

**Empacotamento:**

```text
Cliente: writeInt(MAGIC_REQ), writeUTF(operacao), writeInt(id), writeInt(qtd)
Servidor: readInt, readUTF, readInt, readInt  ->  executa operação
Servidor: writeInt(MAGIC_REP), writeBoolean(sucesso), writeUTF(mensagem),
          writeLong(valorTotalCentavos), writeInt(totalProdutos),
          para cada Produto: writeInt(id), writeUTF(nome), writeLong(preço), writeInt(qtd)
Cliente: lê os mesmos campos, na mesma ordem, e reconstitui os Produtos da resposta.
```

Isto é **serialização manual em representação externa binária**, não `ObjectOutputStream`. O POJO `Produto` é explicitamente transformado em bytes e reconstruído do outro lado. O servidor utiliza um pool de oito threads; a lógica de `EstoqueService` é sincronizada para que clientes simultâneos não retirem a mesma unidade duas vezes.

## 5. Exercício 4 — Multicast UDP com login TCP

Abra **quatro terminais** na mesma máquina:

```bash
# Terminal 1 — servidor
java -cp out br.ufc.comunicacao.multicast.MulticastServidor
```

```bash
# Terminal 2 — cliente A
java -cp out br.ufc.comunicacao.multicast.MulticastCliente localhost aluno 1234
```

```bash
# Terminal 3 — cliente B
java -cp out br.ufc.comunicacao.multicast.MulticastCliente localhost aluno 1234
```

Depois de ambos receberem a resposta de autenticação, digite no **terminal do servidor**:

```text
ALERTA|Estoque baixo da bolsa artesanal
NOTIFICACAO|Nova peça cadastrada
ATUALIZACAO|Tabela de estoque atualizada
```

Os dois clientes devem mostrar o **mesmo datagrama JSON** com `tipo`, `mensagem` e `timestamp`. O servidor também envia uma atualização automática a cada 15 segundos (a primeira após 5 segundos). Em cada cliente, digite `sair` para executar `leaveGroup` e terminar suas duas threads. No servidor, digite `sair` para encerrar.

Fluxo: o cliente **autentica por TCP** na porta **5003** (`aluno/1234`); somente depois a aplicação executa `joinGroup(230.0.0.1)`. A escuta UDP na porta **5004** fica em uma thread e a interação de teclado em outra. O servidor trata logins simultâneos em um pool e produz notificações a partir de um executor com duas threads. O endereço `230.0.0.1` é multicast IPv4 (classe D, como no enunciado); o TTL é 1, limitado à rede local.

**Limitação de segurança:** a autenticação é feita na aplicação cliente, mas o protocolo de multicast UDP por si só não impede que outro programa na mesma rede entre no grupo sem passar pelo TCP. Um sistema real precisaria de controle de rede, credenciais protegidas e criptografia/autorização de mensagens. Alguns roteadores, VPNs e redes Wi-Fi bloqueiam multicast; para a apresentação, prefira executar todos os processos na mesma máquina.

## 6. Questão extra 6 — votação didática

A pasta `extra/` implementa o que a questão extra solicita, separadamente do serviço de estoque:

- Eleitor e administrador fazem **LOGIN** por TCP (porta **5010**) com mensagens **XML**; no login, o servidor envia a lista de candidatos.
- Eleitores usam `VOTAR` por TCP, com bloqueio de voto duplicado, candidato inexistente e voto após o prazo.
- Administrador usa `ADICIONAR`, `REMOVER` e `NOTA`; a nota é o **único tipo de envio UDP multicast** neste módulo, ao grupo `230.0.0.2:5011`.
- O servidor atende diversas conexões por meio de um pool de 16 threads; o acesso ao estado da votação é sincronizado.
- `RESULTADO` só é liberado após o prazo. Apresenta total, votos, percentuais com duas casas e desfecho, inclusive empate ou ausência de votos. Para cada candidato: `percentual = votos / total * 100`, se `total > 0`.
- Candidatos fictícios iniciais: `Opção A` e `Opção B`. Não há ligação com uma eleição real.

**Terminal do servidor (prazo de 40 segundos):**

```bash
java -cp out br.ufc.comunicacao.extra.VotacaoServidor 40
```

**Terminais de clientes:**

```bash
java -cp out br.ufc.comunicacao.extra.VotacaoCliente localhost eleitor1 1234
java -cp out br.ufc.comunicacao.extra.VotacaoCliente localhost eleitor2 1234
java -cp out br.ufc.comunicacao.extra.VotacaoCliente localhost admin admin123
```

Em cada terminal de eleitor, use `listar` e `votar 1` ou `votar 2`. No terminal do administrador, use `adicionar Opção C`, `remover 3` (apenas se não houver votos nesse candidato) e `nota A votação encerra em breve`. Ambos os eleitores devem receber a nota via UDP. Depois dos 40 segundos, `resultado` mostra a apuração. Uma tentativa posterior de `votar 1` retorna erro. Digite `sair` em cada cliente; interrompa o servidor com **Ctrl+C**.

A figura da página 3 do enunciado mostra servidor, armazenamento, eleitores e administrador. Nesta solução acadêmica, o armazenamento foi implementado **em memória** (a figura ilustra um banco, mas o texto da questão extra não obriga um SGBD). Uma solução persistente pode substituir o mapa de candidatos por banco de dados sem alterar o protocolo TCP/UDP.

## 7. Testes, demonstração e evidências

No Linux/macOS, execute:

```bash
./testar_integracao.sh
```

O script compila, executa os testes de streams por três meios, cliente/servidor RPC, dois clientes multicast e a questão extra com voto duplicado, credencial inválida, prazo encerrado, operações administrativas, nota multicast e empate. Os registros de execução estão em `evidencias/`.

Os testes unitários estão em `src/br/ufc/comunicacao/testes/Testes.java` e verificam, entre outros, limites dos streams, cabeçalho inválido, serialização de ida/volta, vendas concorrentes, XML especial e bloqueio de entidades externas. No Windows, use `compilar.bat`, execute `Testes` e reproduza as etapas manuais dos itens 3–6.

**Critérios de avaliação:** correção funcional (operações e validações), organização (pacotes separados), conceitos (sockets/streams/serialização/multicast/threads), testes (script/logs) e documentação (este README e `ROTEIRO_APRESENTACAO.md`).

## 8. Limitações deliberadas e decisões de projeto

O escopo do trabalho não exigiu interface gráfica, banco, nuvem ou integração entre as notificações do exercício 4 e cada venda do exercício 3. Por isso as notificações são comandadas pelo servidor multicast (com batimento automático), e os serviços usam dados em memória. Todas as portas são demonstrativas e podem ser alteradas nas respectivas classes se já estiverem ocupadas. O multicast depende da interface e das regras de firewall do sistema operacional. A questão extra usa XML por ser uma das representações aceitas pelo enunciado; o exercício 3 usa binário para evidenciar explicitamente o empacotamento e desempacotamento.
