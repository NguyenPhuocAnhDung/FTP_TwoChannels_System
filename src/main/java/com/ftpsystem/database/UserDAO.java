package com.ftpsystem.database;

import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.User;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Access Object quan ly Nguoi dung
 * Ho tro ca MySQL Database va Mock Cache du phong.
 */
public class UserDAO {
    private static UserDAO instance;
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    // Cache du phong khi khong co MySQL
    private final Map<String, User> mockUsers = new ConcurrentHashMap<>();

    private UserDAO() {
        initMockData();
    }

    public static synchronized UserDAO getInstance() {
        if (instance == null) {
            instance = new UserDAO();
        }
        return instance;
    }

    private void initMockData() {
        // admin / admin123
        User admin = new User("admin", 
                ChecksumUtil.hashPassword("admin123", "ftpsalt123"), 
                "ftpsalt123", "storage/admin", "ADMIN");
        admin.setCanRead(true);
        admin.setCanWrite(true);
        admin.setCanDelete(true);
        mockUsers.put("admin", admin);

        // user / 123456
        User normalUser = new User("user", 
                ChecksumUtil.hashPassword("123456", "ftpsalt456"), 
                "ftpsalt456", "storage/public", "USER");
        normalUser.setCanRead(true);
        normalUser.setCanWrite(true);
        normalUser.setCanDelete(false);
        normalUser.setMaxSpeedKbps(512); // Gioi han 512KB/s
        mockUsers.put("user", normalUser);

        // anonymous / guest
        User guest = new User("anonymous", "", "", "storage/public", "GUEST");
        guest.setCanRead(true);
        guest.setCanWrite(false);
        guest.setCanDelete(false);
        mockUsers.put("anonymous", guest);
    }

    public synchronized boolean registerUser(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,32}")) {
            return false; // Ten tai khoan tro thanh ten thu muc -> chan ky tu nguy hiem (../, /, \, :)
        }
        if (password == null || password.length() < 4) {
            return false;
        }
        if (findByUsername(username) != null) {
            return false; // Da ton tai
        }

        String salt = java.util.UUID.randomUUID().toString().substring(0, 8);
        String hash = ChecksumUtil.hashPassword(password, salt);
        String home = "storage/users/" + username;
        new File(home).mkdirs();

        User user = new User(username, hash, salt, home, "USER");
        user.setCanRead(true);
        user.setCanWrite(true);
        user.setCanDelete(false);
        user.setMaxSpeedKbps(512); // Gioi han 512KB/s

        return save(user);
    }

    public User findByUsername(String username) {
        if (dbManager.isMockMode()) {
            return mockUsers.get(username);
        }

        String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Loi tim user MySQL: " + e.getMessage() + ". Dung mock cache.");
            return mockUsers.get(username);
        }
        return null;
    }

    public boolean authenticate(String username, String password) {
        User user = findByUsername(username);
        if (user == null) return false;

        // Anonymous login
        if ("anonymous".equalsIgnoreCase(username)) return true;

        String hashed = ChecksumUtil.hashPassword(password, user.getSalt());
        return hashed.equalsIgnoreCase(user.getPasswordHash());
    }

    public boolean save(User user) {
        mockUsers.put(user.getUsername(), user);
        if (dbManager.isMockMode()) {
            return true;
        }

        String sql = "INSERT INTO users (username, password_hash, salt, home_directory, role, " +
                "can_read, can_write, can_delete, max_speed_kbps, quota_bytes, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "password_hash = VALUES(password_hash), salt = VALUES(salt), " +
                "home_directory = VALUES(home_directory), role = VALUES(role), " +
                "can_read = VALUES(can_read), can_write = VALUES(can_write), " +
                "can_delete = VALUES(can_delete), max_speed_kbps = VALUES(max_speed_kbps), " +
                "quota_bytes = VALUES(quota_bytes), is_active = VALUES(is_active)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getHomeDirectory());
            ps.setString(5, user.getRole());
            ps.setBoolean(6, user.isCanRead());
            ps.setBoolean(7, user.isCanWrite());
            ps.setBoolean(8, user.isCanDelete());
            ps.setInt(9, user.getMaxSpeedKbps());
            ps.setLong(10, user.getQuotaBytes());
            ps.setBoolean(11, user.isActive());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Loi luu user vao MySQL: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(String username) {
        mockUsers.remove(username);
        if (dbManager.isMockMode()) return true;

        String sql = "UPDATE users SET is_active = 0 WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Loi xoa user trong MySQL: " + e.getMessage());
            return false;
        }
    }

    public List<User> getAllUsers() {
        if (dbManager.isMockMode()) {
            return new ArrayList<>(mockUsers.values());
        }

        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE is_active = 1 ORDER BY id ASC";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            return new ArrayList<>(mockUsers.values());
        }
        return list;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setHomeDirectory(rs.getString("home_directory"));
        u.setRole(rs.getString("role"));
        u.setCanRead(rs.getBoolean("can_read"));
        u.setCanWrite(rs.getBoolean("can_write"));
        u.setCanDelete(rs.getBoolean("can_delete"));
        u.setMaxSpeedKbps(rs.getInt("max_speed_kbps"));
        u.setQuotaBytes(rs.getLong("quota_bytes"));
        u.setUsedBytes(rs.getLong("used_bytes"));
        u.setActive(rs.getBoolean("is_active"));
        return u;
    }
}
