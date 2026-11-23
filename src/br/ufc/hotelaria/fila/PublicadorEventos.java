package br.ufc.hotelaria.fila;

import br.ufc.hotelaria.modelo.Reserva;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Outbox local: registra o evento em disco e reenvia ao broker quando ele voltar. */
public final class PublicadorEventos implements AutoCloseable {
    private final String host;
    private final int porta;
    private final Path outbox;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "publicador-eventos"); t.setDaemon(true); return t;
    });

    public PublicadorEventos(String host, int porta, Path outbox) throws IOException {
        this.host = host;
        this.porta = porta;
        this.outbox = outbox;
        Files.createDirectories(outbox);
        executor.scheduleWithFixedDelay(this::enviarPendentes, 0, 1, TimeUnit.SECONDS);
    }

    public synchronized void publicar(String tipo, Reserva reserva) {
        String texto = tipo + "|reserva=" + reserva.getId() + "|meio=" + reserva.getMeioHospedagemId()
                + "|hospede=" + reserva.getHospede().getId() + "|status=" + reserva.getStatus()
                + "|entrada=" + reserva.getEntrada() + "|saida=" + reserva.getSaida();
        Path temporario = null;
        try {
            temporario = Files.createTempFile(outbox, ".outbox-", ".tmp");
            Files.writeString(temporario, texto, StandardCharsets.UTF_8);
            Path finalArquivo = outbox.resolve(String.format("%019d-%s.evt",
                    System.currentTimeMillis(), UUID.randomUUID()));
            try { Files.move(temporario, finalArquivo, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporario, finalArquivo); }
            executor.execute(this::enviarPendentes);
        } catch (IOException e) {
            System.err.println("ERRO: evento não persistido na outbox: " + e.getMessage());
        } finally {
            if (temporario != null) try { Files.deleteIfExists(temporario); } catch (IOException ignored) {}
        }
    }

    private synchronized void enviarPendentes() {
        try (var stream = Files.list(outbox)) {
            for (Path evento : stream.filter(p -> p.getFileName().toString().endsWith(".evt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                try {
                    String texto = Files.readString(evento, StandardCharsets.UTF_8);
                    FilaCliente.publicar(host, porta, texto);
                    Files.delete(evento);
                } catch (IOException e) {
                    return; // broker indisponível: mantém arquivo para retentativa
                }
            }
        } catch (IOException e) { System.err.println("Erro ao verificar outbox: " + e.getMessage()); }
    }

    @Override public void close() { executor.shutdownNow(); }
}
