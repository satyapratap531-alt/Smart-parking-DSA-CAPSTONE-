package com.parking.model;

/**
 * A single parking space. This is the object actually stored inside
 * QuadTree leaf nodes.
 */
public class ParkingSlot {

    public enum Type { REGULAR, EV, HANDICAPPED }

    /**
     * VACANT     - free, can be reserved or taken directly.
     * RESERVED   - held for a specific driver who hasn't arrived yet.
     *              Not available to others, but no car is physically there.
     * OCCUPIED   - a car is physically parked here.
     */
    public enum Status { VACANT, RESERVED, OCCUPIED }

    private final int id;
    private final Point location;
    private final int floor;
    private final Type type;
    private Status status;

    public ParkingSlot(int id, double x, double y, int floor, Type type) {
        this.id = id;
        this.location = new Point(x, y);
        this.floor = floor;
        this.type = type;
        this.status = Status.VACANT;
    }

    public int getId() { return id; }
    public Point getLocation() { return location; }
    public int getFloor() { return floor; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }

    public boolean isVacant() { return status == Status.VACANT; }
    public boolean isReserved() { return status == Status.RESERVED; }
    public boolean isOccupied() { return status == Status.OCCUPIED; }

    /** Car physically parks here (whether or not it was reserved first). */
    public void occupy() { this.status = Status.OCCUPIED; }

    /** Car leaves, or a reservation is cancelled — slot becomes free again. */
    public void vacate() { this.status = Status.VACANT; }

    /** Held for a driver who hasn't arrived yet. */
    public void reserve() { this.status = Status.RESERVED; }

    @Override
    public String toString() {
        return String.format("Slot#%d [floor=%d, %s, type=%s, %s]",
                id, floor, location, type, status);
    }
}