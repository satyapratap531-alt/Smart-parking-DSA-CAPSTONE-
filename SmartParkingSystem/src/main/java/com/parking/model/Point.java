package com.parking.model;

/**
 * A simple 2D coordinate, used both for parking slot positions
 * and for a driver's current location when searching.
 */
public class Point {
    public final double x;
    public final double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Euclidean distance to another point — used for nearest-slot ranking. */
    public double distanceTo(Point other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }
}