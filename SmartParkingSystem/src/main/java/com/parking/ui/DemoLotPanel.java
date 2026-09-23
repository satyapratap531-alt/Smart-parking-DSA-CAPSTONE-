package com.parking.ui;

import com.parking.model.Boundary;
import com.parking.model.ParkingSlot;
import com.parking.model.Point;
import com.parking.model.Zone;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The drawing surface for the demo. Renders, in layers:
 *   1. zone rectangles (faint, labelled)
 *   2. the actual QuadTree leaf boundaries (so the subdivision is visible)
 *   3. the range-query box, if one is active
 *   4. every parking slot, colour-coded by status
 *   5. the driver marker, the highlighted result, and a line between them
 */
public class DemoLotPanel extends JPanel {

    private static final int PAD = 34;
    private static final int SLOT = 15;

    private final Boundary lotBoundary;

    private List<ParkingSlot> slots = new ArrayList<>();
    private List<Boundary> leafBoxes = new ArrayList<>();
    private List<Zone> zones = new ArrayList<>();
    private List<ParkingSlot> rangeHits = new ArrayList<>();

    private Point driver;
    private Boundary queryBox;
    private ParkingSlot highlighted;

    private boolean showQuadTree = true;
    private boolean showZones = true;

    private Consumer<Point> onClick;

    public DemoLotPanel(Boundary lotBoundary) {
        this.lotBoundary = lotBoundary;
        setBackground(new Color(24, 26, 30));
        setPreferredSize(new Dimension(560, 560));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.accept(pixelToLot(e.getX(), e.getY()));
            }
        });
    }

    public void setOnClick(Consumer<Point> c) { this.onClick = c; }

    public void setSlots(List<ParkingSlot> s) { this.slots = s; repaint(); }
    public void setLeafBoxes(List<Boundary> b) { this.leafBoxes = b; repaint(); }
    public void setZones(List<Zone> z) { this.zones = z; repaint(); }
    public void setDriver(Point p) { this.driver = p; repaint(); }
    public void setHighlighted(ParkingSlot s) { this.highlighted = s; repaint(); }
    public void setQueryBox(Boundary b) { this.queryBox = b; repaint(); }
    public void setRangeHits(List<ParkingSlot> r) { this.rangeHits = r; repaint(); }
    public void setShowQuadTree(boolean b) { this.showQuadTree = b; repaint(); }
    public void setShowZones(boolean b) { this.showZones = b; repaint(); }

    public void clearOverlays() {
        this.highlighted = null;
        this.queryBox = null;
        this.rangeHits = new ArrayList<>();
        repaint();
    }

    // ---- coordinate conversion ----
    private double minX() { return lotBoundary.centerX - lotBoundary.halfWidth; }
    private double minY() { return lotBoundary.centerY - lotBoundary.halfHeight; }
    private double sx() { return (getWidth() - 2.0 * PAD) / (lotBoundary.halfWidth * 2.0); }
    private double sy() { return (getHeight() - 2.0 * PAD) / (lotBoundary.halfHeight * 2.0); }

    private int px(double x) { return (int) (PAD + (x - minX()) * sx()); }
    private int py(double y) { return (int) (PAD + (y - minY()) * sy()); }

    private Point pixelToLot(int mx, int my) {
        return new Point(minX() + (mx - PAD) / sx(), minY() + (my - PAD) / sy());
    }

    private void drawBoundary(Graphics2D g, Boundary b) {
        int x = px(b.centerX - b.halfWidth);
        int y = py(b.centerY - b.halfHeight);
        int w = (int) (b.halfWidth * 2 * sx());
        int h = (int) (b.halfHeight * 2 * sy());
        g.drawRect(x, y, w, h);
    }

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        Graphics2D g = (Graphics2D) gr;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. zones
        if (showZones) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (Zone z : zones) {
                Boundary a = z.getArea();
                int x = px(a.centerX - a.halfWidth);
                int y = py(a.centerY - a.halfHeight);
                int w = (int) (a.halfWidth * 2 * sx());
                int h = (int) (a.halfHeight * 2 * sy());
                g.setColor(new Color(90, 120, 200, 22));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(120, 150, 220, 90));
                g.drawRect(x, y, w, h);
                g.drawString(z.getName(), x + 5, y + 14);
            }
        }

        // 2. QuadTree subdivision
        if (showQuadTree) {
            g.setColor(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke(1));
            for (Boundary b : leafBoxes) drawBoundary(g, b);
        }

        // 3. active range query box
        if (queryBox != null) {
            g.setColor(new Color(0, 200, 255, 30));
            int x = px(queryBox.centerX - queryBox.halfWidth);
            int y = py(queryBox.centerY - queryBox.halfHeight);
            int w = (int) (queryBox.halfWidth * 2 * sx());
            int h = (int) (queryBox.halfHeight * 2 * sy());
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0, 210, 255));
            g.setStroke(new BasicStroke(2));
            g.drawRect(x, y, w, h);
        }

        // 4. slots
        for (ParkingSlot s : slots) drawSlot(g, s);

        // 5. driver + result
        if (driver != null) {
            if (highlighted != null) {
                g.setColor(new Color(255, 235, 59, 170));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(px(driver.x), py(driver.y),
                        px(highlighted.getLocation().x), py(highlighted.getLocation().y));
            }
            int dx = px(driver.x), dy = py(driver.y);
            g.setColor(new Color(0, 229, 255));
            g.fillOval(dx - 6, dy - 6, 12, 12);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(dx - 6, dy - 6, 12, 12);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString("DRIVER", dx + 10, dy + 4);
        }
    }

    private void drawSlot(Graphics2D g, ParkingSlot s) {
        int x = px(s.getLocation().x);
        int y = py(s.getLocation().y);

        Color fill = switch (s.getStatus()) {
            case VACANT -> new Color(76, 175, 80);
            case RESERVED -> new Color(255, 152, 0);
            case OCCUPIED -> new Color(229, 57, 53);
        };

        g.setColor(fill);
        g.fillRoundRect(x - SLOT / 2, y - SLOT / 2, SLOT, SLOT, 4, 4);

        if (rangeHits.contains(s)) {
            g.setColor(new Color(0, 229, 255));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x - SLOT / 2 - 2, y - SLOT / 2 - 2, SLOT + 4, SLOT + 4, 5, 5);
        }

        if (s.getType() == ParkingSlot.Type.EV) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.drawString("E", x - 3, y + 3);
        }

        if (s == highlighted) {
            g.setColor(new Color(255, 235, 59));
            g.setStroke(new BasicStroke(3));
            g.drawOval(x - SLOT / 2 - 6, y - SLOT / 2 - 6, SLOT + 12, SLOT + 12);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString("#" + s.getId(), x + 12, y - 8);
        }
    }
}