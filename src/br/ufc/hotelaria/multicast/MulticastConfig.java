package br.ufc.hotelaria.multicast;

/** Portas separadas: TCP de autenticação; UDP multicast de mensagens. */
public final class MulticastConfig {
    public static final String GRUPO = "230.0.0.1";
    public static final int PORTA_UDP = 5004;
    public static final int PORTA_AUTENTICACAO = 5003;
    private MulticastConfig() {}

    public static String json(String tipo, String mensagem, long timestamp) {
        return "{\"tipo\":\"" + escapar(tipo) + "\",\"mensagem\":\""
                + escapar(mensagem) + "\",\"timestamp\":" + timestamp + "}";
    }

    private static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
