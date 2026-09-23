package br.ufc.hotelaria.servico;

import br.ufc.hotelaria.modelo.ComplexoTuristico;
import br.ufc.hotelaria.modelo.MeioHospedagem;
import java.util.List;

/** Serviço 1: mantém o catálogo do complexo e usa a agregação. */
public final class HospedagemService {
    private final ComplexoTuristico complexo;
    public HospedagemService(ComplexoTuristico complexo) { this.complexo = java.util.Objects.requireNonNull(complexo); }
    public void cadastrar(MeioHospedagem meio) { complexo.agregar(meio); }
    public MeioHospedagem consultar(int id) { return complexo.buscar(id); }
    public List<MeioHospedagem> listar() { return complexo.listar(); }
    public String nomeComplexo() { return complexo.getNome(); }
}
