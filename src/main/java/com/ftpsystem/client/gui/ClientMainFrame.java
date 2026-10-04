package com.ftpsystem.client.gui;

import com.ftpsystem.client.FtpClientCore;
import com.ftpsystem.client.TransferProgressCallback;
import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.common.I18nManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao dien Desktop FTP Client 2 Pane (Phong cach FileZilla)
 * Ho tro:
 * - Keno Dieu Khien va Kenh Du Lieu doc lap (PASV / PORT)
 * - Thanh tien do Upload / Download voi toc do thoi gian thuc
 * - Tinh nang tiep tuc tai do dang (Resumable - REST)
 * - Xac thuc ma Checksum SHA-256 toan ven
 * - Chuyen doi ngon ngu Tieng Viet / English
 */
public class ClientMainFrame extends JFrame implements FtpClientCore.CommandLogListener {
    private final FtpClientCore ftpClient = new FtpClientCore();
    private final I18nManager i18n = I18nManager.getInstance();

    // Controls
    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JComboBox<String> cbMode;
    private JButton btnConnect;
    private JButton btnLangToggle;

    // Panes
    private JTextField txtLocalPath;
    private JTable localTable;
    private DefaultTableModel localTableModel;
    private File currentLocalDir = new File(System.getProperty("user.home"));

    private JTextField txtRemotePath;
    private JTable remoteTable;
    private DefaultTableModel remoteTableModel;

    // Actions
    private JButton btnUpload;
    private JButton btnDownload;
    private JButton btnDeleteRemote;
    private JButton btnMkdirRemote;
    private JCheckBox chkResume;

    // Progress
    private JProgressBar progressBar;
    private JLabel lblProgressInfo;
    private JLabel lblSpeed;
    private JLabel lblChecksum;

    // Terminal
    private JTextArea txtConsoleLog;

