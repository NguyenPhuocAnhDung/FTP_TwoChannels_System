package com.ftpsystem.database;

import com.ftpsystem.common.Room;
import com.ftpsystem.common.RoomMember;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data Access Object quan ly Phong (Room) va Thanh vien (Member)
 * Ho tro ca MySQL Database va In-Memory Mock Fallback.
 */
public class RoomDAO {
    private static RoomDAO instance;
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    // In-memory mock storage
    private final Map<String, Room> mockRooms = new ConcurrentHashMap<>();
    private final List<RoomMember> mockMembers = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(10);

    private RoomDAO() {
        initMockData();
    }

    public static synchronized RoomDAO getInstance() {
        if (instance == null) {
            instance = new RoomDAO();
        }
        return instance;
    }

    private void initMockData() {
        // Mau 1 phong mac dinh do sinhvien1 lam Chu phong
        Room r = new Room("DoAn_Mang_Nhom1", "sinhvien1", "Phong Do An Mang May Tinh", "storage/rooms/DoAn_Mang_Nhom1");
        r.setId(1);
        mockRooms.put(r.getRoomName(), r);
        mockMembers.add(new RoomMember(1, "DoAn_Mang_Nhom1", "sinhvien1", "OWNER", "APPROVED", true, true));

        // Tao thu muc tren o dia
        new File(r.getStoragePath()).mkdirs();
    }

    public synchronized boolean createRoom(String roomName, String ownerUsername, String description) {
        // Ten phong tro thanh ten thu muc tren dia -> chi cho phep chu/so/_/- de chan Path Traversal
        if (roomName == null || !roomName.matches("[A-Za-z0-9_\\-]{3,50}")) return false;
        if (findByRoomName(roomName) != null) return false; // Khong cho ghi de phong cua nguoi khac
        String path = "storage/rooms/" + roomName;
        new File(path).mkdirs();

        Room room = new Room(roomName, ownerUsername, description, path);
        room.setId(idCounter.incrementAndGet());
        mockRooms.put(roomName, room);
        mockMembers.add(new RoomMember(room.getId(), roomName, ownerUsername, "OWNER", "APPROVED", true, true));

        if (dbManager.isMockMode()) return true;

        String sqlRoom = "INSERT INTO rooms (room_name, owner_username, description, storage_path) VALUES (?, ?, ?, ?)";
        String sqlMem = "INSERT INTO room_members (room_id, username, role, status, can_upload, can_delete) VALUES (?, ?, 'OWNER', 'APPROVED', 1, 1)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlRoom, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, roomName);
            ps.setString(2, ownerUsername);
            ps.setString(3, description);
            ps.setString(4, path);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int roomId = rs.getInt(1);
                    room.setId(roomId);
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlMem)) {
                        ps2.setInt(1, roomId);
                        ps2.setString(2, ownerUsername);
                        ps2.executeUpdate();
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[RoomDAO] Loi tao phong tren MySQL: " + e.getMessage());
            return true; // Fallback thanh cong tren cache
        }
    }

    public synchronized boolean requestJoinRoom(String roomName, String username) {
        Room r = findByRoomName(roomName);
        if (r == null) return false;

        // Kiem tra da tham gia chua
        for (RoomMember m : mockMembers) {
            if (m.getRoomName().equalsIgnoreCase(roomName) && m.getUsername().equalsIgnoreCase(username)) {
                return false; // Da gui hoac da la thanh vien
            }
        }

        mockMembers.add(new RoomMember(r.getId(), roomName, username, "VIEWER", "PENDING", true, false));

        if (dbManager.isMockMode()) return true;

        String sql = "INSERT INTO room_members (room_id, username, role, status, can_upload, can_delete) VALUES (?, ?, 'VIEWER', 'PENDING', 1, 0)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getId());
            ps.setString(2, username);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[RoomDAO] Loi xin vao phong tren MySQL: " + e.getMessage());
            return true;
        }
    }

    public synchronized boolean approveMember(String roomName, String username, String role, boolean canUpload, boolean canDelete) {
        Room r = findByRoomName(roomName);
        if (r == null) return false;

        for (RoomMember m : mockMembers) {
            if (m.getRoomName().equalsIgnoreCase(roomName) && m.getUsername().equalsIgnoreCase(username)) {
                m.setStatus("APPROVED");
                m.setRole(role);
                m.setCanUpload(canUpload);
                m.setCanDelete(canDelete);
                break;
            }
        }

        if (dbManager.isMockMode()) return true;

        String sql = "UPDATE room_members SET status = 'APPROVED', role = ?, can_upload = ?, can_delete = ? WHERE room_id = ? AND username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setBoolean(2, canUpload);
            ps.setBoolean(3, canDelete);
            ps.setInt(4, r.getId());
            ps.setString(5, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[RoomDAO] Loi duyet thanh vien: " + e.getMessage());
            return true;
        }
    }

    public synchronized boolean rejectMember(String roomName, String username) {
        Room r = findByRoomName(roomName);
        if (r == null) return false;

        mockMembers.removeIf(m -> m.getRoomName().equalsIgnoreCase(roomName) && m.getUsername().equalsIgnoreCase(username));

        if (dbManager.isMockMode()) return true;

        String sql = "DELETE FROM room_members WHERE room_id = ? AND username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getId());
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return true;
        }
    }

    public Room findByRoomName(String roomName) {
        if (dbManager.isMockMode()) {
            return mockRooms.get(roomName);
        }

        String sql = "SELECT * FROM rooms WHERE room_name = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Room r = new Room(rs.getString("room_name"), rs.getString("owner_username"),
                            rs.getString("description"), rs.getString("storage_path"));
                    r.setId(rs.getInt("id"));
                    r.setCreatedAt(rs.getTimestamp("created_at"));
                    return r;
                }
            }
        } catch (SQLException ignored) {}
        return mockRooms.get(roomName);
    }

    public List<Room> getRoomsOwnedBy(String ownerUsername) {
        List<Room> list = new ArrayList<>();
        for (Room r : mockRooms.values()) {
            if (r.getOwnerUsername().equalsIgnoreCase(ownerUsername)) {
                list.add(r);
            }
        }
        return list;
    }

    public List<RoomMember> getPendingRequestsForOwner(String ownerUsername) {
        List<RoomMember> result = new ArrayList<>();
        List<Room> owned = getRoomsOwnedBy(ownerUsername);
        for (Room r : owned) {
            for (RoomMember m : mockMembers) {
                if (m.getRoomName().equalsIgnoreCase(r.getRoomName()) && "PENDING".equalsIgnoreCase(m.getStatus())) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    public List<Room> getApprovedRoomsForUser(String username) {
        List<Room> list = new ArrayList<>();
        for (RoomMember m : mockMembers) {
            if (m.getUsername().equalsIgnoreCase(username) && "APPROVED".equalsIgnoreCase(m.getStatus())) {
                Room r = findByRoomName(m.getRoomName());
                if (r != null && !list.contains(r)) {
                    list.add(r);
                }
            }
        }
        return list;
    }

    public RoomMember getMemberRole(String roomName, String username) {
        for (RoomMember m : mockMembers) {
            if (m.getRoomName().equalsIgnoreCase(roomName) && m.getUsername().equalsIgnoreCase(username) && "APPROVED".equalsIgnoreCase(m.getStatus())) {
                return m;
            }
        }
        return null;
    }
}
