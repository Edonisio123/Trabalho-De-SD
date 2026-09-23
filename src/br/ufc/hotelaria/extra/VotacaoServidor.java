package br.ufc.hotelaria.extra;

import br.ufc.hotelaria.multicast.MulticastConfig;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.w3c.dom.Element;

/** Questão extra: votação didática; TCP unicast para comandos e UDP multicast só para notas. */
public class VotacaoServidor {
    public static final int PORTA_TCP = 5010;
    public static final String GRUPO = "230.0.0.2";
    public static final int PORTA_UDP = 5011;
    private final long encerramento;
    private final Map<Integer, Candidato> candidatos = new LinkedHashMap<>();
    private final Set<String> eleitoresQueVotaram = new HashSet<>();
    private int proximoId = 3;
    private final MulticastSocket avisos;
    private final InetAddress grupo;

    private static class Candidato {
        final int id;
        final String nome;
        int votos;
        Candidato(int id, String nome) { this.id = id; this.nome = nome; }
    }

    public VotacaoServidor(long duracaoSegundos, MulticastSocket avisos, InetAddress grupo) {
        if (duracaoSegundos < 0 || duracaoSegundos > 86_400)
            throw new IllegalArgumentException("Prazo deve estar entre 0 e 86400 segundos");
        this.encerramento = System.currentTimeMillis() + duracaoSegundos * 1000;
        this.avisos = avisos;
        this.grupo = grupo;
        candidatos.put(1, new Candidato(1, "Opção A"));
        candidatos.put(2, new Candidato(2, "Opção B"));
    }

    private boolean aberto() { return System.currentTimeMillis() < encerramento; }

    private String listaCandidatos() {
        StringBuilder b = new StringBuilder("<candidatos>");
        for (Candidato c : candidatos.values())
            b.append("<candidato id=\"").append(c.id).append("\" nome=\"")
                    .append(XmlVotacao.escapar(c.nome)).append("\"/>");
        return b.append("</candidatos>").toString();
    }

