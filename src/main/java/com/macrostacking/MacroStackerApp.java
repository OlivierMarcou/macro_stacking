package com.macrostacking;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;

public class MacroStackerApp {
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
