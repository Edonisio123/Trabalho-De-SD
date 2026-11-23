package br.ufc.hotelaria.fila;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Cliente leve para produtores e consumidores. Nenhum produtor conhece o consumidor final. */
public final class FilaCliente {
    public record Mensagem(long id, String texto) {}
    public record Estado(int pendentes, int disponiveis) {}
    private FilaCliente() {}

    private static Socket conectar(String host, int porta) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, porta), 1200);
        socket.setSoTimeout(3_000);
        return socket;
    }
    public static long publicar(String host, int porta, String texto) throws IOException {
        try (Socket socket = conectar(host, porta)) {
            var out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("PUBLICAR"); out.writeUTF(texto); out.flush();
            return new DataInputStream(socket.getInputStream()).readLong();
        }
    }
    public static Mensagem receber(String host, int porta) throws IOException {
        try (Socket socket = conectar(host, porta)) {
            var out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("RECEBER"); out.flush();
            var in = new DataInputStream(socket.getInputStream());
            return in.readBoolean() ? new Mensagem(in.readLong(), in.readUTF()) : null;
        }
    }
    public static boolean confirmar(String host, int porta, long id) throws IOException {
        try (Socket socket = conectar(host, porta)) {
            var out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("CONFIRMAR"); out.writeLong(id); out.flush();
            return new DataInputStream(socket.getInputStream()).readBoolean();
        }
    }
    public static Estado estado(String host, int porta) throws IOException {
        try (Socket socket = conectar(host, porta)) {
            var out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("ESTADO"); out.flush();
            var in = new DataInputStream(socket.getInputStream());
            return new Estado(in.readInt(), in.readInt());
        }
    }
}
