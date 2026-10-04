package com.ftpsystem.server.gui;

import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.common.NetworkPacketInfo;
import com.ftpsystem.common.TransferLog;
import com.ftpsystem.database.DatabaseManager;
import com.ftpsystem.database.LogDAO;
import com.ftpsystem.server.ClientControlHandler;
import com.ftpsystem.server.FtpServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao dien Bang Dieu Khien May Chu FTP Server 2 Kenh (RFC 959)
 * Tich hop Giam sat Luong, Mo phong Tang Van Chuyen / Tang Ung Dung
 * va Quan tri He thong Co so du lieu MySQL.
 */
public class ServerDashboardFrame extends JFrame implements FtpServer.ServerEventListener {
    private final FtpServer ftpServer = FtpServer.getInstance();

    // UI Controls
    private JLabel lblServerStatus;
    private JLabel lblActiveClients;
    private JLabel lblTransferred;
    private JLabel lblThroughput;
    private JLabel lblDbStatus;

    private JButton btnStartStop;
    private JButton btnTestDb;
    private JButton btnWebPortal;
    private JButton btnUserMgmt;

    private JTextField txtPort;

    // Tables
    private JTable clientTable;
    private DefaultTableModel clientTableModel;

    private JTable packetTable;
    private DefaultTableModel packetTableModel;

    private JTable logTable;
    private DefaultTableModel logTableModel;

    private JTextArea txtConsoleLog;
    private NetworkTopologyPanel topologyPanel;

    public ServerDashboardFrame() {
        super("FTP Server Dashboard & Protocol Simulator (RFC 959) - Java 23");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        ftpServer.addListener(this);

        initUI();
        updateDbStatusBadge();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // 1. TOP HEADER & CONTROL BAR
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(35, 40, 50));

