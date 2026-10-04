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
 * 1. Nguoi dung tu Tao Phong moi -> Tro thanh Chu phong (Owner)
 * 2. Nguoi khac Gui yeu cau Xin Gia Nhap
 * 3. Chu phong xem danh sach va Phe Duyet (cap quyen Editor hoac Viewer)
 */
public class RoomManagementDialog extends JDialog {
    private final FtpClientCore ftpClient;
    private final String currentUsername;
    private final Runnable onDataChanged;

    private JTable pendingTable;
    private DefaultTableModel pendingModel;
    private JComboBox<String> cbMyRooms;

    public RoomManagementDialog(Frame owner, FtpClientCore ftpClient, String currentUsername, Runnable onDataChanged) {
        super(owner, "Quản Lý Không Gian Làm Việc Nhóm & Phê Duyệt (Chủ Phòng)", true);
        this.ftpClient = ftpClient;
        this.currentUsername = currentUsername;
        this.onDataChanged = onDataChanged;

        setSize(750, 480);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("👑 Phê Duyệt Thành Viên (Dành Cho Chủ Phòng)", createOwnerPanel());
        tabs.addTab("➕ Tạo Phòng Mới Hoặc Xin Gia Nhập", createMemberActionsPanel());

        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> dispose());
        bottom.add(btnClose);
        add(bottom, BorderLayout.SOUTH);

        reloadMyRooms();
    }

    private JPanel createOwnerPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: Chon phong minh lam chu
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.add(new JLabel("Phòng bạn làm Chủ phòng:"));
        cbMyRooms = new JComboBox<>();
        cbMyRooms.setPreferredSize(new Dimension(200, 26));
        JButton btnRefresh = new JButton("Làm mới");
        top.add(cbMyRooms);
        top.add(btnRefresh);
        panel.add(top, BorderLayout.NORTH);

        // Center: Bang yeu cau cho duyet
        String[] cols = {"Tên Phòng", "Tài Khoản Xin Vào", "Thời Gian Xin", "Trạng Thái"};
        pendingModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        pendingTable = new JTable(pendingModel);
        pendingTable.setRowHeight(24);
        panel.add(new JScrollPane(pendingTable), BorderLayout.CENTER);

        // Bottom: Nut phe duyet
        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnApproveEditor = new JButton("Duyệt Quyền Editor (Được Upload)");
        JButton btnApproveViewer = new JButton("Duyệt Quyền Viewer (Chỉ Xem)");
        JButton btnReject = new JButton("Từ Chối");

        btnApproveEditor.setBackground(new Color(40, 167, 69)); btnApproveEditor.setForeground(Color.WHITE);
        btnApproveViewer.setBackground(new Color(52, 152, 219)); btnApproveViewer.setForeground(Color.WHITE);
        btnReject.setBackground(new Color(220, 53, 69)); btnReject.setForeground(Color.WHITE);

        act.add(btnApproveEditor);
        act.add(btnApproveViewer);
        act.add(btnReject);
        panel.add(act, BorderLayout.SOUTH);

        // Events
        btnRefresh.addActionListener(e -> reloadPendingRequests());
        cbMyRooms.addActionListener(e -> reloadPendingRequests());

        btnApproveEditor.addActionListener(e -> doApprove("EDITOR"));
        btnApproveViewer.addActionListener(e -> doApprove("VIEWER"));
        btnReject.addActionListener(e -> doReject());

        return panel;
    }

    private JPanel createMemberActionsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Box 1: Tao phong moi
        JPanel pCreate = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pCreate.setBorder(BorderFactory.createTitledBorder("1. Tạo Phòng Mới (Bạn Sẽ Là Chủ Phòng)"));
        JTextField txtNewRoom = new JTextField(15);
        JButton btnCreate = new JButton("Tạo Phòng Mới");
        btnCreate.setBackground(new Color(40, 167, 69));
        btnCreate.setForeground(Color.WHITE);
        pCreate.add(new JLabel("Tên Phòng:"));
        pCreate.add(txtNewRoom);
        pCreate.add(btnCreate);

        btnCreate.addActionListener(e -> {
            String name = txtNewRoom.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng!");
                return;
            }
            new Thread(() -> {
                try {
                    String res = ftpClient.makeRoom(name);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, res);
                        txtNewRoom.setText("");
                        reloadMyRooms();
                        if (onDataChanged != null) onDataChanged.run();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
                }
            }).start();
        });

        // Box 2: Xin gia nhap phong
        JPanel pJoin = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pJoin.setBorder(BorderFactory.createTitledBorder("2. Xin Gia Nhập Vào Phòng Của Người Khác"));
        JTextField txtJoinRoom = new JTextField(15);
        JButton btnJoin = new JButton("Gửi Yêu Cầu Xin Vào");
        btnJoin.setBackground(new Color(52, 152, 219));
        btnJoin.setForeground(Color.WHITE);
        pJoin.add(new JLabel("Tên Phòng Muốn Vào:"));
        pJoin.add(txtJoinRoom);
        pJoin.add(btnJoin);

        btnJoin.addActionListener(e -> {
            String target = txtJoinRoom.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng muốn xin vào!");
                return;
            }
            new Thread(() -> {
                try {
                    String res = ftpClient.joinRoom(target);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, res);
                        txtJoinRoom.setText("");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
                }
            }).start();
        });

        panel.add(pCreate);
        panel.add(pJoin);
        return panel;
    }

    private void reloadMyRooms() {
        cbMyRooms.removeAllItems();
        List<Room> rooms = RoomDAO.getInstance().getRoomsOwnedBy(currentUsername);
        for (Room r : rooms) {
            cbMyRooms.addItem(r.getRoomName());
        }
        reloadPendingRequests();
    }

    private void reloadPendingRequests() {
        pendingModel.setRowCount(0);
        String selectedRoom = (String) cbMyRooms.getSelectedItem();
        List<RoomMember> list = RoomDAO.getInstance().getPendingRequestsForOwner(currentUsername);
        for (RoomMember m : list) {
            if (selectedRoom == null || m.getRoomName().equalsIgnoreCase(selectedRoom)) {
                pendingModel.addRow(new Object[]{
                        m.getRoomName(),
                        m.getUsername(),
                        m.getJoinedAt() != null ? m.getJoinedAt().toString() : "Mới đây",
                        "Đang chờ phê duyệt"
                });
            }
        }
    }

    private void doApprove(String role) {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thành viên trong bảng để duyệt!");
            return;
        }

        String rName = (String) pendingModel.getValueAt(row, 0);
        String uName = (String) pendingModel.getValueAt(row, 1);

        new Thread(() -> {
            try {
                String res = ftpClient.approveMember(rName, uName, role);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res);
                    reloadPendingRequests();
                    if (onDataChanged != null) onDataChanged.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
            }
        }).start();
    }

    private void doReject() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thành viên trong bảng để từ chối!");
            return;
        }

        String rName = (String) pendingModel.getValueAt(row, 0);
        String uName = (String) pendingModel.getValueAt(row, 1);

        new Thread(() -> {
            try {
                String res = ftpClient.rejectMember(rName, uName);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, res);
                    reloadPendingRequests();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage()));
            }
        }).start();
    }
}
