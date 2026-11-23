package br.ufc.hotelaria.fila;

/** Processo independente: recebe um evento por vez; confirma somente após processar. */
public final class FilaConsumidor {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        boolean uma = java.util.Arrays.asList(args).contains("--uma");
        boolean semAck = java.util.Arrays.asList(args).contains("--sem-ack");
        System.out.println("Consumidor iniciado. Broker: " + host + ":" + porta);
        while (true) {
            try {
                FilaCliente.Mensagem m = FilaCliente.receber(host, porta);
                if (m == null) { if (uma || semAck) return; Thread.sleep(500); continue; }
                System.out.println("PROCESSADO id=" + m.id() + " evento=" + m.texto());
                if (semAck) { System.out.println("SIMULACAO_FALHA sem confirmação"); return; }
                if (!FilaCliente.confirmar(host, porta, m.id()))
                    System.err.println("Confirmação não recebida: evento pode ser reentregue");
                else System.out.println("CONFIRMADO id=" + m.id());
                if (uma) return;
            } catch (java.io.IOException e) {
                System.err.println("Broker indisponível; tentando reconectar: " + e.getMessage());
                Thread.sleep(1_000);
            }
        }
    }
}
