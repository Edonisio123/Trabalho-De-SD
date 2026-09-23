package br.ufc.hotelaria.streams;

import br.ufc.hotelaria.modelo.Reserva;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Ex. 2: InputStream que reconstrói arrays transmitidos pelo OutputStream do ex. 1. */
public final class MeioHospedagemInputStream extends InputStream {
    private final InputStream origem;
    public MeioHospedagemInputStream(InputStream origem) { this.origem = Objects.requireNonNull(origem); }

    public Reserva[] lerReservas() throws IOException {
        DataInputStream dados = new DataInputStream(this);
        if (dados.readInt() != MeioHospedagemOutputStream.MAGIC)
            throw new IOException("Cabeçalho HRS1 inválido");
        int quantidade = dados.readInt();
        if (quantidade < 0 || quantidade > 10_000)
            throw new IOException("Quantidade de reservas inválida");
        Reserva[] reservas = new Reserva[quantidade];
        for (int i = 0; i < quantidade; i++) reservas[i] = ReservaCodec.ler(dados);
        return reservas;
    }
    @Override public int read() throws IOException { return origem.read(); }
    @Override public int read(byte[] bytes, int off, int len) throws IOException { return origem.read(bytes, off, len); }
    @Override public long skip(long n) throws IOException { return origem.skip(n); }
    @Override public int available() throws IOException { return origem.available(); }
    @Override public void close() throws IOException { origem.close(); }
}
