package br.ufc.hotelaria.fila;

/** Ferramenta de diagnóstico para demonstrar a fila sem depender da interface RPC. */
public final class FilaPublicador {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: FilaPublicador HOST PORTA ESTADO|TEXTO_DO_EVENTO"); return;
        }
        String host = args[0]; int porta = Integer.parseInt(args[1]);
        if (args[2].equalsIgnoreCase("ESTADO")) {
            var e = FilaCliente.estado(host, porta);
            System.out.println("PENDENTES=" + e.pendentes() + " DISPONIVEIS=" + e.disponiveis());
        } else {
            String texto = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            System.out.println("PUBLICADO id=" + FilaCliente.publicar(host, porta, texto));
        }
    }
}
