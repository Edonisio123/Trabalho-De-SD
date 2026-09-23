package br.ufc.hotelaria.streams;

import br.ufc.hotelaria.modelo.Reserva;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Ex. 1: OutputStream nomeado pela superclasse; envia array do OUTRO POJO Reserva. */
public final class MeioHospedagemOutputStream extends OutputStream {
    public static final int MAGIC = 0x48525331; // HRS1: Hotelaria Reservas Stream versão 1
    private final OutputStream destino;
    private final Reserva[] reservas;
    private final int quantidade;
    private boolean enviado;

    public MeioHospedagemOutputStream(OutputStream destino, Reserva[] reservas, int quantidade) {
        this.destino = Objects.requireNonNull(destino, "Destino é obrigatório");
        this.reservas = Objects.requireNonNull(reservas, "Array é obrigatório");
        if (quantidade < 0 || quantidade > reservas.length || quantidade > 10_000)
            throw new IllegalArgumentException("Quantidade de objetos inválida");
        for (int i = 0; i < quantidade; i++) Objects.requireNonNull(reservas[i]);
        this.quantidade = quantidade;
    }

    public void enviar() throws IOException {
        if (enviado) throw new IllegalStateException("Lote já transmitido");
        enviado = true;
        DataOutputStream dados = new DataOutputStream(this);
        dados.writeInt(MAGIC);
        dados.writeInt(quantidade);
        for (int i = 0; i < quantidade; i++) ReservaCodec.escrever(dados, reservas[i]);
        dados.flush();
    }
    @Override public void write(int b) throws IOException { destino.write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { destino.write(b, off, len); }
    @Override public void flush() throws IOException { destino.flush(); }
    @Override public void close() throws IOException { destino.close(); }
}
