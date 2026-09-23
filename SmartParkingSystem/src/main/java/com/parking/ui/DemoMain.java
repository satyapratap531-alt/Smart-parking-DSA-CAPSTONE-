package com.parking.ui;

import javax.swing.*;

/**
 * Entry point for the interactive demo front end.
 *
 * Run THIS class for a live demonstration. It opens a single window
 * containing every feature of the system, with all inputs editable
 * at run time so the dataset and query parameters can be changed
 * on the spot without restarting or recompiling.
 */
public class DemoMain {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new DemoFrame().setVisible(true));
    }
}