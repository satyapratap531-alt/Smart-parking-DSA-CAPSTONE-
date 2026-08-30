package com.parking.tree;

import com.parking.model.Point;
import com.parking.model.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers "which zone does this coordinate belong to?" — e.g. a driver
 * enters at gate coordinate (x, y) and the system needs to know which
 * named zone/section they're in.
 *
 * Implementation note: for a typical parking facility, the number of
 * zones is small (a handful to a few dozen — "Zone A", "EV Section",
 * "Visitor Row", etc.), so a linear scan over zones is fast in practice
 * and simple to reason about. If this ever needs to scale to hundreds
 * of zones, the standard upgrade path is a slab decomposition or a
 * trapezoidal map for true O(log n) point location — flag it if you
 * need that later.
 */
public class PointLocationService {

    private final List<Zone> zones = new ArrayList<>();

    public void addZone(Zone zone) {
        zones.add(zone);
    }

    /**
     * Returns the zone containing point p, or null if the point falls
     * outside every defined zone. If zones happen to overlap, the first
     * match (in insertion order) wins.
     */
    public Zone locate(Point p) {
        for (Zone zone : zones) {
            if (zone.containsPoint(p)) {
                return zone;
            }
        }
        return null;
    }

    public List<Zone> getZones() {
        return zones;
    }
}