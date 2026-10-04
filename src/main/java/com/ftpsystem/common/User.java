package com.ftpsystem.common;

/**
 * Thuc the Nguoi dung trong co so du lieu MySQL
 * Ho tro Phan quyen (RBAC) va Gioi han bang thong / Quota.
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private String homeDirectory;
    private String role; // ADMIN, USER, GUEST
    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;
    private int maxSpeedKbps; // 0 = khong gioi han
    private long quotaBytes;
    private long usedBytes;
    private boolean isActive;

    public User() {
        this.canRead = true;
        this.canWrite = true;
        this.canDelete = true;
        this.isActive = true;
        this.role = "USER";
        this.quotaBytes = 1073741824L; // 1GB
        this.maxSpeedKbps = 0;
    }

    public User(String username, String passwordHash, String salt, String homeDirectory, String role) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.homeDirectory = homeDirectory;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getHomeDirectory() { return homeDirectory; }
    public void setHomeDirectory(String homeDirectory) { this.homeDirectory = homeDirectory; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean canRead) { this.canRead = canRead; }

    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean canWrite) { this.canWrite = canWrite; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public int getMaxSpeedKbps() { return maxSpeedKbps; }
    public void setMaxSpeedKbps(int maxSpeedKbps) { this.maxSpeedKbps = maxSpeedKbps; }

    public long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(long quotaBytes) { this.quotaBytes = quotaBytes; }

    public long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
