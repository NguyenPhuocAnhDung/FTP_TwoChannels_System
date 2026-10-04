package com.ftpsystem.server;

import com.formdev.flatlaf.FlatDarkLaf;
import com.ftpsystem.server.gui.ServerDashboardFrame;

import javax.swing.*;

/**
 * Diem khoi dau chay FTP Server
 */
public class ServerMain {
    public static void main(String[] args) {
        // Ap dung giao dien FlatLaf Dark hien dai
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerDashboardFrame frame = new ServerDashboardFrame();
            frame.setVisible(true);
        });
    }
}
