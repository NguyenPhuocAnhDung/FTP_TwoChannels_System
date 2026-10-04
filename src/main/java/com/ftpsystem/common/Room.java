package com.ftpsystem.common;

import java.sql.Timestamp;

/**
 * Thuc the Phong / Khong gian lam viec nhom (Room / Group Workspace)
 * Nguoi tao phong se tro thanh Chu phong (Owner).
 */
public class Room {
    private int id;
    private String roomName;
    private String ownerUsername;
    private String description;
    private String storagePath;
    private Timestamp createdAt;

    public Room() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Room(String roomName, String ownerUsername, String description, String storagePath) {
        this();
        this.roomName = roomName;
        this.ownerUsername = ownerUsername;
        this.description = description;
        this.storagePath = storagePath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
