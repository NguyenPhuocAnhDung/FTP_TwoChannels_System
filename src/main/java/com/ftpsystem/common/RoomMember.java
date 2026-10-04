package com.ftpsystem.common;

import java.sql.Timestamp;

/**
 * Thanh vien trong Phong voi Vai tro va Trang thai phe duyet
 */
public class RoomMember {
    private int id;
    private int roomId;
    private String roomName;
    private String username;
    private String role;     // OWNER, EDITOR, VIEWER
    private String status;   // PENDING, APPROVED, REJECTED
    private boolean canUpload;
    private boolean canDelete;
    private Timestamp joinedAt;

    public RoomMember() {
        this.joinedAt = new Timestamp(System.currentTimeMillis());
        this.role = "VIEWER";
        this.status = "PENDING";
        this.canUpload = true;
        this.canDelete = false;
    }

    public RoomMember(int roomId, String roomName, String username, String role, String status, boolean canUpload, boolean canDelete) {
        this();
        this.roomId = roomId;
        this.roomName = roomName;
        this.username = username;
        this.role = role;
        this.status = status;
        this.canUpload = canUpload;
        this.canDelete = canDelete;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isCanUpload() { return canUpload; }
    public void setCanUpload(boolean canUpload) { this.canUpload = canUpload; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }

    public boolean isOwner() { return "OWNER".equalsIgnoreCase(role); }
    public boolean isApproved() { return "APPROVED".equalsIgnoreCase(status); }
}
