package br.ufc.hotelaria.rpc;

import br.ufc.hotelaria.modelo.Hotel;
import br.ufc.hotelaria.modelo.MeioHospedagem;
import br.ufc.hotelaria.modelo.Motel;
import br.ufc.hotelaria.modelo.Pousada;
import br.ufc.hotelaria.modelo.Reserva;
import br.ufc.hotelaria.streams.ReservaCodec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Ex. 3: protocolo binário explícito de request/reply; não depende de serialização Java nativa. */
public final class ProtocoloRpc {
    private static final int REQ = 0x48525131; // HRQ1
    private static final int REP = 0x48525031; // HRP1
    private static final int LIMITE = 10_000;
    private ProtocoloRpc() {}

    public record Requisicao(String operacao, int reservaId, int meioId, int hospedeId,
                             String hospedeNome, long entradaEpoch, long saidaEpoch, int unidades) {
        public Requisicao {
            if (operacao == null || operacao.isBlank()) throw new IllegalArgumentException("Operação obrigatória");
            if (hospedeNome == null) hospedeNome = "";
        }
    }
    public record Resposta(boolean sucesso, String mensagem, int disponibilidade,
                           long valorCentavos, List<MeioHospedagem> meios, List<Reserva> reservas) {}

    public static void enviarRequisicao(OutputStream destino, Requisicao req) throws IOException {
        DataOutputStream out = new DataOutputStream(destino);
        out.writeInt(REQ);
        out.writeUTF(req.operacao());
        out.writeInt(req.reservaId());
        out.writeInt(req.meioId());
        out.writeInt(req.hospedeId());
        out.writeUTF(req.hospedeNome());
        out.writeLong(req.entradaEpoch());
        out.writeLong(req.saidaEpoch());
        out.writeInt(req.unidades());
        out.flush();
    }
    public static Requisicao lerRequisicao(InputStream origem) throws IOException {
        DataInputStream in = new DataInputStream(origem);
        if (in.readInt() != REQ) throw new IOException("Cabeçalho HRQ1 de request inválido");
        return new Requisicao(in.readUTF(), in.readInt(), in.readInt(), in.readInt(),
                in.readUTF(), in.readLong(), in.readLong(), in.readInt());
    }
    public static void enviarResposta(OutputStream destino, Resposta rep) throws IOException {
        DataOutputStream out = new DataOutputStream(destino);
        out.writeInt(REP);
        out.writeBoolean(rep.sucesso());
        out.writeUTF(rep.mensagem());
        out.writeInt(rep.disponibilidade());
        out.writeLong(rep.valorCentavos());
        out.writeInt(rep.meios().size());
        for (MeioHospedagem meio : rep.meios()) escreverMeio(out, meio);
        out.writeInt(rep.reservas().size());
        for (Reserva reserva : rep.reservas()) ReservaCodec.escrever(out, reserva);
        out.flush();
    }
    public static Resposta lerResposta(InputStream origem) throws IOException {
        DataInputStream in = new DataInputStream(origem);
        if (in.readInt() != REP) throw new IOException("Cabeçalho HRP1 de reply inválido");
        boolean sucesso = in.readBoolean();
        String mensagem = in.readUTF();
        int disponibilidade = in.readInt();
        long valor = in.readLong();
        int qtdMeios = quantidade(in.readInt());
        List<MeioHospedagem> meios = new ArrayList<>();
        for (int i = 0; i < qtdMeios; i++) meios.add(lerMeio(in));
        int qtdReservas = quantidade(in.readInt());
        List<Reserva> reservas = new ArrayList<>();
        for (int i = 0; i < qtdReservas; i++) reservas.add(ReservaCodec.ler(in));
        return new Resposta(sucesso, mensagem, disponibilidade, valor, meios, reservas);
    }
    private static int quantidade(int valor) throws IOException {
        if (valor < 0 || valor > LIMITE) throw new IOException("Quantidade fora do limite do protocolo");
        return valor;
    }
    private static void escreverMeio(DataOutputStream out, MeioHospedagem m) throws IOException {
        out.writeUTF(m.getTipo());
        out.writeInt(m.getId());
        out.writeUTF(m.getNome());
        out.writeUTF(m.getEndereco());
        out.writeLong(m.getDiariaCentavos());
        out.writeInt(m.getTotalUnidades());
        if (m instanceof Hotel h) out.writeInt(h.getEstrelas());
        else if (m instanceof Motel m2) out.writeBoolean(m2.isGaragemPrivativa());
        else if (m instanceof Pousada p) out.writeBoolean(p.isCafeDaManhaIncluso());
        else throw new IOException("Tipo de hospedagem não reconhecido: " + m.getClass().getName());
    }
    private static MeioHospedagem lerMeio(DataInputStream in) throws IOException {
        String tipo = in.readUTF();
        int id = in.readInt();
        String nome = in.readUTF();
        String endereco = in.readUTF();
        long diaria = in.readLong();
        int unidades = in.readInt();
        try {
            return switch (tipo) {
                case "HOTEL" -> new Hotel(id, nome, endereco, diaria, unidades, in.readInt());
                case "MOTEL" -> new Motel(id, nome, endereco, diaria, unidades, in.readBoolean());
                case "POUSADA" -> new Pousada(id, nome, endereco, diaria, unidades, in.readBoolean());
                default -> throw new IOException("Tipo recebido desconhecido: " + tipo);
            };
        } catch (IllegalArgumentException e) {
            throw new IOException("Dados de hospedagem inválidos", e);
        }
    }
}
