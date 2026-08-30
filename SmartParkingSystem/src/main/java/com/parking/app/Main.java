package com.parking.app;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;
import com.parking.model.Reservation;
import com.parking.model.Zone;
import com.parking.tree.ParkingSystem;
import com.parking.tree.PointLocationService;
import com.parking.tree.RecommendationService;
import com.parking.tree.ReservationService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) {

        // The lot footprint: 100x100 units, same shape on every floor.
        Boundary floorFootprint = new Boundary(50, 50, 50, 50);
        ParkingSystem system = new ParkingSystem(floorFootprint);

        // --- Populate 2 floors with random slots ---
        Random rand = new Random(42);
        int nextId = 1;
        for (int floor = 1; floor <= 2; floor++) {
            for (int i = 0; i < 30; i++) {
                double x = rand.nextDouble() * 100;
                double y = rand.nextDouble() * 100;
                ParkingSlot.Type type = rand.nextInt(10) == 0 ? ParkingSlot.Type.EV : ParkingSlot.Type.REGULAR;

                ParkingSlot slot = new ParkingSlot(nextId++, x, y, floor, type);
                if (rand.nextDouble() < 0.6) slot.occupy(); // 60% occupied, to make congestion visible

                system.insertSlot(slot);
            }
        }
        System.out.println("Total slots across all floors: " + system.totalSlots());

        // --- Define zones for point location (spans both floors) ---
        PointLocationService locator = new PointLocationService();
        locator.addZone(new Zone("Zone A - Near Entrance", new Boundary(25, 25, 25, 25))); // covers 0-50, 0-50
        locator.addZone(new Zone("Zone B - Mid Lot",       new Boundary(75, 25, 25, 25))); // covers 50-100, 0-50
        locator.addZone(new Zone("Zone C - Far Corner",    new Boundary(25, 75, 25, 25))); // covers 0-50, 50-100
        locator.addZone(new Zone("Zone D - Rear",          new Boundary(75, 75, 25, 25))); // covers 50-100, 50-100

        // --- Demo: Point Location ---
        Point gateEntry = new Point(15, 15);
        Zone zoneAtGate = locator.locate(gateEntry);
        System.out.println("\n=== Point Location ===");
        System.out.println("Gate at " + gateEntry + " is in: " +
                (zoneAtGate != null ? zoneAtGate.getName() : "no defined zone"));

        // --- Demo: Naive vs Fast nearest vacant search ---
        Point driver = new Point(50, 50);
        System.out.println("\n=== Nearest Vacant Search: naive O(n) vs fast best-first ===");

        long t0 = System.nanoTime();
        ParkingSlot nearestNaive = system.findNearestVacant(1, driver);
        long t1 = System.nanoTime();
        ParkingSlot nearestFast = system.findNearestVacantFast(1, driver);
        long t2 = System.nanoTime();

        System.out.println("  Naive result: " + nearestNaive + "   (" + (t1 - t0) / 1000 + " microseconds)");
        System.out.println("  Fast  result: " + nearestFast  + "   (" + (t2 - t1) / 1000 + " microseconds)");
        System.out.println("  Results match: " + (nearestNaive != null && nearestNaive.getId() == nearestFast.getId()));

        // --- Demo: Deletion (a car leaves) ---
        System.out.println("\n=== Deletion Demo ===");
        if (nearestFast != null) {
            System.out.println("Before removal, floor 1 has " + system.allSlotsOnFloor(1).size() + " slots.");
            system.removeSlot(nearestFast);
            System.out.println("Removed slot #" + nearestFast.getId() + ". Floor 1 now has "
                    + system.allSlotsOnFloor(1).size() + " slots.");
        }

        // --- Demo: Congestion-aware recommendation ---
        System.out.println("\n=== Smart Recommendation (distance + congestion + preference) ===");
        RecommendationService recommender = new RecommendationService(system, locator);

        ParkingSlot recommendedEV = recommender.recommend(driver, ParkingSlot.Type.EV);
        System.out.println("Best EV-preferring recommendation for driver at " + driver + ":");
        System.out.println("  " + recommendedEV);
        if (recommendedEV != null) {
            System.out.printf("  Score: %.2f%n", recommender.scoreOf(recommendedEV, driver, ParkingSlot.Type.EV));
        }

        ParkingSlot noPreference = recommender.recommend(driver, null);
        System.out.println("Best recommendation with no type preference:");
        System.out.println("  " + noPreference);

        // --- Demo: Reservation lifecycle ---
        System.out.println("\n=== Reservation Demo ===");
        ReservationService reservations = new ReservationService(system);

        ParkingSlot toReserve = system.findNearestVacantFast(2, driver);
        System.out.println("Reserving " + toReserve + " for plate TN-01-AB-1234 (5 min hold)...");
        Reservation booking = reservations.reserve(toReserve.getId(), "TN-01-AB-1234", Duration.ofMinutes(5));
        System.out.println("  Created: " + booking);
        System.out.println("  Slot status is now: " + system.findSlotById(toReserve.getId()).getStatus());

        // A second driver tries to grab the same slot -> should be rejected
        Reservation clash = reservations.reserve(toReserve.getId(), "TN-02-CD-5678", Duration.ofMinutes(5));
        System.out.println("  Second driver attempts same slot -> reservation granted: " + (clash != null));

        // First driver actually arrives and parks
        boolean confirmed = reservations.confirmArrival(toReserve.getId());
        System.out.println("  Driver arrives, confirmArrival() succeeded: " + confirmed);
        System.out.println("  Slot status is now: " + system.findSlotById(toReserve.getId()).getStatus());

        // Demonstrate an expired reservation getting auto-released
        ParkingSlot secondSlot = system.findNearestVacantFast(2, driver);
        Reservation shortHold = reservations.reserve(secondSlot.getId(), "TN-03-EF-9999", Duration.ofMillis(1));
        System.out.println("\nReserved " + secondSlot + " with a 1ms hold (to force expiry)...");
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        List<Reservation> expired = reservations.releaseExpired(Instant.now());
        System.out.println("  Expired & released: " + expired.size() + " reservation(s)");
        System.out.println("  Slot status is now: " + system.findSlotById(secondSlot.getId()).getStatus());
    }
}