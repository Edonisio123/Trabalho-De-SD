package br.ufc.hotelaria.multicast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ex. 4: login TCP concorrente e notificações UDP multicast concorrentes. */
public class MulticastServidor {
    public static void main(String[] args) throws Exception {
        InetAddress grupo = InetAddress.getByName(MulticastConfig.GRUPO);
        AtomicBoolean ativo = new AtomicBoolean(true);
        ExecutorService autenticacoes = Executors.newFixedThreadPool(8);
        ScheduledExecutorService automaticas = Executors.newScheduledThreadPool(2);
        try (ServerSocket servidorTcp = new ServerSocket(MulticastConfig.PORTA_AUTENTICACAO);
             MulticastSocket emissorUdp = new MulticastSocket()) {
            emissorUdp.setTimeToLive(1); // restrito à rede local

            Thread aceitar = new Thread(() -> {
                while (ativo.get()) {
                    try {
                        Socket cliente = servidorTcp.accept();
                        autenticacoes.submit(() -> autenticar(cliente));
                    } catch (Exception e) {
                        if (ativo.get()) System.err.println("Autenticação: " + e.getMessage());
                    }
                }
            }, "aceitador-tcp");
            aceitar.start();
            automaticas.scheduleAtFixedRate(() -> {
                if (ativo.get()) enviar(emissorUdp, grupo, "ATUALIZACAO",
                        "Servidor de reservas ativo", ativo);
            }, 5, 15, TimeUnit.SECONDS);
            System.out.println("Autenticação: TCP " + MulticastConfig.PORTA_AUTENTICACAO);
            System.out.println("Grupo UDP: " + MulticastConfig.GRUPO + ":" + MulticastConfig.PORTA_UDP);
            System.out.println("Login de demonstração: aluno / 1234");
            System.out.println("Digite TIPO|mensagem (NOTIFICACAO, ALERTA, ATUALIZACAO), ou sair:");
            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String linha;
            while ((linha = teclado.readLine()) != null && !linha.equalsIgnoreCase("sair")) {
                String[] partes = linha.split("\\|", 2);
                if (partes.length != 2 ||
                        !(partes[0].equals("NOTIFICACAO") || partes[0].equals("ALERTA") || partes[0].equals("ATUALIZACAO"))) {
                    System.out.println("Formato esperado: ALERTA|Disponibilidade de suites atualizada");
                    continue;
                }
                // Outro produtor concorrente à thread da atualização automática.
                automaticas.execute(() -> enviar(emissorUdp, grupo, partes[0], partes[1], ativo));
            }
            ativo.set(false);
            servidorTcp.close();
            aceitar.join(2000);
        } finally {
            automaticas.shutdownNow();
            autenticacoes.shutdownNow();
        }
    }

    private static void autenticar(Socket cliente) {
        try (cliente) {
            cliente.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(cliente.getInputStream());
            DataOutputStream out = new DataOutputStream(cliente.getOutputStream());
            String usuario = in.readUTF();
            String senha = in.readUTF();
            boolean ok = "aluno".equals(usuario) && "1234".equals(senha);
            out.writeBoolean(ok);
            out.writeUTF(ok ? "Acesso autorizado ao grupo" : "Usuário/senha incorretos");
            out.flush();
            System.out.println("Autenticação TCP de " + usuario + ": " + (ok ? "OK" : "NEGADA"));
        } catch (Exception e) {
            System.err.println("Falha ao autenticar: " + e.getMessage());
        }
    }

    private static void enviar(MulticastSocket socket, InetAddress grupo,
                               String tipo, String mensagem, AtomicBoolean ativo) {
        if (!ativo.get()) return;
        try {
            byte[] bytes = MulticastConfig.json(tipo, mensagem, System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8);
            DatagramPacket pacote = new DatagramPacket(bytes, bytes.length, grupo, MulticastConfig.PORTA_UDP);
            synchronized (socket) { socket.send(pacote); }
            System.out.println("UDP enviado: " + new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Erro ao enviar multicast: " + e.getMessage());
        }
    }
}
