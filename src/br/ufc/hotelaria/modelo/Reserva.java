package br.ufc.hotelaria.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** POJO da reserva; fim da hospedagem é exclusivo para verificação de sobreposição. */
public final class Reserva {
    public enum Status { PENDENTE, EFETIVADA, CANCELADA }

    private final int id;
    private final int meioHospedagemId;
    private final Hospede hospede;
    private final LocalDate entrada;
    private final LocalDate saida;
    private final int unidades;
    private Status status;

    public Reserva(int id, int meioHospedagemId, Hospede hospede, LocalDate entrada,
                   LocalDate saida, int unidades) {
        this(id, meioHospedagemId, hospede, entrada, saida, unidades, Status.PENDENTE);
    }
    public Reserva(int id, int meioHospedagemId, Hospede hospede, LocalDate entrada,
                   LocalDate saida, int unidades, Status status) {
        if (id <= 0 || meioHospedagemId <= 0 || unidades <= 0)
            throw new IllegalArgumentException("IDs e número de unidades devem ser positivos");
        this.hospede = Objects.requireNonNull(hospede);
        this.entrada = Objects.requireNonNull(entrada);
        this.saida = Objects.requireNonNull(saida);
        if (!saida.isAfter(entrada)) throw new IllegalArgumentException("Saída deve ocorrer após a entrada");
        this.status = Objects.requireNonNull(status);
        this.id = id;
        this.meioHospedagemId = meioHospedagemId;
        this.unidades = unidades;
    }
    public int getId() { return id; }
    public int getMeioHospedagemId() { return meioHospedagemId; }
    public Hospede getHospede() { return hospede; }
    public LocalDate getEntrada() { return entrada; }
    public LocalDate getSaida() { return saida; }
    public int getUnidades() { return unidades; }
    public Status getStatus() { return status; }
    public long getDiarias() { return ChronoUnit.DAYS.between(entrada, saida); }
    public boolean ocupa(LocalDate inicio, LocalDate fim) {
        return status != Status.CANCELADA && entrada.isBefore(fim) && saida.isAfter(inicio);
    }
    public void efetivar() {
        if (status != Status.PENDENTE) throw new IllegalArgumentException("Só reserva pendente pode ser efetivada");
        status = Status.EFETIVADA;
    }
    public void cancelar() {
        if (status == Status.CANCELADA) throw new IllegalArgumentException("Reserva já cancelada");
        status = Status.CANCELADA;
    }
    public Reserva copiar() {
        return new Reserva(id, meioHospedagemId, hospede, entrada, saida, unidades, status);
    }
    @Override public String toString() {
        return "Reserva{id=%d, meioId=%d, hospede='%s', entrada=%s, saida=%s, unidades=%d, status=%s}"
                .formatted(id, meioHospedagemId, hospede.getNome(), entrada, saida, unidades, status);
    }
}
