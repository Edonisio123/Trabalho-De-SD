package br.ufc.hotelaria.modelo;

/** Superclasse "Meios de Hospedagem". Cada unidade é um quarto/suíte disponível. */
public abstract class MeioHospedagem {
    private final int id;
    private final String nome;
    private final String endereco;
    private final long diariaCentavos;
    private final int totalUnidades;

    protected MeioHospedagem(int id, String nome, String endereco, long diariaCentavos, int totalUnidades) {
        if (id <= 0 || diariaCentavos <= 0 || totalUnidades <= 0)
            throw new IllegalArgumentException("ID, diária e capacidade devem ser positivos");
        if (nome == null || nome.isBlank() || endereco == null || endereco.isBlank())
            throw new IllegalArgumentException("Nome e endereço são obrigatórios");
        this.id = id;
        this.nome = nome;
        this.endereco = endereco;
        this.diariaCentavos = diariaCentavos;
        this.totalUnidades = totalUnidades;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getEndereco() { return endereco; }
    public long getDiariaCentavos() { return diariaCentavos; }
    public int getTotalUnidades() { return totalUnidades; }
    public abstract String getTipo();

    @Override public String toString() {
        return "%s{id=%d, nome='%s', endereco='%s', diária=R$ %d,%02d, unidades=%d}"
                .formatted(getTipo(), id, nome, endereco, diariaCentavos / 100,
                        diariaCentavos % 100, totalUnidades);
    }
}
