package br.ufc.hotelaria.modelo;

/** Subclasse de MeioHospedagem: motel indica garagem privativa. */
public final class Motel extends MeioHospedagem {
    private final boolean garagemPrivativa;

    public Motel(int id, String nome, String endereco, long diariaCentavos, int totalUnidades,
                 boolean garagemPrivativa) {
        super(id, nome, endereco, diariaCentavos, totalUnidades);
        this.garagemPrivativa = garagemPrivativa;
    }
    public boolean isGaragemPrivativa() { return garagemPrivativa; }
    @Override public String getTipo() { return "MOTEL"; }
    @Override public String toString() { return super.toString() + " | garagemPrivativa=" + garagemPrivativa; }
}
