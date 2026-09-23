package br.ufc.hotelaria.extra;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.w3c.dom.Element;

/** Cliente interativo da questão extra. Um eleitor recebe notas UDP em thread separada. */
public class VotacaoCliente {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: VotacaoCliente HOST USUARIO SENHA");
            return;
        }
        String host = args[0];
        String usuario = args[1];
        String senha = args[2];
        try (Socket tcp = new Socket(host, VotacaoServidor.PORTA_TCP)) {
            tcp.setSoTimeout(10_000);
            DataInputStream entrada = new DataInputStream(tcp.getInputStream());
            DataOutputStream saida = new DataOutputStream(tcp.getOutputStream());
            saida.writeUTF(XmlVotacao.requisicao("LOGIN", Map.of("usuario", usuario, "senha", senha)));
            saida.flush();
            String primeira = entrada.readUTF();
            System.out.println(primeira);
            if (!XmlVotacao.analisar(primeira).getAttribute("sucesso").equals("true")) return;
            boolean eleitor = usuario.startsWith("eleitor");
            AtomicBoolean ativo = new AtomicBoolean(true);
            MulticastSocket udp = null;
            Thread escuta = null;
            InetAddress grupo = InetAddress.getByName(VotacaoServidor.GRUPO);
            try {
                if (eleitor) {
                    udp = new MulticastSocket(VotacaoServidor.PORTA_UDP);
                    udp.setSoTimeout(1000);
                    udp.joinGroup(grupo);
                    MulticastSocket receptor = udp;
                    escuta = new Thread(() -> {
                        byte[] bytes = new byte[4096];
                        while (ativo.get()) {
                            try {
                                DatagramPacket pacote = new DatagramPacket(bytes, bytes.length);
                                receptor.receive(pacote);
                                System.out.println("[NOTA UDP] " + new String(pacote.getData(),
                                        pacote.getOffset(), pacote.getLength(), StandardCharsets.UTF_8));
                            } catch (SocketTimeoutException ignored) {
                            } catch (Exception e) {
                                if (ativo.get()) System.err.println("UDP: " + e.getMessage());
                                break;
                            }
                        }
                    }, "notas-multicast");
                    escuta.start();
                }
                System.out.println(eleitor
                        ? "Comandos: listar | votar ID | resultado | sair"
                        : "Comandos: listar | adicionar NOME | remover ID | nota TEXTO | resultado | sair");
                BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String linha;
                while ((linha = teclado.readLine()) != null && !linha.equalsIgnoreCase("sair")) {
                    String[] partes = linha.trim().split("\\s+", 2);
                    String op = partes[0].toLowerCase();
                    String valor = partes.length == 2 ? partes[1] : "";
                    Map<String, String> parametros = new LinkedHashMap<>();
                    String comando;
                    switch (op) {
                        case "listar" -> comando = "LISTAR";
                        case "resultado" -> comando = "RESULTADO";
                        case "votar" -> { comando = "VOTAR"; parametros.put("candidato", valor); }
                        case "adicionar" -> { comando = "ADICIONAR"; parametros.put("nome", valor); }
                        case "remover" -> { comando = "REMOVER"; parametros.put("candidato", valor); }
                        case "nota" -> { comando = "NOTA"; parametros.put("mensagem", valor); }
                        default -> { System.out.println("Comando inválido"); continue; }
                    }
                    saida.writeUTF(XmlVotacao.requisicao(comando, parametros));
                    saida.flush();
                    String resposta = entrada.readUTF();
                    Element xml = XmlVotacao.analisar(resposta);
                    System.out.println("Servidor: " + xml.getAttribute("mensagem"));
                    System.out.println(resposta); // inclui candidatos, votos, percentuais e desfecho
                }
            } finally {
                ativo.set(false);
                if (escuta != null) escuta.join(2000);
                if (udp != null) { udp.leaveGroup(grupo); udp.close(); }
                System.out.println("Sessão encerrada.");
            }
        }
    }
}
