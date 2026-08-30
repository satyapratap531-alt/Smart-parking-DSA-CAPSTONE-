package com.parking.model;

/**
 * An axis-aligned rectangular region, defined by a center point and
 * a half-width / half-height. Every QuadTree node owns one Boundary
 * describing the area of the parking lot it is responsible for.
 */
public class Boundary {
    public final double centerX;
    public final double centerY;
    public final double halfWidth;
    public final double halfHeight;

    public Boundary(double centerX, double centerY, double halfWidth, double halfHeight) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
    }

    /** True if the given point lies inside this rectangle (inclusive of edges). */
    public boolean contains(Point p) {
        return p.x >= centerX - halfWidth && p.x <= centerX + halfWidth
                && p.y >= centerY - halfHeight && p.y <= centerY + halfHeight;
    }

    /**
     * The minimum possible distance from point p to ANY location inside
     * this rectangle. Returns 0 if p is already inside. This is the key
     * to fast nearest-neighbor search: it's a lower bound on how close
     * anything in this region could be, letting us skip whole branches
     * that can't possibly beat a candidate we already have.
     */
    public double distanceTo(Point p) {
        double dx = Math.max(0, Math.abs(p.x - centerX) - halfWidth);
        double dy = Math.max(0, Math.abs(p.y - centerY) - halfHeight);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** True if this rectangle overlaps at all with another rectangle (a query box). */
    public boolean intersects(Boundary other) {
        return !(other.centerX - other.halfWidth > centerX + halfWidth
                || other.centerX + other.halfWidth < centerX - halfWidth
                || other.centerY - other.halfHeight > centerY + halfHeight
                || other.centerY + other.halfHeight < centerY - halfHeight);
    }

    // --- The four quadrant sub-boundaries, used when a node splits ---

    public Boundary northWest() {
        return new Boundary(centerX - halfWidth / 2, centerY - halfHeight / 2, halfWidth / 2, halfHeight / 2);
    }

    public Boundary northEast() {
        return new Boundary(centerX + halfWidth / 2, centerY - halfHeight / 2, halfWidth / 2, halfHeight / 2);
    }

    public Boundary southWest() {
        return new Boundary(centerX - halfWidth / 2, centerY + halfHeight / 2, halfWidth / 2, halfHeight / 2);
    }

    public Boundary southEast() {
        return new Boundary(centerX + halfWidth / 2, centerY + halfHeight / 2, halfWidth / 2, halfHeight / 2);
    }
}