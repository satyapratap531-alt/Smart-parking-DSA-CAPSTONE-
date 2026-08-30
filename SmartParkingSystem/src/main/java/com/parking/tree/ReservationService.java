package com.parking.tree;

import com.parking.model.ParkingSlot;
import com.parking.model.Reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the lifecycle of reservations on top of a ParkingSystem:
 *
 *   reserve()        VACANT   -> RESERVED   (driver books ahead)
 *   confirmArrival()  RESERVED -> OCCUPIED   (driver actually parks)
 *   cancel()          RESERVED -> VACANT     (driver cancels, or a car leaves)
 *   releaseExpired()  RESERVED -> VACANT     (driver never showed up)
 *
 * The QuadTree itself doesn't know anything about reservations — it just
 * sees ParkingSlot.Status. This service is purely the bookkeeping layer
 * on top: who booked what, and when it expires.
 */
public class ReservationService {

    private final ParkingSystem system;
    private final AtomicInteger nextReservationId = new AtomicInteger(1);

    private final Map<Integer, Reservation> reservationsById = new HashMap<>();
    private final Map<Integer, Integer> reservationIdBySlotId = new HashMap<>();

    public ReservationService(ParkingSystem system) {
        this.system = system;
    }

    /**
     * Reserve a specific slot for a driver. Returns null if the slot
     * doesn't exist or isn't currently vacant (already reserved or
     * occupied by someone else).
     */
    public Reservation reserve(int slotId, String vehiclePlate, Duration holdDuration) {
        ParkingSlot slot = system.findSlotById(slotId);
        if (slot == null || !slot.isVacant()) {
            return null;
        }

        slot.reserve();

        Instant now = Instant.now();
        Reservation reservation = new Reservation(
                nextReservationId.getAndIncrement(),
                slotId,
                vehiclePlate,
                now,
                now.plus(holdDuration)
        );

        reservationsById.put(reservation.getReservationId(), reservation);
        reservationIdBySlotId.put(slotId, reservation.getReservationId());
        return reservation;
    }

    /**
     * Driver has physically arrived and parked. Converts a RESERVED
     * slot to OCCUPIED. Returns false if there's no active reservation
     * for that slot (e.g. it already expired).
     */
    public boolean confirmArrival(int slotId) {
        Integer reservationId = reservationIdBySlotId.get(slotId);
        if (reservationId == null) return false;

        ParkingSlot slot = system.findSlotById(slotId);
        if (slot == null || !slot.isReserved()) return false;

        slot.occupy();
        reservationsById.remove(reservationId);
        reservationIdBySlotId.remove(slotId);
        return true;
    }

    /** Driver cancels ahead of time, freeing the slot back up. */
    public boolean cancel(int reservationId) {
        Reservation reservation = reservationsById.get(reservationId);
        if (reservation == null) return false;

        ParkingSlot slot = system.findSlotById(reservation.getSlotId());
        if (slot != null && slot.isReserved()) {
            slot.vacate();
        }

        reservationsById.remove(reservationId);
        reservationIdBySlotId.remove(reservation.getSlotId());
        return true;
    }

    /**
     * Sweep all active reservations and release any that have expired
     * without the driver confirming arrival. Call this periodically
     * (e.g. every minute in a real system, or once per "tick" in a
     * simulation).
     */
    public List<Reservation> releaseExpired(Instant now) {
        List<Reservation> expired = new ArrayList<>();

        for (Reservation r : new ArrayList<>(reservationsById.values())) {
            if (r.isExpired(now)) {
                ParkingSlot slot = system.findSlotById(r.getSlotId());
                if (slot != null && slot.isReserved()) {
                    slot.vacate();
                }
                reservationsById.remove(r.getReservationId());
                reservationIdBySlotId.remove(r.getSlotId());
                expired.add(r);
            }
        }
        return expired;
    }

    public Reservation getReservation(int reservationId) {
        return reservationsById.get(reservationId);
    }

    public int activeReservationCount() {
        return reservationsById.size();
    }
}