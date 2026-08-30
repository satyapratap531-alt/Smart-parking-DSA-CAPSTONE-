package com.parking.tree;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the whole facility: one independent QuadTree per floor.
 *
 * Why one tree per floor instead of a single 3D tree (an Octree)?
 * In real parking structures, floors are physically separate — a
 * driver on floor 2 can't park on floor 1's coordinates. Keeping
 * floors as separate 2D trees keeps each tree smaller (faster
 * subdivision, faster queries) and makes "find me a spot on floor 3
 * specifically" a trivial lookup instead of a filtered 3D search.
 */
public class ParkingSystem {

    private final Map<Integer, QuadTree> floors = new HashMap<>();
    private final Boundary floorBoundary; // same footprint assumed for every floor

    public ParkingSystem(Boundary floorBoundary) {
        this.floorBoundary = floorBoundary;
    }

    /** Registers a floor so it can accept slots. Safe to call multiple times. */
    private QuadTree getOrCreateFloor(int floorNumber) {
        return floors.computeIfAbsent(floorNumber, f -> new QuadTree(floorBoundary));
    }

    public boolean insertSlot(ParkingSlot slot) {
        QuadTree floorTree = getOrCreateFloor(slot.getFloor());
        return floorTree.insert(slot);
    }

    public boolean removeSlot(ParkingSlot slot) {
        QuadTree floorTree = floors.get(slot.getFloor());
        return floorTree != null && floorTree.remove(slot);
    }

    /** Range query scoped to one floor. */
    public List<ParkingSlot> rangeQuery(int floorNumber, Boundary queryBox) {
        QuadTree floorTree = floors.get(floorNumber);
        if (floorTree == null) return new ArrayList<>();
        return floorTree.rangeQuery(queryBox);
    }

    /** Range query across every floor at once. */
    public List<ParkingSlot> rangeQueryAllFloors(Boundary queryBox) {
        List<ParkingSlot> results = new ArrayList<>();
        for (QuadTree floorTree : floors.values()) {
            results.addAll(floorTree.rangeQuery(queryBox));
        }
        return results;
    }

    /** Nearest vacant slot on a specific floor. */
    public ParkingSlot findNearestVacant(int floorNumber, Point from) {
        QuadTree floorTree = floors.get(floorNumber);
        if (floorTree == null) return null;
        return floorTree.findNearestVacant(from);
    }

    /** Nearest vacant slot on a specific floor, using the fast O(log n)-average search. */
    public ParkingSlot findNearestVacantFast(int floorNumber, Point from) {
        QuadTree floorTree = floors.get(floorNumber);
        if (floorTree == null) return null;
        return floorTree.findNearestVacantFast(from);
    }

    /**
     * Nearest vacant slot across ALL floors — compares the best candidate
     * from each floor's tree and returns the overall closest.
     * (Distance here is purely 2D per-floor; a real system might add a
     * fixed "floor change penalty" to the score — that fits naturally
     * into the scoring function we'll build next.)
     */
    public ParkingSlot findNearestVacantAnyFloor(Point from) {
        ParkingSlot best = null;
        double bestDist = Double.MAX_VALUE;

        for (QuadTree floorTree : floors.values()) {
            ParkingSlot candidate = floorTree.findNearestVacant(from);
            if (candidate == null) continue;
            double d = from.distanceTo(candidate.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return best;
    }

    /** All slots on one floor (occupied and vacant). */
    public List<ParkingSlot> allSlotsOnFloor(int floorNumber) {
        QuadTree floorTree = floors.get(floorNumber);
        return floorTree == null ? new ArrayList<>() : floorTree.allSlots();
    }

    /**
     * Find a slot by id anywhere in the facility, regardless of floor.
     * Used by ReservationService, which only has a slot id on hand
     * (e.g. from a booking request) and needs the live object.
     */
    public ParkingSlot findSlotById(int id) {
        for (QuadTree floorTree : floors.values()) {
            ParkingSlot found = floorTree.findById(id);
            if (found != null) return found;
        }
        return null;
    }

    public int totalSlots() {
        int total = 0;
        for (QuadTree floorTree : floors.values()) {
            total += floorTree.size();
        }
        return total;
    }

    public java.util.Set<Integer> floorNumbers() {
        return floors.keySet();
    }
}