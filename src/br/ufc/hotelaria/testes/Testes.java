package br.ufc.hotelaria.testes;

import br.ufc.hotelaria.extra.XmlVotacao;
import br.ufc.hotelaria.modelo.ComplexoTuristico;
import br.ufc.hotelaria.modelo.Hospede;
import br.ufc.hotelaria.modelo.Hotel;
import br.ufc.hotelaria.modelo.MeioHospedagem;
import br.ufc.hotelaria.modelo.Motel;
import br.ufc.hotelaria.modelo.Pousada;
import br.ufc.hotelaria.modelo.Reserva;
import br.ufc.hotelaria.rpc.ProtocoloRpc;
import br.ufc.hotelaria.rpc.RpcServidor;
import br.ufc.hotelaria.servico.HospedagemService;
import br.ufc.hotelaria.servico.ReservaService;
import br.ufc.hotelaria.streams.MeioHospedagemInputStream;
import br.ufc.hotelaria.streams.MeioHospedagemOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Testes automáticos de modelo OO, regras de reserva, Streams e protocolo RPC. */
public final class Testes {
    private static int verificacoes = 0;
    private static void checar(boolean condicao, String descricao) {
        verificacoes++;
        if (!condicao) throw new AssertionError(descricao);
    }
    private static void erro(Runnable operacao, String descricao) {
        try { operacao.run(); throw new AssertionError(descricao); }
        catch (IllegalArgumentException esperado) { checar(true, descricao); }
    }
    private static Reserva nova(int id, int meioId, int unidades, String inicio, String fim) {
        return new Reserva(id, meioId, new Hospede(id, "Hospede " + id),
                LocalDate.parse(inicio), LocalDate.parse(fim), unidades);
    }
    public static void main(String[] args) throws Exception {
        modeloOO();
        contratosEConcorrencia();
        streams();
        protocoloRpc();
        xmlExtra();
        System.out.println("TESTES OK: " + verificacoes + " verificações.");
    }
    private static void modeloOO() {
        ComplexoTuristico complexo = new ComplexoTuristico("Complexo das Dunas");
        MeioHospedagem hotel = new Hotel(1, "Hotel", "Rua A", 15000, 3, 5);
        MeioHospedagem motel = new Motel(2, "Motel", "Rua B", 10000, 2, true);
        MeioHospedagem pousada = new Pousada(3, "Pousada", "Rua C", 9000, 1, true);
        complexo.agregar(hotel); complexo.agregar(motel); complexo.agregar(pousada);
        checar(complexo.listar().size() == 3, "Complexo agrega os três subtipos");
        checar(complexo.buscar(1) == hotel, "Agregação preserva identidade do objeto externo");
        checar(hotel.getTipo().equals("HOTEL") && motel.getTipo().equals("MOTEL")
                && pousada.getTipo().equals("POUSADA"), "Polimorfismo da superclasse");
        complexo.desagregar(2);
        checar(motel.getNome().equals("Motel") && complexo.listar().size() == 2,
                "Desagregação não destrói a instância externa");
        erro(() -> complexo.agregar(new Hotel(1, "Repetido", "Rua D", 1000, 1, 1)),
                "ID de hospedagem duplicado é recusado");
    }
    private static void contratosEConcorrencia() throws Exception {
        ComplexoTuristico complexo = new ComplexoTuristico("Complexo");
        HospedagemService hospedagens = new HospedagemService(complexo);
        hospedagens.cadastrar(new Hotel(1, "Hotel", "Rua A", 10000, 3, 3));
        ReservaService servico = new ReservaService(hospedagens);
        Reserva r = servico.registrarReserva(nova(1, 1, 2, "2027-01-10", "2027-01-12"));
        checar(r.getStatus() == Reserva.Status.PENDENTE, "Interface registrarReserva");
        checar(servico.disponibilidade(1, LocalDate.parse("2027-01-11"),
                LocalDate.parse("2027-01-12")) == 1, "Sobreposição parcial ocupa unidades");
        erro(() -> servico.registrarReserva(nova(2, 1, 2, "2027-01-11", "2027-01-12")),
                "Impedir reserva acima da capacidade");
        checar(servico.disponibilidade(1, LocalDate.parse("2027-01-12"),
                LocalDate.parse("2027-01-13")) == 3, "Saída e próxima entrada no mesmo dia não sobrepõem");
        checar(servico.valorReservaCentavos(1) == 40000, "Valor = diária × dias × unidades");
        checar(servico.efetivarReserva(1).getStatus() == Reserva.Status.EFETIVADA,
                "Interface efetivarReserva");
        erro(() -> servico.efetivarReserva(1), "Impedir efetivação duplicada");
        checar(servico.cancelarReserva(1).getStatus() == Reserva.Status.CANCELADA,
                "Interface cancelarReserva");
        checar(servico.disponibilidade(1, LocalDate.parse("2027-01-10"),
                LocalDate.parse("2027-01-12")) == 3, "Cancelamento libera disponibilidade");
        erro(() -> servico.efetivarReserva(1), "Reserva cancelada não pode ser efetivada");
        checar(servico.registrarReserva(nova(2, 1, 3, "2027-01-10", "2027-01-12"))
                .getUnidades() == 3, "Capacidade pode ser novamente reservada");
        var executor = Executors.newFixedThreadPool(10);
        try {
            ReservaService concorrente = new ReservaService(hospedagens);
            @SuppressWarnings("unchecked")
            Future<Boolean>[] pedidos = new Future[10];
            for (int i = 0; i < pedidos.length; i++) {
                final int id = 100 + i;
                pedidos[i] = executor.submit(() -> {
                    try { concorrente.registrarReserva(nova(id, 1, 1, "2027-02-10", "2027-02-12")); return true; }
                    catch (IllegalArgumentException e) { return false; }
                });
            }
            int aceitos = 0;
            for (Future<Boolean> pedido : pedidos) if (pedido.get()) aceitos++;
            checar(aceitos == 3, "Dez clientes disputam três unidades: exatamente três reservas");
            checar(concorrente.disponibilidade(1, LocalDate.parse("2027-02-10"),
                    LocalDate.parse("2027-02-12")) == 0, "Capacidade final não fica negativa");
        } finally { executor.shutdownNow(); }
    }
    private static void streams() throws Exception {
        Reserva[] entradas = {
            nova(1, 1, 1, "2027-03-01", "2027-03-02"),
            nova(2, 2, 2, "2027-03-02", "2027-03-04"),
            nova(3, 3, 1, "2027-03-03", "2027-03-05")
        };
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new MeioHospedagemOutputStream(buffer, entradas, 2).enviar();
        Reserva[] recebidas = new MeioHospedagemInputStream(new ByteArrayInputStream(buffer.toByteArray()))
                .lerReservas();
        checar(recebidas.length == 2, "OutputStream respeita quantidade indicada");
        checar(recebidas[0].getHospede().getNome().equals("Hospede 1")
                && recebidas[1].getUnidades() == 2, "InputStream reconstitui outro POJO");
        checar(recebidas[1].getSaida().equals(LocalDate.parse("2027-03-04")),
                "As datas são preservadas na serialização");
        buffer.reset();
        new MeioHospedagemOutputStream(buffer, entradas, 0).enviar();
        checar(new MeioHospedagemInputStream(new ByteArrayInputStream(buffer.toByteArray()))
                .lerReservas().length == 0, "Lote de tamanho zero é válido");
        erro(() -> new MeioHospedagemOutputStream(buffer, entradas, 4),
                "Quantidade maior que array é recusada");
        try {
            new MeioHospedagemInputStream(new ByteArrayInputStream(new byte[] {0, 0, 0, 1}))
                    .lerReservas();
            throw new AssertionError("Cabeçalho inválido foi aceito");
        } catch (IOException esperado) { checar(true, "Cabeçalho binário incorreto é recusado"); }
    }
    private static void protocoloRpc() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ProtocoloRpc.Requisicao req = new ProtocoloRpc.Requisicao("REGISTRAR", 400, 1, 88,
                "Ana Souza", LocalDate.parse("2027-06-01").toEpochDay(),
                LocalDate.parse("2027-06-04").toEpochDay(), 2);
        ProtocoloRpc.enviarRequisicao(buffer, req);
        ProtocoloRpc.Requisicao decodificada = ProtocoloRpc.lerRequisicao(
                new ByteArrayInputStream(buffer.toByteArray()));
        checar(decodificada.hospedeNome().equals("Ana Souza") && decodificada.unidades() == 2,
                "Request é empacotado e desempacotado");
        RpcServidor servidor = new RpcServidor();
        ProtocoloRpc.Resposta registrada = servidor.processar(decodificada);
        checar(registrada.sucesso() && registrada.valorCentavos() == 210000,
                "Servidor registra a reserva e calcula as diárias");
        buffer.reset();
        ProtocoloRpc.enviarResposta(buffer, registrada);
        ProtocoloRpc.Resposta resposta = ProtocoloRpc.lerResposta(
                new ByteArrayInputStream(buffer.toByteArray()));
        checar(resposta.reservas().get(0).getHospede().getNome().equals("Ana Souza"),
                "Reply serializa e reconstitui Reserva");
        ProtocoloRpc.Resposta listagem = servidor.processar(new ProtocoloRpc.Requisicao(
                "LISTAR", 0, 0, 0, "", 0, 0, 0));
        buffer.reset();
        ProtocoloRpc.enviarResposta(buffer, listagem);
        List<MeioHospedagem> reconstruidos = ProtocoloRpc.lerResposta(
                new ByteArrayInputStream(buffer.toByteArray())).meios();
        checar(reconstruidos.get(0) instanceof Hotel && reconstruidos.get(1) instanceof Motel
                && reconstruidos.get(2) instanceof Pousada, "RPC preserva os três subtipos OO");
        checar(((Hotel) reconstruidos.get(0)).getEstrelas() == 4
                && ((Motel) reconstruidos.get(1)).isGaragemPrivativa()
                && ((Pousada) reconstruidos.get(2)).isCafeDaManhaIncluso(),
                "RPC preserva atributos específicos das subclasses");
        var efetivar = new ProtocoloRpc.Requisicao("EFETIVAR", 400, 0, 0, "", 0, 0, 0);
        checar(servidor.processar(efetivar).reservas().get(0).getStatus()
                == Reserva.Status.EFETIVADA, "Efetivar remotamente");
        checar(!servidor.processar(req).sucesso(), "Servidor recusa ID duplicado");
        var cancelar = new ProtocoloRpc.Requisicao("CANCELAR", 400, 0, 0, "", 0, 0, 0);
        checar(servidor.processar(cancelar).reservas().get(0).getStatus()
                == Reserva.Status.CANCELADA, "Cancelar remotamente");
    }
    private static void xmlExtra() throws Exception {
        String xml = XmlVotacao.requisicao("ADICIONAR", Map.of("nome", "Opção & alternativa"));
        checar(XmlVotacao.analisar(xml).getAttribute("nome").equals("Opção & alternativa"),
                "Questão extra: XML preserva caracteres especiais");
        try {
            XmlVotacao.analisar("<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><r>&x;</r>");
            throw new AssertionError("Entidade externa deveria falhar");
        } catch (Exception esperado) { checar(true, "Parser da questão extra recusa entidades externas"); }
    }
}
