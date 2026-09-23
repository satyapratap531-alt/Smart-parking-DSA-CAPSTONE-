package com.parking.ui;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;
import com.parking.model.Reservation;
import com.parking.model.Zone;
import com.parking.tree.ParkingSystem;
import com.parking.tree.PointLocationService;
import com.parking.tree.QuadTree;
import com.parking.tree.RecommendationService;
import com.parking.tree.ReservationService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Interactive demonstration front end for the Smart Parking System.
 *
 * Everything shown here is produced by the real backend classes:
 * QuadTree, ParkingSystem, PointLocationService, RecommendationService
 * and ReservationService. No results are simulated or hard-coded.
 *
 * Every input (slot count, floors, occupancy, EV share, driver position,
 * query box, scoring weights) is editable live, so the dataset can be
 * regenerated with different parameters in a single click during a demo.
 */
public class DemoFrame extends JFrame {

    private static final double LOT_SIZE = 100;

    // --- backend ---
    private final Boundary lotBoundary = new Boundary(LOT_SIZE / 2, LOT_SIZE / 2, LOT_SIZE / 2, LOT_SIZE / 2);
    private ParkingSystem system;
    private PointLocationService locator;
    private RecommendationService recommender;
    private ReservationService reservations;

    // --- input controls ---
    private final JSpinner slotCountSpinner   = new JSpinner(new SpinnerNumberModel(60, 1, 20000, 10));
    private final JSpinner floorCountSpinner  = new JSpinner(new SpinnerNumberModel(2, 1, 6, 1));
    private final JSpinner occupancySpinner   = new JSpinner(new SpinnerNumberModel(50, 0, 100, 5));
    private final JSpinner evShareSpinner     = new JSpinner(new SpinnerNumberModel(15, 0, 100, 5));
    private final JSpinner seedSpinner        = new JSpinner(new SpinnerNumberModel(42, 0, 99999, 1));

