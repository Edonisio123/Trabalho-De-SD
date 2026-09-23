package br.ufc.hotelaria.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agregação: reúne meios de hospedagem sem controlar o ciclo de vida deles. */
public final class ComplexoTuristico {
    private final String nome;
    private final Map<Integer, MeioHospedagem> estabelecimentos = new LinkedHashMap<>();

    public ComplexoTuristico(String nome) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome do complexo obrigatório");
        this.nome = nome;
    }
    public String getNome() { return nome; }
    public synchronized void agregar(MeioHospedagem meio) {
        if (meio == null || estabelecimentos.containsKey(meio.getId()))
            throw new IllegalArgumentException("Hospedagem nula ou ID repetido");
        estabelecimentos.put(meio.getId(), meio); // mantém a referência, não cria nem destrói o objeto
    }
    public synchronized MeioHospedagem buscar(int id) {
        MeioHospedagem meio = estabelecimentos.get(id);
        if (meio == null) throw new IllegalArgumentException("Meio de hospedagem não encontrado: " + id);
        return meio;
    }
    public synchronized List<MeioHospedagem> listar() { return new ArrayList<>(estabelecimentos.values()); }
    public synchronized MeioHospedagem desagregar(int id) {
        MeioHospedagem meio = estabelecimentos.remove(id);
        if (meio == null) throw new IllegalArgumentException("Meio de hospedagem não encontrado: " + id);
        return meio;
    }
}
