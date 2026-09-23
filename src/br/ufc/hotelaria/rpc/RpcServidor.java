package br.ufc.hotelaria.rpc;

import br.ufc.hotelaria.modelo.ComplexoTuristico;
import br.ufc.hotelaria.modelo.Hospede;
import br.ufc.hotelaria.modelo.Hotel;
import br.ufc.hotelaria.modelo.Motel;
import br.ufc.hotelaria.modelo.Pousada;
import br.ufc.hotelaria.modelo.Reserva;
import br.ufc.hotelaria.servico.HospedagemService;
import br.ufc.hotelaria.servico.ReservaService;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servidor de hotelaria: vários clientes TCP compartilham o mesmo catálogo e as reservas. */
public final class RpcServidor {
    private final HospedagemService hospedagens =
            new HospedagemService(new ComplexoTuristico("Complexo Turístico Dunas"));
    private final ReservaService reservas = new ReservaService(hospedagens);

    public RpcServidor() {
        hospedagens.cadastrar(new Hotel(1, "Hotel Praia Azul", "Av. Beira-Mar, 10", 35000, 5, 4));
        hospedagens.cadastrar(new Motel(2, "Motel Sol", "Rua das Flores, 20", 18000, 3, true));
        hospedagens.cadastrar(new Pousada(3, "Pousada do Bosque", "Rua Verde, 30", 22000, 2, true));
    }

    public ProtocoloRpc.Resposta processar(ProtocoloRpc.Requisicao req) {
        try {
            return switch (req.operacao().toUpperCase(java.util.Locale.ROOT)) {
                case "LISTAR" -> ok("Meios de hospedagem do " + hospedagens.nomeComplexo(), -1, 0,
                        hospedagens.listar(), List.of());
                case "CONSULTAR" -> ok("Meio de hospedagem encontrado", -1, 0,
                        List.of(hospedagens.consultar(req.meioId())), List.of());
                case "REGISTRAR" -> {
                    Reserva nova = new Reserva(req.reservaId(), req.meioId(),
                            new Hospede(req.hospedeId(), req.hospedeNome()),
                            LocalDate.ofEpochDay(req.entradaEpoch()), LocalDate.ofEpochDay(req.saidaEpoch()),
                            req.unidades());
                    Reserva salva = reservas.registrarReserva(nova);
                    yield ok("Reserva registrada", -1, reservas.valorReservaCentavos(salva.getId()),
                            List.of(), List.of(salva));
                }
                case "CANCELAR" -> ok("Reserva cancelada", -1, 0,
                        List.of(), List.of(reservas.cancelarReserva(req.reservaId())));
                case "EFETIVAR" -> {
                    Reserva efetivada = reservas.efetivarReserva(req.reservaId());
                    yield ok("Reserva efetivada", -1, reservas.valorReservaCentavos(efetivada.getId()),
                            List.of(), List.of(efetivada));
                }
                case "RESERVAS" -> ok("Reservas do complexo", -1, 0, List.of(), reservas.listar());
                case "CONSULTAR_RESERVA" -> ok("Reserva encontrada", -1, 0,
                        List.of(), List.of(reservas.consultar(req.reservaId())));
                case "DISPONIBILIDADE" -> ok("Unidades disponíveis no período",
                        reservas.disponibilidade(req.meioId(), LocalDate.ofEpochDay(req.entradaEpoch()),
                                LocalDate.ofEpochDay(req.saidaEpoch())), 0, List.of(), List.of());
                default -> erro("Operação desconhecida: " + req.operacao());
            };
        } catch (IllegalArgumentException | ArithmeticException e) {
            return erro(e.getMessage());
        }
    }
    private static ProtocoloRpc.Resposta ok(String mensagem, int unidades, long centavos,
                                           List<br.ufc.hotelaria.modelo.MeioHospedagem> meios,
                                           List<Reserva> reservas) {
        return new ProtocoloRpc.Resposta(true, mensagem, unidades, centavos, meios, reservas);
    }
    private static ProtocoloRpc.Resposta erro(String mensagem) {
        return new ProtocoloRpc.Resposta(false, mensagem, -1, 0, List.of(), List.of());
    }
    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5002;
        RpcServidor app = new RpcServidor();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor de hotelaria ouvindo TCP:" + porta);
            while (true) {
                Socket cliente = servidor.accept();
                pool.submit(() -> {
                    try (cliente) {
                        cliente.setSoTimeout(10_000);
                        var req = ProtocoloRpc.lerRequisicao(cliente.getInputStream());
                        ProtocoloRpc.enviarResposta(cliente.getOutputStream(), app.processar(req));
                    } catch (Exception e) {
                        System.err.println("Erro no atendimento TCP: " + e.getMessage());
                    }
                });
            }
        } finally { pool.shutdownNow(); }
    }
}