    private final JSpinner driverXSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0.0, LOT_SIZE, 1.0));
    private final JSpinner driverYSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0.0, LOT_SIZE, 1.0));
    private final JComboBox<String> preferenceBox = new JComboBox<>(new String[]{"No preference", "EV", "REGULAR", "HANDICAPPED"});

    private final JSpinner qx1 = new JSpinner(new SpinnerNumberModel(20.0, 0.0, LOT_SIZE, 5.0));
    private final JSpinner qy1 = new JSpinner(new SpinnerNumberModel(20.0, 0.0, LOT_SIZE, 5.0));
    private final JSpinner qx2 = new JSpinner(new SpinnerNumberModel(60.0, 0.0, LOT_SIZE, 5.0));
    private final JSpinner qy2 = new JSpinner(new SpinnerNumberModel(60.0, 0.0, LOT_SIZE, 5.0));

    private final JSpinner wDistance   = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 500.0, 1.0));
    private final JSpinner wCongestion = new JSpinner(new SpinnerNumberModel(25.0, 0.0, 500.0, 5.0));
    private final JSpinner wPreference = new JSpinner(new SpinnerNumberModel(50.0, 0.0, 500.0, 5.0));

    private final JSpinner reserveIdSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
    private final JSpinner holdSecSpinner   = new JSpinner(new SpinnerNumberModel(10, 1, 3600, 5));

    // --- display ---
    private final JTabbedPane floorTabs = new JTabbedPane();
    private final List<DemoLotPanel> panels = new ArrayList<>();
    private final JTextArea log = new JTextArea();
    private final JLabel statsLabel = new JLabel();
    private final JCheckBox showTreeBox = new JCheckBox("Show QuadTree subdivision", true);
    private final JCheckBox showZoneBox = new JCheckBox("Show zones", true);

    private Timer simTimer;
    private Reservation lastReservation;

    public DemoFrame() {
        super("Smart Parking System \u2014 Interactive Demo (Quad Tree / Range Search / Point Location)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildControlPanel(), BorderLayout.WEST);
        add(floorTabs, BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        regenerate();

        setSize(1320, 830);
        setLocationRelativeTo(null);
    }

    // ================= UI construction =================

    private JComponent buildControlPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        p.add(section("1.  Dataset input  (change \u2192 Regenerate)", grid(
                new String[]{"Total slots:", "Floors:", "Occupied %:", "EV %:", "Random seed:"},
                new JComponent[]{slotCountSpinner, floorCountSpinner, occupancySpinner, evShareSpinner, seedSpinner})));

        JButton regen = new JButton("\u21bb  Regenerate dataset");
        regen.setBackground(new Color(46, 125, 50));
        regen.setForeground(Color.WHITE);
        regen.setFocusPainted(false);
        regen.addActionListener(e -> regenerate());
        JPanel regenWrap = new JPanel(new BorderLayout());
        regenWrap.add(regen, BorderLayout.CENTER);
        regenWrap.setMaximumSize(new Dimension(320, 34));
        p.add(regenWrap);
        p.add(Box.createVerticalStrut(6));

        p.add(section("2.  Driver query", grid(
                new String[]{"Driver X:", "Driver Y:", "Wants type:"},
                new JComponent[]{driverXSpinner, driverYSpinner, preferenceBox})));

        p.add(buttonRow(
                btn("Nearest vacant (FAST)", new Color(21, 101, 192), e -> findNearest(true)),
                btn("Nearest vacant (naive)", new Color(69, 90, 100), e -> findNearest(false))));
        p.add(buttonRow(
                btn("Compare naive vs fast", new Color(94, 53, 177), e -> compareSearches()),
                btn("Which zone? (point location)", new Color(0, 121, 107), e -> pointLocate())));
        p.add(Box.createVerticalStrut(6));

        p.add(section("3.  Recommendation weights", grid(
                new String[]{"w1 \u00b7 distance:", "w2 \u00b7 congestion:", "w3 \u00b7 wrong type:"},
                new JComponent[]{wDistance, wCongestion, wPreference})));
        p.add(buttonRow(btn("Recommend best slot (weighted)", new Color(230, 126, 34), e -> recommend())));
        p.add(Box.createVerticalStrut(6));

        p.add(section("4.  Range query (orthogonal search)", grid(
                new String[]{"x from:", "y from:", "x to:", "y to:"},
                new JComponent[]{qx1, qy1, qx2, qy2})));
        p.add(buttonRow(
                btn("Run range query", new Color(0, 151, 167), e -> rangeQuery()),
                btn("Clear overlays", new Color(84, 110, 122), e -> clearOverlays())));
        p.add(Box.createVerticalStrut(6));

        p.add(section("5.  Reservation lifecycle", grid(
                new String[]{"Slot ID:", "Hold (seconds):"},
                new JComponent[]{reserveIdSpinner, holdSecSpinner})));
        p.add(buttonRow(
                btn("Reserve", new Color(245, 124, 0), e -> doReserve()),
                btn("Confirm arrival", new Color(198, 40, 40), e -> doConfirm())));
        p.add(buttonRow(
                btn("Cancel", new Color(120, 144, 156), e -> doCancel()),
                btn("Release expired", new Color(120, 144, 156), e -> doReleaseExpired())));
        p.add(Box.createVerticalStrut(6));

        p.add(section("6.  Dynamic update", buttonColumn(
                btn("Delete highlighted slot (tree remove)", new Color(93, 64, 55), e -> deleteHighlighted()),
                btn("Start / stop live simulation", new Color(55, 71, 79), e -> toggleSim()))));

        JPanel view = new JPanel(new GridLayout(0, 1));
        view.setBorder(new TitledBorder("7.  View"));
        showTreeBox.addActionListener(e -> { for (DemoLotPanel dp : panels) dp.setShowQuadTree(showTreeBox.isSelected()); });
        showZoneBox.addActionListener(e -> { for (DemoLotPanel dp : panels) dp.setShowZones(showZoneBox.isSelected()); });
        view.add(showTreeBox);
        view.add(showZoneBox);
        view.setMaximumSize(new Dimension(320, 70));
        p.add(view);

        p.add(Box.createVerticalGlue());

        JScrollPane sc = new JScrollPane(p);
        sc.setPreferredSize(new Dimension(345, 800));
        sc.getVerticalScrollBar().setUnitIncrement(16);
        return sc;
    }

    private JPanel section(String title, JComponent inner) {
        JPanel s = new JPanel(new BorderLayout());
        s.setBorder(new TitledBorder(title));
        s.add(inner, BorderLayout.CENTER);
        s.setMaximumSize(new Dimension(330, 200));
        return s;
    }

    private JPanel grid(String[] labels, JComponent[] fields) {
        JPanel g = new JPanel(new GridLayout(labels.length, 2, 4, 3));
        for (int i = 0; i < labels.length; i++) {
            g.add(new JLabel(labels[i]));
            g.add(fields[i]);
        }
        return g;
    }

    private JButton btn(String text, Color bg, java.awt.event.ActionListener a) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.addActionListener(a);
        return b;
    }

    private JPanel buttonRow(JButton... bs) {
        JPanel row = new JPanel(new GridLayout(1, bs.length, 4, 0));
        for (JButton b : bs) row.add(b);
        row.setMaximumSize(new Dimension(330, 30));
        return row;
    }

    private JPanel buttonColumn(JButton... bs) {
        JPanel col = new JPanel(new GridLayout(bs.length, 1, 0, 4));
        for (JButton b : bs) col.add(b);
        return col;
    }

    private JComponent buildBottomPanel() {
        log.setEditable(false);
        log.setFont(new Font("Consolas", Font.PLAIN, 12));
        log.setBackground(new Color(18, 20, 24));
        log.setForeground(new Color(200, 230, 200));
        JScrollPane sc = new JScrollPane(log);
        sc.setPreferredSize(new Dimension(100, 190));
        sc.setBorder(new TitledBorder("Operation log \u2014 every line is real output from the backend classes"));

        statsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statsLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        legend.add(swatch(new Color(76, 175, 80), "VACANT"));
        legend.add(swatch(new Color(255, 152, 0), "RESERVED"));
        legend.add(swatch(new Color(229, 57, 53), "OCCUPIED"));
        legend.add(swatch(new Color(0, 229, 255), "driver / range hits"));
        legend.add(swatch(new Color(255, 235, 59), "search result"));
        legend.add(new JLabel("|  'E' = EV slot  |  click the map to move the driver"));

        JPanel north = new JPanel(new BorderLayout());
        north.add(statsLabel, BorderLayout.NORTH);
        north.add(legend, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(north, BorderLayout.NORTH);
        bottom.add(sc, BorderLayout.CENTER);
        return bottom;
    }

    private JPanel swatch(Color c, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel box = new JLabel("   ");
        box.setOpaque(true);
        box.setBackground(c);
        box.setPreferredSize(new Dimension(14, 14));
        p.add(box);
        p.add(new JLabel(label));
        return p;
    }

    // ================= dataset generation =================

    private void regenerate() {
        if (simTimer != null && simTimer.isRunning()) simTimer.stop();

        int totalSlots = (int) slotCountSpinner.getValue();
        int floorCount = (int) floorCountSpinner.getValue();
        double occupiedShare = ((Number) occupancySpinner.getValue()).doubleValue() / 100.0;
        double evShare = ((Number) evShareSpinner.getValue()).doubleValue() / 100.0;
        long seed = ((Number) seedSpinner.getValue()).longValue();

        system = new ParkingSystem(lotBoundary);
        locator = new PointLocationService();
        recommender = new RecommendationService(system, locator);
        reservations = new ReservationService(system);
        lastReservation = null;

        locator.addZone(new Zone("Zone A - Near Entrance", new Boundary(25, 25, 25, 25)));
        locator.addZone(new Zone("Zone B - Mid Lot",       new Boundary(75, 25, 25, 25)));
        locator.addZone(new Zone("Zone C - Far Corner",    new Boundary(25, 75, 25, 25)));
        locator.addZone(new Zone("Zone D - Rear",          new Boundary(75, 75, 25, 25)));

        Random rand = new Random(seed);
        int perFloor = Math.max(1, totalSlots / floorCount);
        int id = 1;
        for (int floor = 1; floor <= floorCount; floor++) {
            for (int i = 0; i < perFloor; i++) {
                double x = rand.nextDouble() * LOT_SIZE;
                double y = rand.nextDouble() * LOT_SIZE;
                ParkingSlot.Type type = rand.nextDouble() < evShare
                        ? ParkingSlot.Type.EV : ParkingSlot.Type.REGULAR;
                ParkingSlot s = new ParkingSlot(id++, x, y, floor, type);
                if (rand.nextDouble() < occupiedShare) s.occupy();
                system.insertSlot(s);
            }
        }

        rebuildTabs(floorCount);
        applyWeights();
        refreshAll();

        log.setText("");
        logLine("=== Dataset regenerated ===");
        logLine(String.format("  %d slots across %d floor(s) | %.0f%% occupied | %.0f%% EV | seed=%d",
                system.totalSlots(), floorCount, occupiedShare * 100, evShare * 100, seed));
        for (int f = 1; f <= floorCount; f++) {
            QuadTree t = system.getFloorTree(f);
            if (t != null) {
                logLine(String.format("  Floor %d QuadTree -> %d slots, depth=%d, nodes=%d, leaf regions=%d",
                        f, t.size(), t.depth(), t.nodeCount(), t.leafCount()));
            }
        }
        logLine("Change any input above and press Regenerate to rebuild with different parameters.");
    }

    private void rebuildTabs(int floorCount) {
        floorTabs.removeAll();
        panels.clear();
        for (int f = 1; f <= floorCount; f++) {
            DemoLotPanel dp = new DemoLotPanel(lotBoundary);
            dp.setZones(locator.getZones());
            dp.setShowQuadTree(showTreeBox.isSelected());
            dp.setShowZones(showZoneBox.isSelected());
            dp.setOnClick(pt -> {
                driverXSpinner.setValue(Math.round(pt.x * 10) / 10.0);
                driverYSpinner.setValue(Math.round(pt.y * 10) / 10.0);
                refreshDriverMarker();
                logLine(String.format("Driver moved to (%.1f, %.1f) by map click.", pt.x, pt.y));
            });
            panels.add(dp);
            floorTabs.addTab("Floor " + f, dp);
        }
    }

    // ================= operations =================

    private int currentFloor() { return floorTabs.getSelectedIndex() + 1; }
    private DemoLotPanel currentPanel() { return panels.get(floorTabs.getSelectedIndex()); }

    private Point driverPoint() {
        return new Point(((Number) driverXSpinner.getValue()).doubleValue(),
                ((Number) driverYSpinner.getValue()).doubleValue());
    }

    private ParkingSlot.Type selectedPreference() {
        String s = (String) preferenceBox.getSelectedItem();
        if (s == null || s.equals("No preference")) return null;
        return ParkingSlot.Type.valueOf(s);
    }

    private void applyWeights() {
        recommender.setWeights(
                ((Number) wDistance.getValue()).doubleValue(),
                ((Number) wCongestion.getValue()).doubleValue(),
                ((Number) wPreference.getValue()).doubleValue());
    }

    private void findNearest(boolean fast) {
        int floor = currentFloor();
        Point from = driverPoint();
        long t0 = System.nanoTime();
        ParkingSlot result = fast ? system.findNearestVacantFast(floor, from)
                : system.findNearestVacant(floor, from);
        long us = (System.nanoTime() - t0) / 1000;

        currentPanel().setHighlighted(result);
        if (result == null) {
            logLine(String.format("[%s] No vacant slot on floor %d.", fast ? "FAST" : "naive", floor));
        } else {
            logLine(String.format("[%s] Floor %d nearest vacant to %s -> Slot#%d %s, dist=%.2f  (%d \u00b5s)",
                    fast ? "FAST" : "naive", floor, from, result.getId(), result.getLocation(),
                    from.distanceTo(result.getLocation()), us));
            reserveIdSpinner.setValue(result.getId());
        }
    }

    private void compareSearches() {
        int floor = currentFloor();
        Point from = driverPoint();

        long t0 = System.nanoTime();
        ParkingSlot naive = system.findNearestVacant(floor, from);
        long naiveUs = (System.nanoTime() - t0) / 1000;

        long t1 = System.nanoTime();
        ParkingSlot fast = system.findNearestVacantFast(floor, from);
        long fastUs = (System.nanoTime() - t1) / 1000;

        currentPanel().setHighlighted(fast);
        boolean match = (naive == null && fast == null)
                || (naive != null && fast != null && naive.getId() == fast.getId());

        logLine("--- naive O(n) vs best-first O(log n) on floor " + floor
                + " (" + system.allSlotsOnFloor(floor).size() + " slots) ---");
        logLine(String.format("   naive : %s   (%d \u00b5s)", naive == null ? "none" : "Slot#" + naive.getId(), naiveUs));
        logLine(String.format("   fast  : %s   (%d \u00b5s)", fast == null ? "none" : "Slot#" + fast.getId(), fastUs));
        logLine("   results identical: " + match
                + (match ? "   <- pruning does not sacrifice correctness" : "   <- MISMATCH"));
        logLine("   (at small slot counts the priority-queue overhead can make FAST slower;");
        logLine("    raise 'Total slots' to ~5000+ and regenerate to see the gap invert.)");
    }

    private void pointLocate() {
        Point from = driverPoint();
        Zone z = locator.locate(from);
        logLine(String.format("[Point location] %s -> %s", from, z == null ? "outside every defined zone" : z.getName()));
        if (z != null) {
            double c = recommender.congestionOf(z, currentFloor());
            logLine(String.format("   congestion of %s on floor %d = %.0f%% occupied",
                    z.getName(), currentFloor(), c * 100));
        }
    }

    private void recommend() {
        applyWeights();
        Point from = driverPoint();
        ParkingSlot.Type pref = selectedPreference();

        long t0 = System.nanoTime();
        ParkingSlot best = recommender.recommend(from, pref);
        long us = (System.nanoTime() - t0) / 1000;

        if (best == null) { logLine("[Recommend] No vacant slot anywhere."); return; }

        floorTabs.setSelectedIndex(best.getFloor() - 1);
        currentPanel().setHighlighted(best);
        reserveIdSpinner.setValue(best.getId());

        Zone z = locator.locate(best.getLocation());
        double dist = from.distanceTo(best.getLocation());
        double cong = z == null ? 0 : recommender.congestionOf(z, best.getFloor());

        logLine(String.format("[Recommend] pref=%s -> Slot#%d (floor %d, %s, %s)  score=%.2f  (%d \u00b5s)",
                pref == null ? "none" : pref, best.getId(), best.getFloor(),
                best.getLocation(), best.getType(),
                recommender.scoreOf(best, from, pref), us));
        logLine(String.format("   breakdown: distance=%.2f x w1=%.1f | congestion=%.0f%% x w2=%.1f | type match=%s",
                dist, ((Number) wDistance.getValue()).doubleValue(),
                cong * 100, ((Number) wCongestion.getValue()).doubleValue(),
                (pref == null || best.getType() == pref) ? "yes (no penalty)" : "no (penalty applied)"));
        logLine("   tip: raise w2 and re-run to watch it avoid crowded zones and pick a farther slot.");
    }

    private void rangeQuery() {
        double x1 = ((Number) qx1.getValue()).doubleValue();
        double y1 = ((Number) qy1.getValue()).doubleValue();
        double x2 = ((Number) qx2.getValue()).doubleValue();
        double y2 = ((Number) qy2.getValue()).doubleValue();

        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        double hw = Math.abs(x2 - x1) / 2, hh = Math.abs(y2 - y1) / 2;
        Boundary box = new Boundary(cx, cy, hw, hh);

        int floor = currentFloor();
        long t0 = System.nanoTime();
        List<ParkingSlot> hits = system.rangeQuery(floor, box);
        long us = (System.nanoTime() - t0) / 1000;

        long vacant = hits.stream().filter(ParkingSlot::isVacant).count();

        currentPanel().setQueryBox(box);
        currentPanel().setRangeHits(hits);

        logLine(String.format("[Range query] floor %d, x:%.0f-%.0f y:%.0f-%.0f -> %d slot(s), %d vacant  (%d \u00b5s)",
                floor, Math.min(x1, x2), Math.max(x1, x2), Math.min(y1, y2), Math.max(y1, y2),
                hits.size(), vacant, us));
        logLine("   branches whose boundary did not intersect this box were pruned without being visited.");
    }

    private void clearOverlays() {
        for (DemoLotPanel dp : panels) dp.clearOverlays();
        logLine("Overlays cleared.");
    }

    private void doReserve() {
        int slotId = (int) reserveIdSpinner.getValue();
        int hold = (int) holdSecSpinner.getValue();
        ParkingSlot before = system.findSlotById(slotId);
        if (before == null) { logLine("[Reserve] No slot with id " + slotId + "."); return; }

        String statusBefore = before.getStatus().toString();
        Reservation r = reservations.reserve(slotId, "TN-01-AB-" + (1000 + slotId), Duration.ofSeconds(hold));

        if (r == null) {
            logLine(String.format("[Reserve] REJECTED for Slot#%d \u2014 status is %s, not VACANT.", slotId, statusBefore));
            logLine("   this is the double-booking guard: only a VACANT slot can be reserved.");
        } else {
            lastReservation = r;
            logLine(String.format("[Reserve] Slot#%d  %s -> %s   (%s, expires in %ds)",
                    slotId, statusBefore, before.getStatus(), r.getVehiclePlate(), hold));
            logLine("   press Reserve again on the same slot to see the conflict rejection.");
        }
        refreshAll();
    }

    private void doConfirm() {
        int slotId = (int) reserveIdSpinner.getValue();
        ParkingSlot s = system.findSlotById(slotId);
        String before = s == null ? "n/a" : s.getStatus().toString();
        boolean ok = reservations.confirmArrival(slotId);
        logLine(String.format("[Confirm arrival] Slot#%d  %s -> %s   success=%s",
                slotId, before, s == null ? "n/a" : s.getStatus(), ok));
        refreshAll();
    }

    private void doCancel() {
        if (lastReservation == null) { logLine("[Cancel] No reservation recorded in this session."); return; }
        boolean ok = reservations.cancel(lastReservation.getReservationId());
        logLine(String.format("[Cancel] Reservation#%d cancelled=%s \u2014 slot returns to VACANT.",
                lastReservation.getReservationId(), ok));
        if (ok) lastReservation = null;
        refreshAll();
    }

    private void doReleaseExpired() {
        List<Reservation> expired = reservations.releaseExpired(Instant.now());
        logLine(String.format("[Release expired] swept %d expired reservation(s); %d still active.",
                expired.size(), reservations.activeReservationCount()));
        for (Reservation r : expired) logLine("   freed Slot#" + r.getSlotId() + " (hold elapsed)");
        refreshAll();
    }

    private void deleteHighlighted() {
        int slotId = (int) reserveIdSpinner.getValue();
        ParkingSlot s = system.findSlotById(slotId);
        if (s == null) { logLine("[Delete] No slot with id " + slotId + "."); return; }

        QuadTree t = system.getFloorTree(s.getFloor());
        int beforeCount = t.size(), beforeDepth = t.depth(), beforeLeaves = t.leafCount();
        boolean removed = system.removeSlot(s);

        logLine(String.format("[Delete] Slot#%d removed=%s", slotId, removed));
        logLine(String.format("   floor %d tree: %d -> %d slots, depth %d -> %d, leaf regions %d -> %d",
                s.getFloor(), beforeCount, t.size(), beforeDepth, t.depth(), beforeLeaves, t.leafCount()));
        if (t.leafCount() < beforeLeaves) logLine("   nodes merged back into a leaf (tryMerge).");
        currentPanel().setHighlighted(null);
        refreshAll();
    }

    private void toggleSim() {
        if (simTimer != null && simTimer.isRunning()) {
            simTimer.stop();
            logLine("[Simulation] stopped.");
            return;
        }
        Random rand = new Random();
        simTimer = new Timer(600, e -> {
            int floor = 1 + rand.nextInt(panels.size());
            List<ParkingSlot> fs = system.allSlotsOnFloor(floor);
            if (fs.isEmpty()) return;
            ParkingSlot s = fs.get(rand.nextInt(fs.size()));
            if (s.isVacant()) s.occupy();
            else if (s.isOccupied()) s.vacate();
            refreshAll();
        });
        simTimer.start();
        logLine("[Simulation] running \u2014 cars arriving and leaving at random; press again to stop.");
    }

    // ================= refresh =================

    private void refreshDriverMarker() {
        for (DemoLotPanel dp : panels) dp.setDriver(driverPoint());
    }

    private void refreshAll() {
        for (int i = 0; i < panels.size(); i++) {
            int floor = i + 1;
            DemoLotPanel dp = panels.get(i);
            dp.setSlots(system.allSlotsOnFloor(floor));
            QuadTree t = system.getFloorTree(floor);
            List<Boundary> leaves = new ArrayList<>();
            if (t != null) t.collectLeafBoundaries(leaves);
            dp.setLeafBoxes(leaves);
            dp.setZones(locator.getZones());
        }
        refreshDriverMarker();
        updateStats();
    }

    private void updateStats() {
        int vacant = 0, reserved = 0, occupied = 0;
        for (int f : system.floorNumbers())
            for (ParkingSlot s : system.allSlotsOnFloor(f)) {
                if (s.isVacant()) vacant++;
                else if (s.isReserved()) reserved++;
                else occupied++;
            }

        StringBuilder trees = new StringBuilder();
        for (int f = 1; f <= panels.size(); f++) {
            QuadTree t = system.getFloorTree(f);
            if (t != null) trees.append(String.format("  |  F%d: depth %d, %d leaves", f, t.depth(), t.leafCount()));
        }

        statsLabel.setText(String.format(
                "Total %d slots   |   VACANT %d   RESERVED %d   OCCUPIED %d   |   active reservations: %d%s",
                system.totalSlots(), vacant, reserved, occupied,
                reservations.activeReservationCount(), trees));
    }

    private void logLine(String s) {
        log.append(s + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}