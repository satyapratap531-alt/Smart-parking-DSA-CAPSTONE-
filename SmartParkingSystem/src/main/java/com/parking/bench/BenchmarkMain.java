package com.parking.bench;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;
import com.parking.tree.QuadTree;

import java.util.Random;

/**
 * Measures how naive O(n) nearest-vacant search and the best-first
 * O(log n)-average QuadTree search actually scale as the number of
 * parking slots grows. This is the evidence for the complexity claim
 * in the project report — not just an assertion.
 */
public class BenchmarkMain {

    private static final int QUERIES_PER_SIZE = 200; // average out noise
    private static final Random rand = new Random(7);

    public static void main(String[] args) {
        int[] sizes = { 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 250_000 };

        System.out.printf("%-10s %-18s %-18s%n", "N", "Naive (avg µs)", "Fast (avg µs)");
        System.out.println("---------------------------------------------------");

        for (int n : sizes) {
            QuadTree tree = buildRandomLot(n);

            long naiveTotal = 0;
            long fastTotal = 0;

            // Warm-up run so JIT compilation doesn't skew the first measured size
            Point warm = randomPoint();
            tree.findNearestVacant(warm);
            tree.findNearestVacantFast(warm);

            for (int i = 0; i < QUERIES_PER_SIZE; i++) {
                Point query = randomPoint();

                long t0 = System.nanoTime();
                tree.findNearestVacant(query);
                long t1 = System.nanoTime();
                naiveTotal += (t1 - t0);

                long t2 = System.nanoTime();
                tree.findNearestVacantFast(query);
                long t3 = System.nanoTime();
                fastTotal += (t3 - t2);
            }

            double naiveAvgUs = naiveTotal / (double) QUERIES_PER_SIZE / 1000.0;
            double fastAvgUs = fastTotal / (double) QUERIES_PER_SIZE / 1000.0;

            System.out.printf("%-10d %-18.2f %-18.2f%n", n, naiveAvgUs, fastAvgUs);
        }
    }

    private static QuadTree buildRandomLot(int n) {
        Boundary lotBoundary = new Boundary(50_000, 50_000, 50_000, 50_000);
        QuadTree tree = new QuadTree(lotBoundary);

        for (int i = 1; i <= n; i++) {
            double x = rand.nextDouble() * 100_000;
            double y = rand.nextDouble() * 100_000;
            ParkingSlot slot = new ParkingSlot(i, x, y, 1, ParkingSlot.Type.REGULAR);
            if (rand.nextDouble() < 0.7) slot.occupy(); // 70% full, so "vacant" is meaningfully sparse
            tree.insert(slot);
        }
        return tree;
    }

    private static Point randomPoint() {
        return new Point(rand.nextDouble() * 100_000, rand.nextDouble() * 100_000);
    }
}