        // Logo & Title
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);
        JLabel lblTitle = new JLabel("FTP SERVER 2 KÊNH (RFC 959) - PROTOCOL SIMULATOR");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(Color.WHITE);
        titlePanel.add(lblTitle);

        lblServerStatus = new JLabel(" [ĐÃ DỪNG] ");
        lblServerStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblServerStatus.setOpaque(true);
        lblServerStatus.setBackground(new Color(220, 53, 69));
        lblServerStatus.setForeground(Color.WHITE);
        lblServerStatus.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        titlePanel.add(lblServerStatus);

        topPanel.add(titlePanel, BorderLayout.WEST);

        // Control buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);

        JLabel lblPortLabel = new JLabel("Cổng TCP:");
        lblPortLabel.setForeground(Color.WHITE);
        txtPort = new JTextField(String.valueOf(FtpConstants.DEFAULT_CONTROL_PORT), 5);

        btnStartStop = new JButton("Khởi Động Server");
        btnStartStop.setBackground(new Color(40, 167, 69));
        btnStartStop.setForeground(Color.WHITE);
        btnStartStop.setFont(new Font("Segoe UI", Font.BOLD, 12));

        btnTestDb = new JButton("MySQL Database");
        btnWebPortal = new JButton("Web Portal (Port 8080)");
        btnUserMgmt = new JButton("Quản Lý Users");

        actionPanel.add(lblPortLabel);
        actionPanel.add(txtPort);
        actionPanel.add(btnStartStop);
        actionPanel.add(btnTestDb);
        actionPanel.add(btnWebPortal);
        actionPanel.add(btnUserMgmt);

        topPanel.add(actionPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 2. SUMMARY CARDS
        JPanel summaryPanel = new JPanel(new GridLayout(1, 5, 10, 10));
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        summaryPanel.add(createCard("Kênh Điều Khiển (TCP)", "Port " + FtpConstants.DEFAULT_CONTROL_PORT, new Color(52, 152, 219)));
        lblActiveClients = new JLabel("0 kết nối", JLabel.CENTER);
        summaryPanel.add(createCardComponent("Clients Đang Kết Nối", lblActiveClients, new Color(46, 204, 113)));
        lblTransferred = new JLabel("0 KB", JLabel.CENTER);
        summaryPanel.add(createCardComponent("Tổng Dữ Liệu Truyền", lblTransferred, new Color(155, 89, 182)));
        lblThroughput = new JLabel("0.0 KB/s", JLabel.CENTER);
        summaryPanel.add(createCardComponent("Băng Thông Tức Thời", lblThroughput, new Color(230, 126, 34)));
        lblDbStatus = new JLabel("MySQL: Checking", JLabel.CENTER);
        summaryPanel.add(createCardComponent("Cơ Sở Dữ Liệu", lblDbStatus, new Color(52, 73, 94)));

        // 3. TABBED PANE CHÍNH
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Tab 1: Danh sach Client & So do mang
        JPanel tab1 = new JPanel(new BorderLayout(10, 10));
        tab1.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] clientCols = {"Địa Chỉ IP", "Port Client", "Tài Khoản", "Quyền Hạn", "Trạng Thái"};
        clientTableModel = new DefaultTableModel(clientCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        clientTable = new JTable(clientTableModel);
        clientTable.setRowHeight(24);

        JPanel clientListPanel = new JPanel(new BorderLayout());
        clientListPanel.setBorder(BorderFactory.createTitledBorder("Danh Sách Client Đang Kết Nối Trực Tiếp"));
        clientListPanel.add(new JScrollPane(clientTable), BorderLayout.CENTER);

        JPanel clientActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnKick = new JButton("Ngắt Kết Nối Client Được Chọn");
        clientActions.add(btnKick);
        clientListPanel.add(clientActions, BorderLayout.SOUTH);

        // So do mang
        topologyPanel = new NetworkTopologyPanel();
        JSplitPane splitTab1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, clientListPanel, topologyPanel);
        splitTab1.setResizeWeight(0.5);
        tab1.add(splitTab1, BorderLayout.CENTER);

        // Tab 2: Mo phong Goi tin Tang Mang & Tang Van Chuyen
        JPanel tab2 = new JPanel(new BorderLayout(10, 10));
        tab2.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel packetNote = new JLabel("<html><b>Lưu ý kỹ thuật:</b> java.net.Socket che giấu cờ TCP với ứng dụng nên các cờ SYN/ACK/PSH/FIN ở đây được "
                + "<b>suy diễn từ vòng đời Socket</b> (accept/connect = bắt tay 3 bước, đóng Socket = FIN), không phải bắt gói tin thật từ card mạng.</html>");
        packetNote.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        tab2.add(packetNote, BorderLayout.NORTH);

        String[] packetCols = {"Thời Gian", "Tầng Mạng", "Giao Thức", "Cờ TCP (suy diễn)", "Nguồn -> Đích", "Kích Thước", "Chi Tiết Lệnh / Nội Dung"};
        packetTableModel = new DefaultTableModel(packetCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        packetTable = new JTable(packetTableModel);
        packetTable.setRowHeight(22);
        tab2.add(new JScrollPane(packetTable), BorderLayout.CENTER);

        // Tab 3: Nhat ky Hoat dong MySQL
        JPanel tab3 = new JPanel(new BorderLayout(10, 10));
        tab3.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] logCols = {"Thời Gian", "Tài Khoản", "IP Client", "Hành Động", "Tên Tệp Tin", "Dung Lượng", "Tốc Độ", "Trạng Thái"};
        logTableModel = new DefaultTableModel(logCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        logTable = new JTable(logTableModel);
        logTable.setRowHeight(24);

        JPanel logTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefreshLogs = new JButton("Làm Mới Nhật Ký (MySQL)");
        logTop.add(btnRefreshLogs);
        tab3.add(logTop, BorderLayout.NORTH);
        tab3.add(new JScrollPane(logTable), BorderLayout.CENTER);

        tabbedPane.addTab("Giám Sát Trực Tiếp & Cấu Trúc Mạng", tab1);
        tabbedPane.addTab("Mô Phỏng Gói Tin (Transport / App Layer)", tab2);
        tabbedPane.addTab("Nhật Ký Truyền Tải (MySQL Audit Logs)", tab3);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.add(summaryPanel, BorderLayout.NORTH);
        centerContainer.add(tabbedPane, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        // 4. BOTTOM CONSOLE LOG
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Nhật Ký Lệnh Giao Thức (Console Terminal RFC 959)"));
        txtConsoleLog = new JTextArea(7, 50);
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setBackground(new Color(20, 24, 30));
        txtConsoleLog.setForeground(new Color(46, 204, 113));
        txtConsoleLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        bottomPanel.add(new JScrollPane(txtConsoleLog), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // EVENT LISTENERS
        btnStartStop.addActionListener(e -> toggleServer());
        btnTestDb.addActionListener(e -> testDatabase());
        btnWebPortal.addActionListener(e -> openWebPortal());
        btnUserMgmt.addActionListener(e -> new UserManagementDialog(this).setVisible(true));
        btnRefreshLogs.addActionListener(e -> reloadLogs());
        btnKick.addActionListener(e -> kickSelectedClient());

        // Tu dong khoi dong Server luon khi mo giao dien de tien loi
        SwingUtilities.invokeLater(this::toggleServer);
    }

    private JPanel createCard(String title, String value, Color color) {
        JLabel valLabel = new JLabel(value, JLabel.CENTER);
        return createCardComponent(title, valLabel, color);
    }

    private JPanel createCardComponent(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setBackground(new Color(245, 248, 252));

        JLabel lblT = new JLabel(title, JLabel.CENTER);
        lblT.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblT.setForeground(new Color(100, 110, 120));

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        valueLabel.setForeground(color.darker());

        card.add(lblT, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void toggleServer() {
        if (!ftpServer.isRunning()) {
            int port = FtpConstants.DEFAULT_CONTROL_PORT;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (Exception ignored) {}

            boolean ok = ftpServer.start(port);
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Không thể khởi động Server trên cổng " + port + ". Có thể cổng đang bị ứng dụng khác chiếm dụng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            ftpServer.stop();
        }
    }

    private void testDatabase() {
        DatabaseManager db = DatabaseManager.getInstance();
        boolean ok = db.checkAndInitConnection();
        updateDbStatusBadge();
        if (ok) {
            JOptionPane.showMessageDialog(this, "Kết nối thành công đến MySQL Database: " + db.getDbName() + " (Port 3306)", "MySQL OK", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Chưa kết nối được MySQL: " + db.getLastError() + "\nHệ thống đang dùng Chế độ Dự phòng In-Memory để không bị lỗi.", "Thông báo", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updateDbStatusBadge() {
        DatabaseManager db = DatabaseManager.getInstance();
        if (!db.isMockMode()) {
            lblDbStatus.setText("MySQL: ĐÃ KẾT NỐI");
            lblDbStatus.setForeground(new Color(40, 167, 69));
        } else {
            lblDbStatus.setText("MySQL: DỰ PHÒNG");
            lblDbStatus.setForeground(new Color(230, 126, 34));
        }
    }

    private void openWebPortal() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + FtpConstants.DEFAULT_WEB_PORT));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Vui lòng mở trình duyệt và truy cập: http://localhost:" + FtpConstants.DEFAULT_WEB_PORT, "Web Portal", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void reloadLogs() {
        logTableModel.setRowCount(0);
        List<TransferLog> logs = LogDAO.getInstance().getRecentLogs(50);
        for (TransferLog l : logs) {
            logTableModel.addRow(new Object[]{
                    l.getCreatedAt() != null ? l.getCreatedAt().toString() : "",
                    l.getUsername(),
                    l.getClientIp(),
                    l.getAction(),
                    l.getFilename() != null ? l.getFilename() : "-",
                    FileItem.fromFile(new java.io.File(l.getFilename() != null ? l.getFilename() : "")).getFormattedSize(),
                    String.format("%.2f KB/s", l.getSpeedKbps()),
                    l.getStatus()
            });
        }
    }

    private void kickSelectedClient() {
        int row = clientTable.getSelectedRow();
        if (row >= 0 && row < ftpServer.getActiveClients().size()) {
            ClientControlHandler client = ftpServer.getActiveClients().get(row);
            ftpServer.kickClient(client);
        }
    }

    private void exitApp() {
        int res = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn tắt FTP Server?", "Thoát", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            ftpServer.stop();
            System.exit(0);
        }
    }

    // INTERFACE LISTENER IMPLEMENTATIONS
    @Override
    public void onServerStateChanged(boolean running, int port) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblServerStatus.setText(" [ĐANG CHẠY - PORT " + port + "] ");
                lblServerStatus.setBackground(new Color(40, 167, 69));
                btnStartStop.setText("Dừng Server");
                btnStartStop.setBackground(new Color(220, 53, 69));
                txtPort.setEnabled(false);
            } else {
                lblServerStatus.setText(" [ĐÃ DỪNG] ");
                lblServerStatus.setBackground(new Color(220, 53, 69));
                btnStartStop.setText("Khởi Động Server");
                btnStartStop.setBackground(new Color(40, 167, 69));
                txtPort.setEnabled(true);
            }
            if (topologyPanel != null) topologyPanel.repaint();
        });
    }

    @Override
    public void onClientConnected(ClientControlHandler client) {
        SwingUtilities.invokeLater(this::updateClientTable);
    }

    @Override
    public void onClientDisconnected(ClientControlHandler client) {
        SwingUtilities.invokeLater(this::updateClientTable);
    }

    private void updateClientTable() {
        clientTableModel.setRowCount(0);
        for (ClientControlHandler c : ftpServer.getActiveClients()) {
            clientTableModel.addRow(new Object[]{
                    c.getClientIp(),
                    c.getClientPort(),
                    c.getCurrentUser() != null ? c.getCurrentUser().getUsername() : "(Đang chờ)",
                    c.getCurrentUser() != null ? c.getCurrentUser().getRole() : "-",
                    c.isLoggedIn() ? "Đã Xác Thực" : "Đang Bắt Tay"
            });
        }
        if (topologyPanel != null) topologyPanel.repaint();
    }

    @Override
    public void onLog(String message, String type) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtConsoleLog.append(String.format("[%s] [%s] %s\n", time, type, message));
            txtConsoleLog.setCaretPosition(txtConsoleLog.getDocument().getLength());
        });
    }

    @Override
    public void onPacket(NetworkPacketInfo packet) {
        SwingUtilities.invokeLater(() -> {
            packetTableModel.insertRow(0, new Object[]{
                    packet.getTimestamp(),
                    packet.getLayer(),
                    packet.getProtocol(),
                    packet.getFlags(),
                    packet.getSourceAddress() + " -> " + packet.getDestAddress(),
                    packet.getPayloadSize() + " B",
                    packet.getContent()
            });
            if (packetTableModel.getRowCount() > 300) {
                packetTableModel.removeRow(packetTableModel.getRowCount() - 1);
            }
            if (topologyPanel != null) topologyPanel.triggerPacketAnimation();
        });
    }

    @Override
    public void onMetrics(int activeClientCount, long totalBytes, double throughputKbps) {
        SwingUtilities.invokeLater(() -> {
            lblActiveClients.setText(activeClientCount + " kết nối");
            if (totalBytes < 1024 * 1024) {
                lblTransferred.setText(String.format("%.2f KB", totalBytes / 1024.0));
            } else {
                lblTransferred.setText(String.format("%.2f MB", totalBytes / (1024.0 * 1024.0)));
            }
            lblThroughput.setText(String.format("%.2f KB/s", throughputKbps));
        });
    }

    /**
     * Panel truc quan hoa So do Mang (Network Topology)
     */
    private class NetworkTopologyPanel extends JPanel {
        private boolean packetPulse = false;

        public NetworkTopologyPanel() {
            setPreferredSize(new Dimension(800, 220));
            setBackground(new Color(28, 33, 43));
            setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(60, 70, 85)),
                    "Mô Phỏng Cấu Trúc Mạng & Hoạt Động Giao Thức (Network Topology & Architecture)",
                    0, 0, new Font("Segoe UI", Font.BOLD, 12), Color.WHITE));
        }

        public void triggerPacketAnimation() {
            packetPulse = true;
            repaint();
            Timer t = new Timer(300, e -> {
                packetPulse = false;
                repaint();
            });
            t.setRepeats(false);
            t.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Toa do cac Node trong mang
            int clientX = 120, clientY = h / 2;
            int serverX = w / 2, serverY = h / 2;
            int dbX = w - 140, dbY = h / 2 - 40;
            int webX = w - 140, webY = h / 2 + 50;

            // 1. Ve duong truyen Kênh Điều Khiển (Control Channel Port 2121)
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(new Color(52, 152, 219));
            g2.drawLine(clientX + 40, clientY - 15, serverX - 50, serverY - 15);

            // 2. Ve duong truyen Kênh Dữ Liệu (Data Channel PASV/PORT 300xx)
            g2.setColor(new Color(230, 126, 34));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6}, 0));
            g2.drawLine(clientX + 40, clientY + 15, serverX - 50, serverY + 15);

            // 3. Duong ket noi MySQL & Web
            g2.setColor(new Color(155, 89, 182));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(serverX + 50, serverY - 15, dbX - 40, dbY);
            g2.setColor(new Color(46, 204, 113));
            g2.drawLine(serverX + 50, serverY + 15, webX - 40, webY);

            // Ve chu thich duong truyen
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(52, 152, 219));
            g2.drawString("TCP Kênh 1: Control (Port 2121) [Lệnh/Phản hồi]", clientX + 70, clientY - 22);
            g2.setColor(new Color(230, 126, 34));
            g2.drawString("TCP Kênh 2: Data (PASV/PORT) [Byte Stream File]", clientX + 70, clientY + 32);

            // Ve hieu ung goi tin di chuyen
            if (packetPulse) {
                g2.setColor(Color.YELLOW);
                g2.fillOval((clientX + serverX) / 2 - 8, clientY - 23, 16, 16);
            }

            // Ve cac Node (Hop chu nhat bo goc)
            drawNode(g2, clientX, clientY, "FTP CLIENT", "Desktop/CLI", new Color(41, 128, 185));
            drawNode(g2, serverX, serverY, "FTP SERVER", "Core Port 2121", ftpServer.isRunning() ? new Color(39, 174, 96) : new Color(192, 57, 43));
            drawNode(g2, dbX, dbY, "MYSQL DB", "Users / Audit", new Color(142, 68, 173));
            drawNode(g2, webX, webY, "WEB PORTAL", "HTTP Port 8080", new Color(22, 160, 133));
        }

        private void drawNode(Graphics2D g2, int cx, int cy, String title, String sub, Color color) {
            int nw = 100, nh = 50;
            int x = cx - nw / 2;
            int y = cy - nh / 2;

            g2.setColor(color);
            g2.fillRoundRect(x, y, nw, nh, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, nw, nh, 12, 12);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title) / 2, cy - 3);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            FontMetrics fm2 = g2.getFontMetrics();
            g2.drawString(sub, cx - fm2.stringWidth(sub) / 2, cy + 14);
        }
    }
}
