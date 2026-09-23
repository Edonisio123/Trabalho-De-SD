package br.ufc.hotelaria.contrato;

import br.ufc.hotelaria.modelo.Reserva;

/** Interface exigida: contrato para registrar, cancelar e efetivar reservas. */
public interface Reservas {
    Reserva registrarReserva(Reserva reserva);
    Reserva cancelarReserva(int reservaId);
    Reserva efetivarReserva(int reservaId);
}
