package com.parking.tree;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A PR-QuadTree (Point-Region QuadTree) over ParkingSlot objects.
 *
 * Each node covers a rectangular Boundary. While a node holds fewer
 * than CAPACITY slots, it stays a leaf and stores them directly.
 * Once it overflows, it "subdivides" into four children (NW, NE, SW, SE)
 * and its slots are redistributed into whichever child's boundary
 * contains them.
 */
public class QuadTree {

    private static final int CAPACITY = 4; // max slots per leaf before splitting

    private final Boundary boundary;
    private final List<ParkingSlot> slots;   // only populated at leaf nodes
    private boolean divided;

    private QuadTree northWest, northEast, southWest, southEast;

    public QuadTree(Boundary boundary) {
        this.boundary = boundary;
        this.slots = new ArrayList<>();
        this.divided = false;
    }

    /**
     * Insert a slot into the tree. Returns false if the slot's location
     * falls outside this node's boundary (shouldn't happen if called on
     * the root with a slot inside the lot).
     */
    public boolean insert(ParkingSlot slot) {
        if (!boundary.contains(slot.getLocation())) {
            return false; // this slot doesn't belong anywhere in this node
        }

        if (!divided && slots.size() < CAPACITY) {
            slots.add(slot);
            return true;
        }

        if (!divided) {
            subdivide();
        }

        // Try each child until one accepts it
        if (northWest.insert(slot)) return true;
        if (northEast.insert(slot)) return true;
        if (southWest.insert(slot)) return true;
        if (southEast.insert(slot)) return true;

        return false; // should not happen
    }

    /** Split this leaf into 4 children and push its existing slots down. */
    private void subdivide() {
        northWest = new QuadTree(boundary.northWest());
        northEast = new QuadTree(boundary.northEast());
        southWest = new QuadTree(boundary.southWest());
        southEast = new QuadTree(boundary.southEast());
        divided = true;

        for (ParkingSlot s : slots) {
            if (northWest.insert(s)) continue;
            if (northEast.insert(s)) continue;
            if (southWest.insert(s)) continue;
            southEast.insert(s);
        }
        slots.clear(); // internal nodes don't hold slots directly anymore
    }

    /**
     * Orthogonal range search: find every slot whose location falls
     * inside queryRange. Prunes entire branches that don't overlap
     * the query box — this is what gives us the O(log n + k) speed-up
     * over scanning every slot.
     */
    public List<ParkingSlot> rangeQuery(Boundary queryRange) {
        List<ParkingSlot> found = new ArrayList<>();
        rangeQuery(queryRange, found);
        return found;
    }

    private void rangeQuery(Boundary queryRange, List<ParkingSlot> found) {
        if (!boundary.intersects(queryRange)) {
            return; // prune this whole branch
        }

        if (!divided) {
            for (ParkingSlot s : slots) {
                if (queryRange.contains(s.getLocation())) {
                    found.add(s);
                }
            }
            return;
        }

        northWest.rangeQuery(queryRange, found);
        northEast.rangeQuery(queryRange, found);
        southWest.rangeQuery(queryRange, found);
        southEast.rangeQuery(queryRange, found);
    }

