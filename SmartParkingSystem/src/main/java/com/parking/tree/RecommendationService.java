package com.parking.tree;

import com.parking.model.ParkingSlot;
import com.parking.model.Point;
import com.parking.model.Zone;

import java.util.List;

/**
 * Implements the scoring function from the project spec:
 *
 *     Score = w1 * distance + w2 * congestion + w3 * preferenceMismatch
 *
 * Lower score is better. Distance comes straight from geometry.
 * Congestion is how full the candidate slot's zone already is (0..1) —
 * a slot in a 95%-full zone gets penalized even if it's technically
 * vacant, because the driver would be fighting traffic to reach it.
 * Preference mismatch is a flat penalty when the slot's type doesn't
 * match what the driver asked for (e.g. wanted EV, slot is Regular).
 */
public class RecommendationService {

    private final ParkingSystem system;
    private final PointLocationService locator;

    // Tunable weights — bump congestionWeight up if you want the system
    // to steer drivers away from crowded zones more aggressively.
    private double distanceWeight = 1.0;
    private double congestionWeight = 25.0;
    private double preferenceMismatchPenalty = 50.0;

    public RecommendationService(ParkingSystem system, PointLocationService locator) {
        this.system = system;
        this.locator = locator;
    }

    public void setWeights(double distanceWeight, double congestionWeight, double preferenceMismatchPenalty) {
        this.distanceWeight = distanceWeight;
        this.congestionWeight = congestionWeight;
        this.preferenceMismatchPenalty = preferenceMismatchPenalty;
    }

    /**
     * Fraction of slots occupied within the given zone, scoped to one
     * floor (0.0 = empty, 1.0 = completely full). Returns 0 if the zone
     * has no known slots on that floor yet.
     */
    public double congestionOf(Zone zone, int floor) {
        List<ParkingSlot> inZone = system.rangeQuery(floor, zone.getArea());
        if (inZone.isEmpty()) return 0.0;

        long occupiedCount = inZone.stream().filter(s -> !s.isVacant()).count();
        return (double) occupiedCount / inZone.size();
    }

    /**
     * Recommends the best vacant slot across all floors for a driver at
     * `from`, optionally preferring a specific slot type (pass null for
     * no preference). Returns null if there are no vacant slots at all.
     */
    public ParkingSlot recommend(Point from, ParkingSlot.Type preferredType) {
        ParkingSlot best = null;
        double bestScore = Double.MAX_VALUE;

        for (int floor : system.floorNumbers()) {
            for (ParkingSlot candidate : system.allSlotsOnFloor(floor)) {
                if (!candidate.isVacant()) continue;

                double score = scoreOf(candidate, from, preferredType);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** Exposed separately so you can show the score breakdown in a UI later. */
    public double scoreOf(ParkingSlot candidate, Point from, ParkingSlot.Type preferredType) {
        double distance = from.distanceTo(candidate.getLocation());

        Zone zone = locator.locate(candidate.getLocation());
        double congestion = (zone != null) ? congestionOf(zone, candidate.getFloor()) : 0.0;

        double preferencePenalty = (preferredType != null && candidate.getType() != preferredType)
                ? preferenceMismatchPenalty
                : 0.0;

        return distanceWeight * distance
                + congestionWeight * congestion
                + preferencePenalty;
    }
}