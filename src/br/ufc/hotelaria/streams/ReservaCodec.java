package br.ufc.hotelaria.streams;

import br.ufc.hotelaria.modelo.Hospede;
import br.ufc.hotelaria.modelo.Reserva;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/** Representação externa de um POJO Reserva: mesmo formato em Streams e RPC. */
public final class ReservaCodec {
    private ReservaCodec() {}

    public static void escrever(DataOutputStream out, Reserva r) throws IOException {
        out.writeInt(r.getId());
        out.writeInt(r.getMeioHospedagemId());
        out.writeInt(r.getHospede().getId());
        out.writeUTF(r.getHospede().getNome());
        out.writeLong(r.getEntrada().toEpochDay());
        out.writeLong(r.getSaida().toEpochDay());
        out.writeInt(r.getUnidades());
        out.writeUTF(r.getStatus().name());
    }
    public static Reserva ler(DataInputStream in) throws IOException {
        try {
            int reservaId = in.readInt();
            int meioId = in.readInt();
            Hospede hospede = new Hospede(in.readInt(), in.readUTF());
            LocalDate entrada = LocalDate.ofEpochDay(in.readLong());
            LocalDate saida = LocalDate.ofEpochDay(in.readLong());
            int unidades = in.readInt();
            Reserva.Status status = Reserva.Status.valueOf(in.readUTF());
            return new Reserva(reservaId, meioId, hospede, entrada, saida, unidades, status);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new IOException("Reserva recebida com campos inválidos", e);
        }
    }
}
