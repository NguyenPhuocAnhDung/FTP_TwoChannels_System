package com.ftpsystem.server.gui;

import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.User;
import com.ftpsystem.database.UserDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.UUID;

/**
 * Hop thoai Quan ly Tai khoan Nguoi dung trong MySQL
 */
public class UserManagementDialog extends JDialog {
    private final JTable userTable;
    private final DefaultTableModel tableModel;

    public UserManagementDialog(Frame owner) {
        super(owner, "Quản Lý Tài Khoản Người Dùng (MySQL Database)", true);
        setSize(850, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // Bang danh sach User
        String[] columns = {"ID", "Tài khoản", "Vai trò", "Thư mục gốc", "Đọc", "Ghi", "Xóa", "Tốc độ (KB/s)", "Hạn ngạch (MB)"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        userTable = new JTable(tableModel);
        userTable.setRowHeight(25);
        add(new JScrollPane(userTable), BorderLayout.CENTER);

        // Thanh cong cu
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton btnAdd = new JButton("Thêm Người Dùng");
        JButton btnDelete = new JButton("Xóa Người Dùng");
        JButton btnRefresh = new JButton("Làm Mới");
        JButton btnClose = new JButton("Đóng");

        toolbar.add(btnAdd);
        toolbar.add(btnDelete);
        toolbar.add(btnRefresh);
        toolbar.add(btnClose);
        add(toolbar, BorderLayout.SOUTH);

        // Events
        btnAdd.addActionListener(e -> showAddUserDialog());
        btnDelete.addActionListener(e -> deleteSelectedUser());
        btnRefresh.addActionListener(e -> loadUsers());
        btnClose.addActionListener(e -> dispose());

        loadUsers();
    }

    private void loadUsers() {
        tableModel.setRowCount(0);
        List<User> list = UserDAO.getInstance().getAllUsers();
        for (User u : list) {
            tableModel.addRow(new Object[]{
                    u.getId(),
                    u.getUsername(),
                    u.getRole(),
                    u.getHomeDirectory(),
                    u.isCanRead() ? "Có" : "Không",
                    u.isCanWrite() ? "Có" : "Không",
                    u.isCanDelete() ? "Có" : "Không",
                    u.getMaxSpeedKbps() == 0 ? "Vô hạn" : u.getMaxSpeedKbps(),
                    u.getQuotaBytes() / (1024 * 1024)
            });
        }
    }

    private void showAddUserDialog() {
        JTextField txtUser = new JTextField(15);
        JPasswordField txtPass = new JPasswordField(15);
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"USER", "ADMIN", "GUEST"});
        JTextField txtHome = new JTextField("storage/public", 15);
        JCheckBox chkRead = new JCheckBox("Đọc (RETR, LIST)", true);
        JCheckBox chkWrite = new JCheckBox("Ghi (STOR, MKD)", true);
        JCheckBox chkDelete = new JCheckBox("Xóa (DELE, RMD)", false);
        JTextField txtSpeed = new JTextField("0", 6); // 0 = unlimited
        JTextField txtQuota = new JTextField("1024", 6); // 1GB

        JPanel panel = new JPanel(new GridLayout(9, 2, 8, 8));
        panel.add(new JLabel("Tài khoản:")); panel.add(txtUser);
        panel.add(new JLabel("Mật khẩu:")); panel.add(txtPass);
        panel.add(new JLabel("Vai trò:")); panel.add(cbRole);
        panel.add(new JLabel("Thư mục lưu trữ:")); panel.add(txtHome);
        panel.add(new JLabel("Quyền đọc:")); panel.add(chkRead);
        panel.add(new JLabel("Quyền ghi:")); panel.add(chkWrite);
        panel.add(new JLabel("Quyền xóa:")); panel.add(chkDelete);
        panel.add(new JLabel("Giới hạn tốc độ (KB/s, 0=vô hạn):")); panel.add(txtSpeed);
        panel.add(new JLabel("Hạn ngạch Quota (MB):")); panel.add(txtQuota);

        int res = JOptionPane.showConfirmDialog(this, panel, "Thêm Tài Khoản Mới", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String uName = txtUser.getText().trim();
            String pwd = new String(txtPass.getPassword());
            if (uName.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tài khoản và mật khẩu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String salt = UUID.randomUUID().toString().substring(0, 8);
            String hash = ChecksumUtil.hashPassword(pwd, salt);

            User user = new User(uName, hash, salt, txtHome.getText().trim(), (String) cbRole.getSelectedItem());
            user.setCanRead(chkRead.isSelected());
            user.setCanWrite(chkWrite.isSelected());
            user.setCanDelete(chkDelete.isSelected());
            try {
                user.setMaxSpeedKbps(Integer.parseInt(txtSpeed.getText().trim()));
                user.setQuotaBytes(Long.parseLong(txtQuota.getText().trim()) * 1024 * 1024);
            } catch (Exception ignored) {}

            boolean saved = UserDAO.getInstance().save(user);
            if (saved) {
                JOptionPane.showMessageDialog(this, "Thêm tài khoản thành công vào MySQL!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                loadUsers();
            } else {
                JOptionPane.showMessageDialog(this, "Không thể lưu tài khoản vào cơ sở dữ liệu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedUser() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng để xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String username = (String) tableModel.getValueAt(row, 1);
        if ("admin".equalsIgnoreCase(username)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản Quản trị viên 'admin' mặc định!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn vô hiệu hóa tài khoản: " + username + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            UserDAO.getInstance().delete(username);
            loadUsers();
        }
    }
}
