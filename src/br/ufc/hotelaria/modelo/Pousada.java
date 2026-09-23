package br.ufc.hotelaria.modelo;

/** Subclasse de MeioHospedagem: pousada indica café da manhã incluso. */
public final class Pousada extends MeioHospedagem {
    private final boolean cafeDaManhaIncluso;

    public Pousada(int id, String nome, String endereco, long diariaCentavos, int totalUnidades,
                   boolean cafeDaManhaIncluso) {
        super(id, nome, endereco, diariaCentavos, totalUnidades);
        this.cafeDaManhaIncluso = cafeDaManhaIncluso;
    }
    public boolean isCafeDaManhaIncluso() { return cafeDaManhaIncluso; }
    @Override public String getTipo() { return "POUSADA"; }
    @Override public String toString() { return super.toString() + " | cafeIncluso=" + cafeDaManhaIncluso; }
}
