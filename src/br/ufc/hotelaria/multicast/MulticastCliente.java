package br.ufc.hotelaria.multicast;

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
import java.util.concurrent.atomic.AtomicBoolean;

/** Ex. 4: thread de recepção multicast + thread de interação com usuário. */
public class MulticastCliente {
    @SuppressWarnings("deprecation") // joinGroup/leaveGroup sem interface: mais simples no laboratório local
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        String usuario = args.length > 1 ? args[1] : "aluno";
        String senha = args.length > 2 ? args[2] : "1234";
        try (Socket login = new Socket(host, MulticastConfig.PORTA_AUTENTICACAO)) {
            login.setSoTimeout(10_000);
            DataOutputStream out = new DataOutputStream(login.getOutputStream());
            out.writeUTF(usuario); out.writeUTF(senha); out.flush();
            DataInputStream in = new DataInputStream(login.getInputStream());
            boolean ok = in.readBoolean();
            System.out.println("Resposta TCP: " + in.readUTF());
            if (!ok) return;
        }

        InetAddress grupo = InetAddress.getByName(MulticastConfig.GRUPO);
        AtomicBoolean ativo = new AtomicBoolean(true);
        try (MulticastSocket recepcao = new MulticastSocket(MulticastConfig.PORTA_UDP)) {
            recepcao.setSoTimeout(1000);
            recepcao.joinGroup(grupo);
            Thread escuta = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (ativo.get()) {
                    try {
                        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                        recepcao.receive(pacote);
                        System.out.println("[MULTICAST] " + new String(pacote.getData(),
                                pacote.getOffset(), pacote.getLength(), StandardCharsets.UTF_8));
                    } catch (SocketTimeoutException ignored) {
                        // timeout curto permite observar o encerramento da thread
                    } catch (Exception e) {
                        if (ativo.get()) System.err.println("Recepção: " + e.getMessage());
                        break;
                    }
                }
            }, "escuta-multicast");
            escuta.start();
            Thread interacao = new Thread(() -> {
                try {
                    BufferedReader teclado = new BufferedReader(new InputStreamReader(
                            System.in, StandardCharsets.UTF_8));
                    System.out.println("Cliente ouvindo o grupo. Digite 'sair' para leaveGroup:");
                    String linha;
                    while ((linha = teclado.readLine()) != null) {
                        if (linha.equalsIgnoreCase("sair")) break;
                        System.out.println("Aguardando avisos. Para encerrar, digite 'sair'.");
                    }
                } catch (Exception e) {
                    System.err.println("Teclado: " + e.getMessage());
                } finally { ativo.set(false); }
            }, "interacao-usuario");
            interacao.start();
            interacao.join();
            ativo.set(false);
            escuta.join(2000);
            recepcao.leaveGroup(grupo);
            System.out.println("Cliente saiu do grupo multicast (leaveGroup).");
        }
    }
}