    public ClientMainFrame() {
        super();
        setTitle(i18n.getString("app.client_title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 750);
        setLocationRelativeTo(null);

        ftpClient.addLogListener(this);
        i18n.addListener(this::updateTexts);

        initUI();
        loadLocalFiles(currentLocalDir);
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // 1. TOP QUICK CONNECT BAR
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        topPanel.setBackground(new Color(40, 45, 55));

        txtHost = new JTextField("127.0.0.1", 9);
        txtPort = new JTextField(String.valueOf(FtpConstants.DEFAULT_CONTROL_PORT), 4);
        txtUser = new JTextField("admin", 8);
        txtPass = new JPasswordField("admin123", 8);
        cbMode = new JComboBox<>(new String[]{"PASV (Passive)", "PORT (Active)"});

        btnConnect = new JButton(i18n.getString("btn.connect"));
        btnConnect.setBackground(new Color(40, 167, 69));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnRegister = new JButton("Đăng Ký");
        btnRegister.setBackground(new Color(155, 89, 182));
        btnRegister.setForeground(Color.WHITE);

        JButton btnRooms = new JButton("🏢 Quản Lý Phòng");
        btnRooms.setBackground(new Color(230, 126, 34));
        btnRooms.setForeground(Color.WHITE);
        btnRooms.setFont(new Font("Segoe UI", Font.BOLD, 12));

        btnLangToggle = new JButton("VI / EN");
        btnLangToggle.setBackground(new Color(52, 152, 219));
        btnLangToggle.setForeground(Color.WHITE);

        topPanel.add(createLabel("Host:", Color.WHITE)); topPanel.add(txtHost);
        topPanel.add(createLabel("Port:", Color.WHITE)); topPanel.add(txtPort);
        topPanel.add(createLabel("User:", Color.WHITE)); topPanel.add(txtUser);
        topPanel.add(createLabel("Pass:", Color.WHITE)); topPanel.add(txtPass);
        topPanel.add(createLabel("Mode:", Color.WHITE)); topPanel.add(cbMode);
        topPanel.add(btnConnect);
        topPanel.add(btnRegister);
        topPanel.add(btnRooms);
        topPanel.add(btnLangToggle);
        add(topPanel, BorderLayout.NORTH);

        btnRegister.addActionListener(e -> showRegisterDialog());
        btnRooms.addActionListener(e -> {
            if (!ftpClient.isConnected() || !ftpClient.isLoggedIn()) {
                JOptionPane.showMessageDialog(this, "Vui lòng kết nối và đăng nhập trước khi quản lý phòng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            new RoomManagementDialog(this, ftpClient, txtUser.getText().trim(), this::loadRemoteFiles).setVisible(true);
        });

        // 2. CENTER DUAL-PANE FILE EXPLORER
        JPanel leftPane = createLocalPane();
        JPanel rightPane = createRemotePane();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
        splitPane.setResizeWeight(0.5);

        // Action Buttons in Middle
        JPanel actionToolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 6));
        btnUpload = new JButton(i18n.getString("btn.upload") + " >>");
        btnDownload = new JButton("<< " + i18n.getString("btn.download"));
        btnDeleteRemote = new JButton(i18n.getString("btn.delete"));
        btnMkdirRemote = new JButton(i18n.getString("btn.new_folder"));
        chkResume = new JCheckBox("Tiếp tục tải dở (REST - Resumable)", false);

        btnUpload.setBackground(new Color(41, 128, 185)); btnUpload.setForeground(Color.WHITE);
        btnDownload.setBackground(new Color(39, 174, 96)); btnDownload.setForeground(Color.WHITE);

        actionToolbar.add(btnUpload);
        actionToolbar.add(btnDownload);
        actionToolbar.add(btnDeleteRemote);
        actionToolbar.add(btnMkdirRemote);
        actionToolbar.add(chkResume);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(actionToolbar, BorderLayout.NORTH);
        centerPanel.add(splitPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 3. BOTTOM PROGRESS & RFC 959 CONSOLE LOG
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Progress row
        JPanel progressPanel = new JPanel(new BorderLayout(10, 5));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Tiến Độ Truyền Tải (Data Channel Throughput)"));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 22));

        lblProgressInfo = new JLabel("Sẵn sàng.");
        lblSpeed = new JLabel("Tốc độ: 0 KB/s", JLabel.RIGHT);
        lblChecksum = new JLabel("SHA-256 Checksum: -");
        lblChecksum.setFont(new Font("Consolas", Font.PLAIN, 11));
        lblChecksum.setForeground(new Color(41, 128, 185));

        JPanel progSub = new JPanel(new BorderLayout());
        progSub.add(lblProgressInfo, BorderLayout.WEST);
        progSub.add(lblSpeed, BorderLayout.EAST);

        progressPanel.add(progSub, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(lblChecksum, BorderLayout.SOUTH);

        // Console Log
        txtConsoleLog = new JTextArea(6, 40);
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setBackground(new Color(25, 30, 38));
        txtConsoleLog.setForeground(new Color(241, 196, 15));
        txtConsoleLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(txtConsoleLog);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật Ký Lệnh & Phản Hồi RFC 959 (Control Stream Port 2121)"));

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, progressPanel, logScroll);
        bottomSplit.setResizeWeight(0.3);
        bottomPanel.add(bottomSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // 4. EVENT BINDINGS
        btnConnect.addActionListener(e -> toggleConnect());
        btnLangToggle.addActionListener(e -> i18n.toggleLanguage());
        btnUpload.addActionListener(e -> doUpload());
        btnDownload.addActionListener(e -> doDownload());
        btnDeleteRemote.addActionListener(e -> doDeleteRemote());
        btnMkdirRemote.addActionListener(e -> doMkdirRemote());
        cbMode.addActionListener(e -> updateDataMode());

        localTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = localTable.getSelectedRow();
                    if (row >= 0) {
                        String name = (String) localTableModel.getValueAt(row, 0);
                        if (".. (Lên thư mục cha)".equals(name)) {
                            File parent = currentLocalDir.getParentFile();
                            if (parent != null) loadLocalFiles(parent);
                        } else {
                            File chosen = new File(currentLocalDir, name);
                            if (chosen.isDirectory()) loadLocalFiles(chosen);
                        }
                    }
                }
            }
        });

        remoteTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = remoteTable.getSelectedRow();
                    if (row >= 0) {
                        String name = (String) remoteTableModel.getValueAt(row, 0);
                        if ("..".equals(name)) {
                            try {
                                ftpClient.cdup();
                                loadRemoteFiles();
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(ClientMainFrame.this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        } else {
                            String type = (String) remoteTableModel.getValueAt(row, 2);
                            if ("<DIR>".equals(type) || "Thư mục".equals(type)) {
                                try {
                                    ftpClient.cwd(name);
                                    loadRemoteFiles();
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(ClientMainFrame.this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private JPanel createLocalPane() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Máy Cục Bộ (Local Machine)"));

        JPanel top = new JPanel(new BorderLayout(5, 5));
        txtLocalPath = new JTextField(currentLocalDir.getAbsolutePath());
        txtLocalPath.setEditable(false);
        JButton btnUp = new JButton("↑ Lên");
        JButton btnRefresh = new JButton("Làm mới");
        btnUp.addActionListener(e -> {
            File p = currentLocalDir.getParentFile();
            if (p != null) loadLocalFiles(p);
        });
        btnRefresh.addActionListener(e -> loadLocalFiles(currentLocalDir));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.add(btnUp); btns.add(btnRefresh);
        top.add(txtLocalPath, BorderLayout.CENTER);
        top.add(btns, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        String[] cols = {"Tên Tệp Tin", "Kích Thước", "Loại", "Ngày Sửa Đổi"};
        localTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        localTable = new JTable(localTableModel);
        localTable.setRowHeight(22);
        panel.add(new JScrollPane(localTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRemotePane() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Máy Chủ FTP (Remote Server)"));

        JPanel top = new JPanel(new BorderLayout(5, 5));
        txtRemotePath = new JTextField("/");
        txtRemotePath.setEditable(false);
        JButton btnUp = new JButton("↑ Lên");
        JButton btnRefresh = new JButton("Làm mới");
        btnUp.addActionListener(e -> {
            try {
                ftpClient.cdup();
                loadRemoteFiles();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });
        btnRefresh.addActionListener(e -> loadRemoteFiles());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.add(btnUp); btns.add(btnRefresh);
        top.add(txtRemotePath, BorderLayout.CENTER);
        top.add(btns, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        String[] cols = {"Tên Tệp Tin", "Kích Thước", "Loại", "Phân Quyền"};
        remoteTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        remoteTable = new JTable(remoteTableModel);
        remoteTable.setRowHeight(22);
        panel.add(new JScrollPane(remoteTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadLocalFiles(File dir) {
        currentLocalDir = dir;
        txtLocalPath.setText(dir.getAbsolutePath());
        localTableModel.setRowCount(0);

        if (dir.getParentFile() != null) {
            localTableModel.addRow(new Object[]{".. (Lên thư mục cha)", "", "Thư mục", ""});
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                FileItem item = FileItem.fromFile(f);
                localTableModel.addRow(new Object[]{
                        item.getName(),
                        item.getFormattedSize(),
                        item.isDirectory() ? "Thư mục" : "Tệp tin",
                        item.getFormattedDate()
                });
            }
        }
    }

    private void loadRemoteFiles() {
        if (!ftpClient.isConnected() || !ftpClient.isLoggedIn()) return;
        new Thread(() -> {
            try {
                String cur = ftpClient.pwd();
                SwingUtilities.invokeLater(() -> txtRemotePath.setText(cur));

                List<FileItem> list = ftpClient.listFiles();
                SwingUtilities.invokeLater(() -> {
                    remoteTableModel.setRowCount(0);
                    remoteTableModel.addRow(new Object[]{"..", "", "Thư mục", "drwxr-xr-x"});
                    for (FileItem item : list) {
                        remoteTableModel.addRow(new Object[]{
                                item.getName(),
                                item.getFormattedSize(),
                                item.isDirectory() ? "Thư mục" : "Tệp tin",
                                item.getPermissions()
                        });
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi lấy danh sách file từ server: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void showRegisterDialog() {
        JTextField txtNewUser = new JTextField(12);
        JPasswordField txtNewPass = new JPasswordField(12);
        JPasswordField txtConfirmPass = new JPasswordField(12);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Tên đăng nhập mới:")); panel.add(txtNewUser);
        panel.add(new JLabel("Mật khẩu:")); panel.add(txtNewPass);
        panel.add(new JLabel("Xác nhận mật khẩu:")); panel.add(txtConfirmPass);

        int res = JOptionPane.showConfirmDialog(this, panel, "Đăng Ký Tài Khoản Client Mới", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String u = txtNewUser.getText().trim();
            String p = new String(txtNewPass.getPassword());
            String cp = new String(txtConfirmPass.getPassword());

            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ tài khoản và mật khẩu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!p.equals(cp)) {
                JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Gui lenh dang ky qua Socket TCP
            new Thread(() -> {
                try {
                    String host = txtHost.getText().trim();
                    int port = Integer.parseInt(txtPort.getText().trim());

                    // Tao ket noi tam thoi de gui lenh REGISTER
                    FtpClientCore tempClient = new FtpClientCore();
                    boolean conn = tempClient.connect(host, port);
                    if (!conn) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không thể kết nối đến Server!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        return;
                    }

                    boolean registered = tempClient.register(u, p);
                    tempClient.disconnect();

                    SwingUtilities.invokeLater(() -> {
                        if (registered) {
                            JOptionPane.showMessageDialog(this, "Đăng ký thành công tài khoản '" + u + "'!\nBạn có thể đăng nhập ngay bây giờ.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                            txtUser.setText(u);
                            txtPass.setText(p);
                        } else {
                            JOptionPane.showMessageDialog(this, "Tên tài khoản '" + u + "' đã tồn tại trên hệ thống. Vui lòng chọn tên khác!", "Trùng Lặp", JOptionPane.WARNING_MESSAGE);
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi đăng ký: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        }
    }

    private void toggleConnect() {
        if (!ftpClient.isConnected()) {
            String host = txtHost.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            String user = txtUser.getText().trim();
            String pass = new String(txtPass.getPassword());

            updateDataMode();

            new Thread(() -> {
                try {
                    boolean ok = ftpClient.connect(host, port);
                    if (!ok) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không thể kết nối đến máy chủ!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        return;
                    }

                    boolean auth = ftpClient.login(user, pass);
                    SwingUtilities.invokeLater(() -> {
                        if (auth) {
                            btnConnect.setText(i18n.getString("btn.disconnect"));
                            btnConnect.setBackground(new Color(220, 53, 69));
                            loadRemoteFiles();
                        } else {
                            JOptionPane.showMessageDialog(this, "Đăng nhập thất bại. Kiểm tra lại tài khoản!", "Lỗi Xác Thực", JOptionPane.ERROR_MESSAGE);
                            ftpClient.disconnect();
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        } else {
            ftpClient.disconnect();
            btnConnect.setText(i18n.getString("btn.connect"));
            btnConnect.setBackground(new Color(40, 167, 69));
            remoteTableModel.setRowCount(0);
            txtRemotePath.setText("/");
        }
    }

    private void updateDataMode() {
        if (cbMode.getSelectedIndex() == 0) {
            ftpClient.setDataMode(FtpConstants.DataMode.PASSIVE);
        } else {
            ftpClient.setDataMode(FtpConstants.DataMode.ACTIVE);
        }
    }

    private void doUpload() {
        int row = localTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp tin ở khung bên trái (Local) để Upload!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fname = (String) localTableModel.getValueAt(row, 0);
        File localFile = new File(currentLocalDir, fname);
        if (localFile.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tệp tin, không chọn thư mục để upload!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long offset = 0;
        if (chkResume.isSelected()) {
            offset = ftpClient.getFileSize(fname); // Tiep tuc tai tu kich thuoc hien tai tren server
        }

        progressBar.setValue(0);
        lblChecksum.setText("SHA-256 Checksum: Đang tính toán...");
        lblProgressInfo.setText("Đang Upload: " + fname + " (Bắt đầu từ byte: " + offset + ")...");

        long finalOffset = offset;
        ftpClient.uploadFile(localFile, fname, finalOffset, new TransferProgressCallback() {
            @Override
            public void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(percent);
                    lblSpeed.setText(String.format("Tốc độ: %.2f KB/s", speedKbps));
                    lblProgressInfo.setText(String.format("Upload: %s [%d%%] (%s / %s)",
                            fname, percent,
                            FileItem.fromFile(new File(String.valueOf(transferredBytes))).getFormattedSize(),
                            FileItem.fromFile(localFile).getFormattedSize()));
                });
            }

            @Override
            public void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    lblSpeed.setText(String.format("Trung bình: %.2f KB/s", avgSpeedKbps));
                    lblProgressInfo.setText("Upload hoàn tất thành công trong " + durationMs + " ms!");
                    lblChecksum.setText("SHA-256 Checksum Toàn Vẹn: " + checksumSHA256);
                    loadRemoteFiles();
                    JOptionPane.showMessageDialog(ClientMainFrame.this,
                            "Upload hoàn tất!\nFile: " + fname + "\nDung lượng: " + totalBytes + " B\nSHA-256: " + checksumSHA256,
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                });
            }

            @Override
            public void onError(String errorMessage) {
                SwingUtilities.invokeLater(() -> {
                    lblProgressInfo.setText("Upload thất bại: " + errorMessage);
                    JOptionPane.showMessageDialog(ClientMainFrame.this, "Lỗi Upload: " + errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void doDownload() {
        int row = remoteTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp tin ở khung bên phải (Remote) để Download!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fname = (String) remoteTableModel.getValueAt(row, 0);
        String type = (String) remoteTableModel.getValueAt(row, 2);
        if ("Thư mục".equals(type) || "..".equals(fname)) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tệp tin, không chọn thư mục!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File localTarget = new File(currentLocalDir, fname);
        long offset = 0;
        if (chkResume.isSelected() && localTarget.exists()) {
            offset = localTarget.length();
        }

        progressBar.setValue(0);
        lblChecksum.setText("SHA-256 Checksum: Đang tính toán...");
        lblProgressInfo.setText("Đang Download: " + fname + " (Bắt đầu từ byte: " + offset + ")...");

        long finalOffset = offset;
        ftpClient.downloadFile(fname, localTarget, finalOffset, new TransferProgressCallback() {
            @Override
            public void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(percent);
                    lblSpeed.setText(String.format("Tốc độ: %.2f KB/s", speedKbps));
                    lblProgressInfo.setText(String.format("Download: %s [%d%%]", fname, percent));
                });
            }

            @Override
            public void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    lblSpeed.setText(String.format("Trung bình: %.2f KB/s", avgSpeedKbps));
                    lblProgressInfo.setText("Download hoàn tất trong " + durationMs + " ms!");
                    lblChecksum.setText("SHA-256 Checksum Toàn Vẹn: " + checksumSHA256);
                    loadLocalFiles(currentLocalDir);
                    JOptionPane.showMessageDialog(ClientMainFrame.this,
                            "Download hoàn tất!\nFile: " + fname + "\nDung lượng: " + totalBytes + " B\nSHA-256: " + checksumSHA256,
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                });
            }

            @Override
            public void onError(String errorMessage) {
                SwingUtilities.invokeLater(() -> {
                    lblProgressInfo.setText("Download thất bại: " + errorMessage);
                    JOptionPane.showMessageDialog(ClientMainFrame.this, "Lỗi Download: " + errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void doDeleteRemote() {
        int row = remoteTable.getSelectedRow();
        if (row < 0) return;
        String fname = (String) remoteTableModel.getValueAt(row, 0);
        if ("..".equals(fname)) return;

        int conf = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa \"" + fname + "\" trên Server?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    boolean ok = ftpClient.deleteFile(fname);
                    SwingUtilities.invokeLater(() -> {
                        if (ok) {
                            loadRemoteFiles();
                        } else {
                            JOptionPane.showMessageDialog(this, "Không thể xóa (có thể do quyền hạn hoặc file đang mở)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
                }
            }).start();
        }
    }

    private void doMkdirRemote() {
        String dirName = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới trên Server:");
        if (dirName != null && !dirName.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    boolean ok = ftpClient.makeDir(dirName.trim());
                    SwingUtilities.invokeLater(() -> {
                        if (ok) loadRemoteFiles();
                        else JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
                }
            }).start();
        }
    }

    private void updateTexts() {
        setTitle(i18n.getString("app.client_title"));
        btnConnect.setText(ftpClient.isConnected() ? i18n.getString("btn.disconnect") : i18n.getString("btn.connect"));
        btnUpload.setText(i18n.getString("btn.upload") + " >>");
        btnDownload.setText("<< " + i18n.getString("btn.download"));
        btnDeleteRemote.setText(i18n.getString("btn.delete"));
        btnMkdirRemote.setText(i18n.getString("btn.new_folder"));
    }

    private JLabel createLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    // COMMAND LOG LISTENER IMPLEMENTATION
    @Override
    public void onCommandSent(String command) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtConsoleLog.append(String.format("[%s] CLIENT >> %s\n", time, command));
            txtConsoleLog.setCaretPosition(txtConsoleLog.getDocument().getLength());
        });
    }

    @Override
    public void onResponseReceived(int code, String response) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtConsoleLog.append(String.format("[%s] SERVER << %s\n", time, response));
            txtConsoleLog.setCaretPosition(txtConsoleLog.getDocument().getLength());
        });
    }
}
