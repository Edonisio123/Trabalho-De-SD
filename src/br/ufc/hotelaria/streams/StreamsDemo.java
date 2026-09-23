package br.ufc.hotelaria.streams;

import br.ufc.hotelaria.modelo.Hospede;
import br.ufc.hotelaria.modelo.Reserva;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;

/** Exs. 1–2: demonstra System.out, System.in, arquivo e envio/recepção TCP. */
public final class StreamsDemo {
    private static Reserva[] exemplo() {
        return new Reserva[] {
            new Reserva(101, 1, new Hospede(11, "Ana Lima"), LocalDate.of(2027, 1, 10),
                    LocalDate.of(2027, 1, 12), 1),
            new Reserva(102, 3, new Hospede(12, "Joao Silva"), LocalDate.of(2027, 1, 12),
                    LocalDate.of(2027, 1, 15), 2),
            new Reserva(103, 2, new Hospede(13, "Maria Costa"), LocalDate.of(2027, 2, 1),
                    LocalDate.of(2027, 2, 2), 1)
        };
    }
    private static void enviar(OutputStream destino) throws IOException {
        Reserva[] reservas = exemplo();
        new MeioHospedagemOutputStream(destino, reservas, reservas.length).enviar();
    }
    private static void ler(InputStream origem) throws IOException {
        for (Reserva reserva : new MeioHospedagemInputStream(origem).lerReservas())
            System.out.println(reserva);
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 1) { ajuda(); return; }
        switch (args[0]) {
            case "escrever-console" -> {
                System.err.println("Bytes binários em System.out; redirecione a saída para um .bin");
                enviar(System.out);
            }
            case "ler-console" -> ler(System.in);
            case "escrever-arquivo" -> {
                if (args.length < 2) { ajuda(); return; }
                try (FileOutputStream arquivo = new FileOutputStream(args[1])) { enviar(arquivo); }
                System.out.println("Arquivo gravado: " + args[1]);
            }
            case "ler-arquivo" -> {
                if (args.length < 2) { ajuda(); return; }
                try (FileInputStream arquivo = new FileInputStream(args[1])) { ler(arquivo); }
            }
            case "servidor-tcp" -> {
                int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5001;
                try (ServerSocket servidor = new ServerSocket(porta)) {
                    System.out.println("Streams: servidor TCP na porta " + porta);
                    try (Socket cliente = servidor.accept()) { ler(cliente.getInputStream()); }
                }
            }
            case "cliente-tcp" -> {
                if (args.length < 2) { ajuda(); return; }
                int porta = args.length > 2 ? Integer.parseInt(args[2]) : 5001;
                try (Socket socket = new Socket(args[1], porta)) { enviar(socket.getOutputStream()); }
                System.out.println("Lote de reservas enviado por TCP.");
            }
            default -> ajuda();
        }
    }
    private static void ajuda() {
        System.out.println("StreamsDemo escrever-console | ler-console | escrever-arquivo CAMINHO "
                + "| ler-arquivo CAMINHO | servidor-tcp [PORTA] | cliente-tcp HOST [PORTA]");
    }
}
