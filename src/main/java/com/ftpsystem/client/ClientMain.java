package com.ftpsystem.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.ftpsystem.client.gui.ClientMainFrame;

import javax.swing.*;

/**
 * Diem khoi dau chay FTP Desktop Client
 */
public class ClientMain {
    public static void main(String[] args) {
        // Ap dung giao dien FlatLaf Light hien dai sang sua
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ClientMainFrame frame = new ClientMainFrame();
            frame.setVisible(true);
        });
    }
}
