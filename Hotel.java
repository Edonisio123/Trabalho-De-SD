package br.ufc.hotelaria.modelo;

/** Subclasse de MeioHospedagem: hotel possui classificação em estrelas. */
public final class Hotel extends MeioHospedagem {
    private final int estrelas;

    public Hotel(int id, String nome, String endereco, long diariaCentavos, int totalUnidades, int estrelas) {
        super(id, nome, endereco, diariaCentavos, totalUnidades);
        if (estrelas < 1 || estrelas > 5) throw new IllegalArgumentException("Estrelas: 1 a 5");
        this.estrelas = estrelas;
    }
    public int getEstrelas() { return estrelas; }
    @Override public String getTipo() { return "HOTEL"; }
    @Override public String toString() { return super.toString() + " | estrelas=" + estrelas; }
}