    /**
     * Naive nearest-vacant-slot search (Phase 1 version): gathers every
     * vacant slot in the tree and picks the closest by straight-line
     * distance. We'll replace this with a true expanding-radius kNN
     * search in Phase 2 for real O(log n) performance.
     */
    public ParkingSlot findNearestVacant(Point from) {
        List<ParkingSlot> all = new ArrayList<>();
        collectAll(all);

        ParkingSlot best = null;
        double bestDist = Double.MAX_VALUE;
        for (ParkingSlot s : all) {
            if (!s.isVacant()) continue;
            double d = from.distanceTo(s.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    /**
     * True O(log n)-average nearest-vacant search using best-first traversal.
     *
     * We keep a priority queue of tree nodes, always expanding the node
     * whose boundary is *closest possible* to the query point first. The
     * moment the closest remaining node's minimum distance exceeds the
     * best vacant slot we've already found, we stop — everything left
     * in the queue is provably farther away. This is the same principle
     * as best-first search on a k-d tree.
     */
    public ParkingSlot findNearestVacantFast(Point from) {
        PriorityQueue<QuadTree> queue = new PriorityQueue<>(
                Comparator.comparingDouble(node -> node.boundary.distanceTo(from)));
        queue.add(this);

        ParkingSlot best = null;
        double bestDist = Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            QuadTree node = queue.poll();
            double nodeMinDist = node.boundary.distanceTo(from);

            // Nothing left in the queue can beat 'best' — stop early.
            if (nodeMinDist > bestDist) {
                break;
            }

            if (!node.divided) {
                for (ParkingSlot s : node.slots) {
                    if (!s.isVacant()) continue;
                    double d = from.distanceTo(s.getLocation());
                    if (d < bestDist) {
                        bestDist = d;
                        best = s;
                    }
                }
            } else {
                queue.add(node.northWest);
                queue.add(node.northEast);
                queue.add(node.southWest);
                queue.add(node.southEast);
            }
        }
        return best;
    }

    private void collectAll(List<ParkingSlot> out) {
        if (!divided) {
            out.addAll(slots);
            return;
        }
        northWest.collectAll(out);
        northEast.collectAll(out);
        southWest.collectAll(out);
        southEast.collectAll(out);
    }

    /** All slots (occupied and vacant) anywhere in this subtree. */
    public List<ParkingSlot> allSlots() {
        List<ParkingSlot> out = new ArrayList<>();
        collectAll(out);
        return out;
    }

    /** Total number of slots stored anywhere in this subtree. */
    public int size() {
        List<ParkingSlot> all = new ArrayList<>();
        collectAll(all);
        return all.size();
    }

    /**
     * Remove a slot from the tree entirely (e.g. the slot is being
     * decommissioned, not just vacated). Returns true if it was found
     * and removed.
     *
     * After removing, if all four children of a divided node together
     * hold <= CAPACITY slots, we merge them back into a single leaf.
     * This keeps the tree from staying needlessly deep after churn.
     */
    public boolean remove(ParkingSlot slot) {
        if (!boundary.contains(slot.getLocation())) {
            return false;
        }

        if (!divided) {
            return slots.remove(slot);
        }

        boolean removed = northWest.remove(slot)
                || northEast.remove(slot)
                || southWest.remove(slot)
                || southEast.remove(slot);

        if (removed) {
            tryMerge();
        }
        return removed;
    }

    /** Collapse this node's children back into a single leaf if they're sparse enough. */
    private void tryMerge() {
        if (!divided) return;

        List<ParkingSlot> combined = new ArrayList<>();
        northWest.collectAll(combined);
        northEast.collectAll(combined);
        southWest.collectAll(combined);
        southEast.collectAll(combined);

        // Only merge if none of the four children are themselves divided
        // (merging would silently drop grandchildren otherwise) and the
        // combined count fits back within one leaf's capacity.
        boolean anyChildDivided = northWest.divided || northEast.divided
                || southWest.divided || southEast.divided;

        if (!anyChildDivided && combined.size() <= CAPACITY) {
            slots.clear();
            slots.addAll(combined);
            northWest = null;
            northEast = null;
            southWest = null;
            southEast = null;
            divided = false;
        }
    }

    /**
     * Height of this subtree (a single leaf has depth 1). Exposed so the
     * demo UI can show that the tree really is subdividing as slots are
     * added, rather than asking the user to take it on trust.
     */
    public int depth() {
        if (!divided) return 1;
        return 1 + Math.max(
                Math.max(northWest.depth(), northEast.depth()),
                Math.max(southWest.depth(), southEast.depth()));
    }

    /** Total number of nodes (internal + leaf) in this subtree. */
    public int nodeCount() {
        if (!divided) return 1;
        return 1 + northWest.nodeCount() + northEast.nodeCount()
                + southWest.nodeCount() + southEast.nodeCount();
    }

    /** Number of leaf nodes — i.e. how many regions the lot is partitioned into. */
    public int leafCount() {
        if (!divided) return 1;
        return northWest.leafCount() + northEast.leafCount()
                + southWest.leafCount() + southEast.leafCount();
    }

    /**
     * The boundaries of every leaf region, used by the demo UI to draw the
     * actual QuadTree subdivision over the parking grid.
     */
    public void collectLeafBoundaries(List<Boundary> out) {
        if (!divided) { out.add(boundary); return; }
        northWest.collectLeafBoundaries(out);
        northEast.collectLeafBoundaries(out);
        southWest.collectLeafBoundaries(out);
        southEast.collectLeafBoundaries(out);
    }

    /**
     * Convenience: find a slot by id anywhere in the tree. Useful for
     * "car with this ticket/plate is leaving" flows where the app layer
     * only has the id, not the live object.
     */
    public ParkingSlot findById(int id) {
        List<ParkingSlot> all = new ArrayList<>();
        collectAll(all);
        for (ParkingSlot s : all) {
            if (s.getId() == id) return s;
        }
        return null;
    }
}