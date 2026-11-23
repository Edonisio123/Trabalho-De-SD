package br.ufc.hotelaria.fila;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/** Broker TCP: fila FIFO durável no disco, confirmação explícita e reentrega após falha. */
public final class FilaServidor {
    private record Mensagem(long id, String texto) {}
    private final TreeMap<Long, Mensagem> mensagens = new TreeMap<>();
    private final Map<Long, Long> prazoEntrega = new TreeMap<>();
    private final Path pasta;
    private final long leaseMs;
    private long proximoId;
    private final Path sequencia;

    public FilaServidor(Path pasta, long leaseMs) throws IOException {
        if (leaseMs < 1) throw new IllegalArgumentException("Prazo de confirmação inválido");
        this.pasta = pasta;
        this.sequencia = pasta.resolve("sequencia.txt");
        this.leaseMs = leaseMs;
        Files.createDirectories(pasta);
        try (var arquivos = Files.list(pasta)) {
            for (Path arquivo : arquivos.filter(p -> p.getFileName().toString().matches("[0-9]{20}\\.msg")).sorted().toList()) {
                long id = Long.parseLong(arquivo.getFileName().toString().substring(0, 20));
                try (var entrada = new DataInputStream(Files.newInputStream(arquivo))) {
                    mensagens.put(id, new Mensagem(id, entrada.readUTF()));
                }
                proximoId = Math.max(proximoId, id);
            }
        }
        if (Files.exists(sequencia)) {
            proximoId = Math.max(proximoId, Long.parseLong(Files.readString(sequencia).trim()));
        }
        System.out.println("Fila recuperada: " + mensagens.size() + " mensagens em " + pasta.toAbsolutePath());
    }

    private Path arquivo(long id) { return pasta.resolve(String.format("%020d.msg", id)); }

    private synchronized long publicar(String texto) throws IOException {
        if (texto.isBlank()) throw new IOException("Mensagem vazia");
        long id = ++proximoId;
        Path temporarioSeq = Files.createTempFile(pasta, ".sequencia-", ".tmp");
        try {
            Files.writeString(temporarioSeq, Long.toString(id));
            try { Files.move(temporarioSeq, sequencia, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporarioSeq, sequencia, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporarioSeq); }
        Path temporario = Files.createTempFile(pasta, ".pendente-", ".tmp");
        try {
            try (var saida = new DataOutputStream(Files.newOutputStream(temporario))) { saida.writeUTF(texto); }
            try { Files.move(temporario, arquivo(id), StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporario, arquivo(id)); }
            mensagens.put(id, new Mensagem(id, texto));
            return id;
        } finally { Files.deleteIfExists(temporario); }
    }

    private synchronized Mensagem receber() {
        long agora = System.currentTimeMillis();
        for (Mensagem m : mensagens.values()) {
            if (prazoEntrega.getOrDefault(m.id, 0L) <= agora) {
                prazoEntrega.put(m.id, agora + leaseMs);
                return m;
            }
        }
        return null;
    }

    private synchronized boolean confirmar(long id) throws IOException {
        if (!mensagens.containsKey(id)) return false;
        if (!prazoEntrega.containsKey(id)) return false;
        Files.delete(arquivo(id));
        mensagens.remove(id);
        prazoEntrega.remove(id);
        return true;
    }

    private synchronized int[] estado() {
        int livres = 0;
        long agora = System.currentTimeMillis();
        for (long id : mensagens.keySet())
            if (prazoEntrega.getOrDefault(id, 0L) <= agora) livres++;
        return new int[] { mensagens.size(), livres };
    }

    private void atender(Socket socket) {
        try (socket; var entrada = new DataInputStream(socket.getInputStream());
             var saida = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(10_000);
            switch (entrada.readUTF()) {
                case "PUBLICAR" -> { long id = publicar(entrada.readUTF()); saida.writeLong(id); }
                case "RECEBER" -> {
                    Mensagem m = receber();
                    saida.writeBoolean(m != null);
                    if (m != null) { saida.writeLong(m.id); saida.writeUTF(m.texto); }
                }
                case "CONFIRMAR" -> saida.writeBoolean(confirmar(entrada.readLong()));
                case "ESTADO" -> { int[] e = estado(); saida.writeInt(e[0]); saida.writeInt(e[1]); }
                default -> throw new IOException("Operação não reconhecida");
            }
            saida.flush();
        } catch (Exception e) { System.err.println("Fila: " + e.getMessage()); }
    }

    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        Path pasta = Path.of(args.length > 1 ? args[1] : "dados_fila");
        long lease = args.length > 2 ? Long.parseLong(args[2]) : 5_000;
        FilaServidor broker = new FilaServidor(pasta, lease);
        var pool = Executors.newFixedThreadPool(8);
        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Broker de mensagens TCP:" + porta + " (ack/lease " + lease + "ms)");
            while (true) { Socket cliente = servidor.accept(); pool.submit(() -> broker.atender(cliente)); }
        } finally { pool.shutdownNow(); }
    }
}
