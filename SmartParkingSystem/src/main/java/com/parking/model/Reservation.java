package com.parking.model;

import java.time.Instant;

/**
 * A record of a slot being held for a specific driver ahead of arrival.
 * Reservations expire automatically if the driver doesn't show up in
 * time, freeing the slot back up for others.
 */
public class Reservation {
    private final int reservationId;
    private final int slotId;
    private final String vehiclePlate;
    private final Instant reservedAt;
    private final Instant expiresAt;

    public Reservation(int reservationId, int slotId, String vehiclePlate,
                       Instant reservedAt, Instant expiresAt) {
        this.reservationId = reservationId;
        this.slotId = slotId;
        this.vehiclePlate = vehiclePlate;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
    }

    public int getReservationId() { return reservationId; }
    public int getSlotId() { return slotId; }
    public String getVehiclePlate() { return vehiclePlate; }
    public Instant getReservedAt() { return reservedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return String.format("Reservation#%d [slot=%d, plate=%s, expires=%s]",
                reservationId, slotId, vehiclePlate, expiresAt);
    }
}