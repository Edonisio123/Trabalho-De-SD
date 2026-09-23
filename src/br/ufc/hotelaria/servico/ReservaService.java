package br.ufc.hotelaria.servico;

import br.ufc.hotelaria.contrato.Reservas;
import br.ufc.hotelaria.modelo.MeioHospedagem;
import br.ufc.hotelaria.modelo.Reserva;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serviço 2: implementa Reservas e protege a disponibilidade contra pedidos concorrentes. */
public final class ReservaService implements Reservas {
    private final HospedagemService hospedagens;
    private final Map<Integer, Reserva> reservas = new LinkedHashMap<>();
    public ReservaService(HospedagemService hospedagens) {
        this.hospedagens = java.util.Objects.requireNonNull(hospedagens);
    }

    public synchronized int disponibilidade(int meioId, LocalDate entrada, LocalDate saida) {
        MeioHospedagem meio = hospedagens.consultar(meioId);
        if (entrada == null || saida == null || !saida.isAfter(entrada))
            throw new IllegalArgumentException("Intervalo de datas inválido");
        int ocupadas = 0;
        for (Reserva r : reservas.values())
            if (r.getMeioHospedagemId() == meioId && r.ocupa(entrada, saida))
                ocupadas = Math.addExact(ocupadas, r.getUnidades());
        return meio.getTotalUnidades() - ocupadas;
    }

    @Override public synchronized Reserva registrarReserva(Reserva reserva) {
        if (reserva == null || reserva.getStatus() != Reserva.Status.PENDENTE)
            throw new IllegalArgumentException("Nova reserva deve ser pendente");
        if (reservas.containsKey(reserva.getId())) throw new IllegalArgumentException("ID de reserva já existente");
        if (disponibilidade(reserva.getMeioHospedagemId(), reserva.getEntrada(), reserva.getSaida())
                < reserva.getUnidades()) throw new IllegalArgumentException("Unidades indisponíveis no período");
        Reserva salva = reserva.copiar();
        reservas.put(salva.getId(), salva);
        return salva.copiar();
    }
    @Override public synchronized Reserva cancelarReserva(int reservaId) {
        Reserva salva = obter(reservaId);
        salva.cancelar();
        return salva.copiar();
    }
    @Override public synchronized Reserva efetivarReserva(int reservaId) {
        Reserva salva = obter(reservaId);
        salva.efetivar();
        return salva.copiar();
    }
    public synchronized Reserva consultar(int reservaId) { return obter(reservaId).copiar(); }
    public synchronized List<Reserva> listar() {
        List<Reserva> copia = new ArrayList<>();
        for (Reserva r : reservas.values()) copia.add(r.copiar());
        return copia;
    }
    public synchronized long valorReservaCentavos(int id) {
        Reserva r = obter(id);
        return Math.multiplyExact(Math.multiplyExact(hospedagens.consultar(r.getMeioHospedagemId())
                .getDiariaCentavos(), r.getDiarias()), r.getUnidades());
    }
    private Reserva obter(int id) {
        Reserva reserva = reservas.get(id);
        if (reserva == null) throw new IllegalArgumentException("Reserva não encontrada: " + id);
        return reserva;
    }
}
