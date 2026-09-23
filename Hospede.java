package br.ufc.hotelaria.modelo;

/** POJO de uma pessoa hóspede: não armazena documento ou senha para a demonstração. */
public final class Hospede {
    private final int id;
    private final String nome;
    public Hospede(int id, String nome) {
        if (id <= 0 || nome == null || nome.isBlank())
            throw new IllegalArgumentException("ID e nome do hóspede são obrigatórios");
        this.id = id;
        this.nome = nome;
    }
    public int getId() { return id; }
    public String getNome() { return nome; }
    @Override public String toString() { return "Hospede{id=" + id + ", nome='" + nome + "'}"; }
}
