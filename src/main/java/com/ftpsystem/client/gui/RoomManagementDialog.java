package com.ftpsystem.client.gui;

import com.ftpsystem.client.FtpClientCore;
import com.ftpsystem.common.Room;
import com.ftpsystem.common.RoomMember;
import com.ftpsystem.database.RoomDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Hop thoai Quan ly Khong Gian Nhom (Room Workspaces & Approvals)
 * Cho phep:
 * 1. Xem danh sach tat ca thanh vien trong phong minh lam chu & dieu chinh phan quyen (Owner/Editor/Viewer, Upload, Delete)
 * 2. Xem va Phe duyet cac yeu cau xin gia nhap moi (Pending)
 * 3. Nguoi dung tu Tao phong moi hoac Gui yeu cau Xin vao phong khac
 */
public class RoomManagementDialog extends JDialog {
    private final FtpClientCore ftpClient;
    private final String currentUsername;
    private final Runnable onDataChanged;

    private JComboBox<String> cbMyRooms;
    
    // Tab 1: Danh sach thanh vien da duyet & phan quyen
    private JTable membersTable;
    private DefaultTableModel membersModel;

    // Tab 2: Danh sach yeu cau cho duyet
    private JTable pendingTable;
    private DefaultTableModel pendingModel;

    public RoomManagementDialog(Frame owner, FtpClientCore ftpClient, String currentUsername, Runnable onDataChanged) {
        super(owner, "Quản Lý Không Gian Làm Việc Nhóm & Phân Quyền Thành Viên", true);
        this.ftpClient = ftpClient;
        this.currentUsername = currentUsername;
        this.onDataChanged = onDataChanged;

        setSize(850, 530);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // Thanh chon phong lam chu tren cung
        JPanel topRoomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        topRoomBar.setBackground(new Color(245, 247, 250));
        topRoomBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 215, 225)));
        
        JLabel lblRoom = new JLabel("🏢 Chọn Phòng Bạn Làm Chủ Phòng (Owner):");
        lblRoom.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topRoomBar.add(lblRoom);

        cbMyRooms = new JComboBox<>();
        cbMyRooms.setPreferredSize(new Dimension(220, 28));
        cbMyRooms.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        topRoomBar.add(cbMyRooms);

        JButton btnRefreshAll = new JButton("🔄 Làm Mới Dữ Liệu");
        btnRefreshAll.setBackground(new Color(52, 152, 219));
        btnRefreshAll.setForeground(Color.WHITE);
        btnRefreshAll.setFocusPainted(false);
        topRoomBar.add(btnRefreshAll);

        add(topRoomBar, BorderLayout.NORTH);

        // Tabbed Pane trung tam
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabs.addTab("👥 Thành Viên Trong Phòng & Phân Quyền", createMembersListPanel());
        tabs.addTab("👑 Yêu Cầu Chờ Phê Duyệt", createPendingApprovalPanel());
        tabs.addTab("➕ Tạo Phòng Mới Hoặc Xin Gia Nhập", createMemberActionsPanel());

        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 8));
        JButton btnClose = new JButton("Đóng");
        btnClose.setPreferredSize(new Dimension(90, 30));
        btnClose.addActionListener(e -> dispose());
        bottom.add(btnClose);
        add(bottom, BorderLayout.SOUTH);

        // Su kien
        btnRefreshAll.addActionListener(e -> reloadAllData());
        cbMyRooms.addActionListener(e -> {
            reloadMembersList();
            reloadPendingRequests();
        });

        reloadMyRooms();
    }

    /**
     * Tab 1: Hien thi tat ca thanh vien hien co trong phong va cho phep doi quyen / kick
     */
    private JPanel createMembersListPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] cols = {"Tên Phòng", "Tài Khoản", "Vai Trò (Role)", "Quyền Upload", "Quyền Xóa", "Trạng Thái", "Ngày Tham Gia"};
        membersModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        membersTable = new JTable(membersModel);
        membersTable.setRowHeight(26);
        membersTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        membersTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(new JScrollPane(membersTable), BorderLayout.CENTER);

        // Thanh thao tac phan quyen
        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton btnSetEditor = new JButton("✏️ Nâng Lên Editor (Upload + Xóa)");
        JButton btnSetViewer = new JButton("👁️ Đổi Thành Viewer (Chỉ Xem)");
        JButton btnKick = new JButton("❌ Xóa Khỏi Phòng");

        btnSetEditor.setBackground(new Color(40, 167, 69)); btnSetEditor.setForeground(Color.WHITE);
        btnSetViewer.setBackground(new Color(52, 152, 219)); btnSetViewer.setForeground(Color.WHITE);
        btnKick.setBackground(new Color(220, 53, 69)); btnKick.setForeground(Color.WHITE);

        btnSetEditor.setFocusPainted(false);
        btnSetViewer.setFocusPainted(false);
        btnKick.setFocusPainted(false);

        act.add(btnSetEditor);
        act.add(btnSetViewer);
        act.add(btnKick);
        panel.add(act, BorderLayout.SOUTH);

        btnSetEditor.addActionListener(e -> changeSelectedMemberRole("EDITOR"));
        btnSetViewer.addActionListener(e -> changeSelectedMemberRole("VIEWER"));
        btnKick.addActionListener(e -> kickSelectedMember());

        return panel;
    }

    /**
     * Tab 2: Hien thi cac yeu cau dang cho duyet (Pending)
     */
    private JPanel createPendingApprovalPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] cols = {"Tên Phòng", "Tài Khoản Xin Vào", "Thời Gian Xin", "Trạng Thái"};
        pendingModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        pendingTable = new JTable(pendingModel);
        pendingTable.setRowHeight(26);
        pendingTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pendingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(new JScrollPane(pendingTable), BorderLayout.CENTER);

        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        JButton btnApproveEditor = new JButton("✅ Duyệt Quyền Editor (Được Upload & Xóa)");
        JButton btnApproveViewer = new JButton("👁️ Duyệt Quyền Viewer (Chỉ Xem/Tải)");
        JButton btnReject = new JButton("❌ Từ Chối");

        btnApproveEditor.setBackground(new Color(40, 167, 69)); btnApproveEditor.setForeground(Color.WHITE);
        btnApproveViewer.setBackground(new Color(52, 152, 219)); btnApproveViewer.setForeground(Color.WHITE);
        btnReject.setBackground(new Color(220, 53, 69)); btnReject.setForeground(Color.WHITE);

        act.add(btnApproveEditor);
        act.add(btnApproveViewer);
        act.add(btnReject);
        panel.add(act, BorderLayout.SOUTH);

        btnApproveEditor.addActionListener(e -> doApprovePending("EDITOR"));
        btnApproveViewer.addActionListener(e -> doApprovePending("VIEWER"));
        btnReject.addActionListener(e -> doRejectPending());

        return panel;
    }

    /**
     * Tab 3: Tao phong moi hoac Xin vao phong khac
     */
    private JPanel createMemberActionsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Box 1: Tao phong moi
        JPanel pCreate = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        pCreate.setBorder(BorderFactory.createTitledBorder("1. Tạo Không Gian Làm Việc Mới (Bạn Sẽ Là Chủ Phòng - Owner)"));
        JTextField txtNewRoom = new JTextField(18);
        JButton btnCreate = new JButton("Tạo Phòng Mới");
        btnCreate.setBackground(new Color(40, 167, 69));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pCreate.add(new JLabel("Tên Phòng:"));
        pCreate.add(txtNewRoom);
        pCreate.add(btnCreate);

        btnCreate.addActionListener(e -> {
            String name = txtNewRoom.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!name.matches("[A-Za-z0-9_\\-]{3,50}")) {
                JOptionPane.showMessageDialog(this, "Tên phòng không hợp lệ!\nTên phòng chỉ được chứa chữ cái, chữ số, dấu gạch dưới (_) hoặc gạch nối (-), từ 3 đến 50 ký tự.", "Tên Phòng Không Hợp Lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!ftpClient.isConnected()) {
                JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ! Vui lòng đóng hộp thoại và bấm 'Kết nối' lại trên màn hình chính.", "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new Thread(() -> {
                try {
                    String res = ftpClient.makeRoom(name);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, res, "Kết quả tạo phòng", JOptionPane.INFORMATION_MESSAGE);
                        txtNewRoom.setText("");
                        reloadMyRooms();
                        if (onDataChanged != null) onDataChanged.run();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        String msg = ex.getMessage();
                        if (msg != null && (msg.contains("aborted") || msg.contains("Connection reset") || msg.contains("Socket closed") || msg.contains("Mất kết nối"))) {
                            JOptionPane.showMessageDialog(this, 
                                "Mất kết nối với Server (kết nối Socket TCP đã bị ngắt)!\nChi tiết: " + msg + "\n\n👉 Cách khắc phục: Đóng hộp thoại này, bấm nút 'Kết nối' lại trên màn hình chính.",
                                "Mất Kết Nối", JOptionPane.ERROR_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this, "Lỗi tạo phòng: " + msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }).start();
        });

        // Box 2: Xin gia nhap phong
        JPanel pJoin = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        pJoin.setBorder(BorderFactory.createTitledBorder("2. Xin Gia Nhập Vào Phòng Của Người Khác"));
        JTextField txtJoinRoom = new JTextField(18);
        JButton btnJoin = new JButton("Gửi Yêu Cầu Xin Vào");
        btnJoin.setBackground(new Color(52, 152, 219));
        btnJoin.setForeground(Color.WHITE);
        btnJoin.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pJoin.add(new JLabel("Tên Phòng Muốn Vào:"));
        pJoin.add(txtJoinRoom);
        pJoin.add(btnJoin);

        btnJoin.addActionListener(e -> {
            String target = txtJoinRoom.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng muốn xin vào!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!ftpClient.isConnected()) {
                JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ! Vui lòng đóng hộp thoại và bấm 'Kết nối' lại trên màn hình chính.", "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new Thread(() -> {
                try {
                    String res = ftpClient.joinRoom(target);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, res, "Kết quả xin vào phòng", JOptionPane.INFORMATION_MESSAGE);
                        txtJoinRoom.setText("");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        String msg = ex.getMessage();
                        if (msg != null && (msg.contains("aborted") || msg.contains("Connection reset") || msg.contains("Socket closed") || msg.contains("Mất kết nối"))) {
                            JOptionPane.showMessageDialog(this, 
                                "Mất kết nối với Server!\nChi tiết: " + msg + "\n\n👉 Cách khắc phục: Đóng hộp thoại này và bấm nút 'Kết nối' lại trên màn hình chính.",
                                "Mất Kết Nối", JOptionPane.ERROR_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this, "Lỗi: " + msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }).start();
        });

        panel.add(pCreate);
        panel.add(pJoin);
        return panel;
    }

    private void reloadAllData() {
        reloadMyRooms();
    }

    private void reloadMyRooms() {
        String prevSelected = (String) cbMyRooms.getSelectedItem();
        cbMyRooms.removeAllItems();
        List<Room> rooms = RoomDAO.getInstance().getRoomsOwnedBy(currentUsername);
        for (Room r : rooms) {
            cbMyRooms.addItem(r.getRoomName());
        }
        if (prevSelected != null) {
            cbMyRooms.setSelectedItem(prevSelected);
        }
        reloadMembersList();
        reloadPendingRequests();
    }

    private void reloadMembersList() {
        if (membersModel == null) return;
        membersModel.setRowCount(0);
        String selectedRoom = (String) cbMyRooms.getSelectedItem();
        if (selectedRoom == null || selectedRoom.isEmpty()) return;

        List<RoomMember> list = RoomDAO.getInstance().getMembersOfRoom(selectedRoom);
        for (RoomMember m : list) {
            if ("APPROVED".equalsIgnoreCase(m.getStatus())) {
                String roleDisplay;
                if ("OWNER".equalsIgnoreCase(m.getRole())) {
                    roleDisplay = "👑 CHỦ PHÒNG (Owner)";
                } else if ("EDITOR".equalsIgnoreCase(m.getRole())) {
                    roleDisplay = "✏️ EDITOR (Biên tập)";
                } else {
                    roleDisplay = "👁️ VIEWER (Chỉ xem)";
                }

                membersModel.addRow(new Object[]{
                        m.getRoomName(),
                        m.getUsername(),
                        roleDisplay,
                        m.isCanUpload() ? "✅ Được Upload" : "❌ Chặn Upload",
                        m.isCanDelete() ? "✅ Được Xóa" : "❌ Chặn Xóa",
                        "Đã tham gia (Active)",
                        m.getJoinedAt() != null ? m.getJoinedAt().toString() : "Mới đây"
                });
            }
        }
    }

    private void reloadPendingRequests() {
        if (pendingModel == null) return;
        pendingModel.setRowCount(0);
        String selectedRoom = (String) cbMyRooms.getSelectedItem();
        List<RoomMember> list = RoomDAO.getInstance().getPendingRequestsForOwner(currentUsername);
        for (RoomMember m : list) {
            if (selectedRoom == null || m.getRoomName().equalsIgnoreCase(selectedRoom)) {
                pendingModel.addRow(new Object[]{
                        m.getRoomName(),
                        m.getUsername(),
                        m.getJoinedAt() != null ? m.getJoinedAt().toString() : "Mới đây",
                        "⏳ Đang chờ phê duyệt"
                });
            }
        }
    }

    private void changeSelectedMemberRole(String newRole) {
        int row = membersTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thành viên trong danh sách!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String rName = (String) membersModel.getValueAt(row, 0);
        String uName = (String) membersModel.getValueAt(row, 1);
        String currentRoleDisplay = (String) membersModel.getValueAt(row, 2);

        if (currentRoleDisplay != null && currentRoleDisplay.contains("CHỦ PHÒNG")) {
            JOptionPane.showMessageDialog(this, "Không thể thay đổi quyền của chính Chủ phòng!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ! Vui lòng kết nối lại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                String res = ftpClient.approveMember(rName, uName, newRole);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res, "Cập nhật thành công", JOptionPane.INFORMATION_MESSAGE);
                    reloadMembersList();
                    if (onDataChanged != null) onDataChanged.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private void kickSelectedMember() {
        int row = membersTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thành viên muốn mời ra khỏi phòng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String rName = (String) membersModel.getValueAt(row, 0);
        String uName = (String) membersModel.getValueAt(row, 1);
        String currentRoleDisplay = (String) membersModel.getValueAt(row, 2);

        if (currentRoleDisplay != null && currentRoleDisplay.contains("CHỦ PHÒNG")) {
            JOptionPane.showMessageDialog(this, "Không thể xóa chính Chủ phòng khỏi phòng!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int conf = JOptionPane.showConfirmDialog(this, 
                "Bạn có chắc chắn muốn xóa thành viên '" + uName + "' khỏi phòng '" + rName + "'?", 
                "Xác nhận xóa thành viên", JOptionPane.YES_NO_OPTION);
        if (conf != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                String res = ftpClient.rejectMember(rName, uName);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res, "Đã xóa thành viên", JOptionPane.INFORMATION_MESSAGE);
                    reloadMembersList();
                    if (onDataChanged != null) onDataChanged.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private void doApprovePending(String role) {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một yêu cầu trong bảng để duyệt!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ! Vui lòng đóng hộp thoại và bấm 'Kết nối' lại trên màn hình chính.", "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String rName = (String) pendingModel.getValueAt(row, 0);
        String uName = (String) pendingModel.getValueAt(row, 1);

        new Thread(() -> {
            try {
                String res = ftpClient.approveMember(rName, uName, role);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res, "Kết quả phê duyệt", JOptionPane.INFORMATION_MESSAGE);
                    reloadPendingRequests();
                    reloadMembersList();
                    if (onDataChanged != null) onDataChanged.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    String msg = ex.getMessage();
                    if (msg != null && (msg.contains("aborted") || msg.contains("Connection reset") || msg.contains("Socket closed") || msg.contains("Mất kết nối"))) {
                        JOptionPane.showMessageDialog(this, 
                            "Mất kết nối với Server!\nChi tiết: " + msg + "\n\n👉 Cách khắc phục: Đóng hộp thoại này và bấm nút 'Kết nối' lại trên màn hình chính.",
                            "Mất Kết Nối", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Lỗi phê duyệt: " + msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }).start();
    }

    private void doRejectPending() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một yêu cầu trong bảng để từ chối!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ! Vui lòng đóng hộp thoại và bấm 'Kết nối' lại trên màn hình chính.", "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String rName = (String) pendingModel.getValueAt(row, 0);
        String uName = (String) pendingModel.getValueAt(row, 1);

        new Thread(() -> {
            try {
                String res = ftpClient.rejectMember(rName, uName);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res, "Kết quả từ chối", JOptionPane.INFORMATION_MESSAGE);
                    reloadPendingRequests();
                    reloadMembersList();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    String msg = ex.getMessage();
                    if (msg != null && (msg.contains("aborted") || msg.contains("Connection reset") || msg.contains("Socket closed") || msg.contains("Mất kết nối"))) {
                        JOptionPane.showMessageDialog(this, 
                            "Mất kết nối với Server!\nChi tiết: " + msg + "\n\n👉 Cách khắc phục: Đóng hộp thoại này và bấm nút 'Kết nối' lại trên màn hình chính.",
                            "Mất Kết Nối", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Lỗi từ chối: " + msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }).start();
    }
}