    private String resultado() {
        if (aberto()) return XmlVotacao.resposta(false,
                "Votação ainda aberta; restam " + ((encerramento - System.currentTimeMillis() + 999) / 1000)
                        + " segundos", "");
        long total = 0;
        for (Candidato c : candidatos.values()) total += c.votos;
        long maior = -1;
        int qtdComMaior = 0;
        String vencedor = "";
        StringBuilder b = new StringBuilder("<apuracao total=\"").append(total).append("\">");
        for (Candidato c : candidatos.values()) {
            double porcentagem = total == 0 ? 0 : 100.0 * c.votos / total;
            b.append("<candidato id=\"").append(c.id).append("\" nome=\"")
                    .append(XmlVotacao.escapar(c.nome)).append("\" votos=\"")
                    .append(c.votos).append("\" percentual=\"")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", porcentagem)).append("\"/>");
            if (c.votos > maior) { maior = c.votos; qtdComMaior = 1; vencedor = c.nome; }
            else if (c.votos == maior) { qtdComMaior++; }
        }
        b.append("<desfecho valor=\"").append(total == 0 ? "SEM_VOTOS" :
                qtdComMaior > 1 ? "EMPATE" : XmlVotacao.escapar(vencedor)).append("\"/>");
        b.append("</apuracao>");
        return XmlVotacao.resposta(true, "Apuração concluída", b.toString());
    }

    /** Métodos sincronizados garantem um voto por eleitor mesmo com vários clientes simultâneos. */
    private synchronized String processar(String usuario, String papel, Element req) {
        String op = req.getAttribute("op").toUpperCase();
        try {
            return switch (op) {
                case "LISTAR" -> XmlVotacao.resposta(true, aberto() ? "Votação aberta" : "Votação encerrada",
                        listaCandidatos());
                case "VOTAR" -> {
                    if (!papel.equals("ELEITOR")) yield erro("Apenas eleitores podem votar");
                    if (!aberto()) yield erro("Prazo para votar encerrado");
                    if (eleitoresQueVotaram.contains(usuario)) yield erro("Eleitor já votou");
                    int id = Integer.parseInt(req.getAttribute("candidato"));
                    Candidato c = candidatos.get(id);
                    if (c == null) yield erro("Candidato inexistente");
                    c.votos++;
                    eleitoresQueVotaram.add(usuario);
                    yield XmlVotacao.resposta(true, "Voto registrado", "");
                }
                case "ADICIONAR" -> {
                    if (!papel.equals("ADMIN")) yield erro("Permissão de administrador necessária");
                    if (!aberto()) yield erro("Não é possível adicionar após o prazo");
                    String nome = req.getAttribute("nome").trim();
                    if (nome.isEmpty() || nome.length() > 80) yield erro("Nome inválido");
                    int id = proximoId++;
                    candidatos.put(id, new Candidato(id, nome));
                    yield XmlVotacao.resposta(true, "Candidato adicionado: " + id, listaCandidatos());
                }
                case "REMOVER" -> {
                    if (!papel.equals("ADMIN")) yield erro("Permissão de administrador necessária");
                    if (!aberto()) yield erro("Não é possível remover após o prazo");
                    int id = Integer.parseInt(req.getAttribute("candidato"));
                    Candidato c = candidatos.get(id);
                    if (c == null) yield erro("Candidato inexistente");
                    if (c.votos != 0) yield erro("Candidato com votos não pode ser removido");
                    candidatos.remove(id);
                    yield XmlVotacao.resposta(true, "Candidato removido", listaCandidatos());
                }
                case "NOTA" -> {
                    if (!papel.equals("ADMIN")) yield erro("Permissão de administrador necessária");
                    String mensagem = req.getAttribute("mensagem").trim();
                    if (mensagem.isEmpty() || mensagem.length() > 1000) yield erro("Nota inválida");
                    enviarNota(mensagem);
                    yield XmlVotacao.resposta(true, "Nota informativa enviada via UDP multicast", "");
                }
                case "RESULTADO" -> resultado();
                default -> erro("Comando desconhecido: " + op);
            };
        } catch (NumberFormatException e) {
            return erro("Identificador numérico inválido");
        } catch (Exception e) {
            return erro("Erro ao executar comando: " + e.getMessage());
        }
    }

    private static String erro(String motivo) { return XmlVotacao.resposta(false, motivo, ""); }

    private void enviarNota(String mensagem) throws Exception {
        byte[] bytes = MulticastConfig.json("NOTA", mensagem, System.currentTimeMillis())
                .getBytes(StandardCharsets.UTF_8);
        DatagramPacket pacote = new DatagramPacket(bytes, bytes.length, grupo, PORTA_UDP);
        synchronized (avisos) { avisos.send(pacote); }
        System.out.println("NOTA multicast enviada: " + mensagem);
    }

    private void atender(Socket cliente) {
        try (cliente) {
            cliente.setSoTimeout(300_000);
            DataInputStream entrada = new DataInputStream(cliente.getInputStream());
            DataOutputStream saida = new DataOutputStream(cliente.getOutputStream());
            Element login = XmlVotacao.analisar(entrada.readUTF());
            String usuario = login.getAttribute("usuario");
            String senha = login.getAttribute("senha");
            String papel = usuario.equals("admin") && senha.equals("admin123") ? "ADMIN" :
                    usuario.matches("eleitor[1-9][0-9]*") && senha.equals("1234") ? "ELEITOR" : "";
            if (!login.getTagName().equals("requisicao") || !login.getAttribute("op").equals("LOGIN") || papel.isEmpty()) {
                saida.writeUTF(erro("Login inválido")); saida.flush(); return;
            }
            synchronized (this) {
                saida.writeUTF(XmlVotacao.resposta(true, "Login efetuado; papel=" + papel,
                        listaCandidatos()));
                saida.flush();
            }
            while (true) {
                String xml;
                try { xml = entrada.readUTF(); }
                catch (EOFException e) { return; }
                Element req = XmlVotacao.analisar(xml);
                if (!req.getTagName().equals("requisicao")) { saida.writeUTF(erro("Mensagem inválida")); saida.flush(); continue; }
                String resposta = processar(usuario, papel, req);
                saida.writeUTF(resposta); saida.flush();
            }
        } catch (Exception e) {
            System.err.println("Conexão de votação: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        long prazo = args.length > 0 ? Long.parseLong(args[0]) : 120;
        InetAddress grupo = InetAddress.getByName(GRUPO);
        ExecutorService clientes = Executors.newFixedThreadPool(16);
        try (ServerSocket tcp = new ServerSocket(PORTA_TCP);
             MulticastSocket multicast = new MulticastSocket()) {
            multicast.setTimeToLive(1);
            VotacaoServidor app = new VotacaoServidor(prazo, multicast, grupo);
            System.out.printf("Extra: votação TCP %d, notas UDP %s:%d, prazo %d segundos%n",
                    PORTA_TCP, GRUPO, PORTA_UDP, prazo);
            System.out.println("Credenciais demo: admin/admin123, eleitor1/1234, eleitor2/1234, ...");
            while (true) {
                Socket cliente = tcp.accept();
                clientes.submit(() -> app.atender(cliente));
            }
        } finally { clientes.shutdownNow(); }
    }
}
