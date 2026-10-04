package com.ftpsystem.database;

import com.ftpsystem.common.TransferLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data Access Object quan ly Nhat ky truyen tai va hoat dong mang
 */
public class LogDAO {
    private static LogDAO instance;
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    // Cache log trong bo nho (de hien thi tren GUI Server)
    private final List<TransferLog> memoryLogs = Collections.synchronizedList(new ArrayList<>());

    private LogDAO() {}

    public static synchronized LogDAO getInstance() {
        if (instance == null) {
            instance = new LogDAO();
        }
        return instance;
    }

    public void log(TransferLog log) {
        memoryLogs.add(0, log); // Them vao dau danh sach
        if (memoryLogs.size() > 500) {
            memoryLogs.remove(memoryLogs.size() - 1);
        }

        if (dbManager.isMockMode()) return;

        String sql = "INSERT INTO transfer_logs (username, client_ip, action, filename, " +
                "file_size_bytes, duration_ms, speed_kbps, status, details) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getUsername());
            ps.setString(2, log.getClientIp());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getFilename());
            ps.setLong(5, log.getFileSizeBytes());
            ps.setLong(6, log.getDurationMs());
            ps.setDouble(7, log.getSpeedKbps());
            ps.setString(8, log.getStatus());
            ps.setString(9, log.getDetails());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[LogDAO] Khong the ghi log vao MySQL: " + e.getMessage());
        }
    }

    public List<TransferLog> getRecentLogs(int limit) {
        if (dbManager.isMockMode()) {
            return new ArrayList<>(memoryLogs.subList(0, Math.min(limit, memoryLogs.size())));
        }

        List<TransferLog> list = new ArrayList<>();
        String sql = "SELECT * FROM transfer_logs ORDER BY id DESC LIMIT ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferLog l = new TransferLog();
                    l.setId(rs.getLong("id"));
                    l.setUsername(rs.getString("username"));
                    l.setClientIp(rs.getString("client_ip"));
                    l.setAction(rs.getString("action"));
                    l.setFilename(rs.getString("filename"));
                    l.setFileSizeBytes(rs.getLong("file_size_bytes"));
                    l.setDurationMs(rs.getLong("duration_ms"));
                    l.setSpeedKbps(rs.getDouble("speed_kbps"));
                    l.setStatus(rs.getString("status"));
                    l.setDetails(rs.getString("details"));
                    l.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(l);
                }
            }
        } catch (SQLException e) {
            return new ArrayList<>(memoryLogs.subList(0, Math.min(limit, memoryLogs.size())));
        }
        return list;
    }
}
