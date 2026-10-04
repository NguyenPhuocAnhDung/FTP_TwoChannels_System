package com.ftpsystem.common;

import java.sql.Timestamp;

/**
 * Nhat ky truyen tai va hoat dong mang luu trong MySQL
 */
public class TransferLog {
    private long id;
    private String username;
    private String clientIp;
    private String action; // UPLOAD, DOWNLOAD, DELETE, MKDIR, RENAME, LOGIN, LOGOUT
    private String filename;
    private long fileSizeBytes;
    private long durationMs;
    private double speedKbps;
    private String status; // SUCCESS, FAILED
    private String details;
    private Timestamp createdAt;

    public TransferLog() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public TransferLog(String username, String clientIp, String action, String filename, 
                       long fileSizeBytes, long durationMs, double speedKbps, String status, String details) {
        this();
        this.username = username;
        this.clientIp = clientIp;
        this.action = action;
        this.filename = filename;
        this.fileSizeBytes = fileSizeBytes;
        this.durationMs = durationMs;
        this.speedKbps = speedKbps;
        this.status = status;
        this.details = details;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public double getSpeedKbps() { return speedKbps; }
    public void setSpeedKbps(double speedKbps) { this.speedKbps = speedKbps; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
