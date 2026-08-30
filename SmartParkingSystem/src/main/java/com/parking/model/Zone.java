package com.parking.model;

/**
 * A named region of the parking lot — e.g. "Zone A - EV Charging",
 * "Zone B - Visitor Parking". Used by PointLocationService to answer
 * "which zone is this coordinate in?"
 */
public class Zone {
    private final String name;
    private final Boundary area;

    public Zone(String name, Boundary area) {
        this.name = name;
        this.area = area;
    }

    public String getName() { return name; }
    public Boundary getArea() { return area; }

    public boolean containsPoint(Point p) {
        return area.contains(p);
    }
}