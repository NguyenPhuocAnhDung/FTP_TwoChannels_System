package com.ftpsystem.common;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Thong tin chi tiet ve mot File/Thu muc tren he thong
 */
public class FileItem {
    private final String name;
    private final long size;
    private final boolean isDirectory;
    private final long lastModified;
    private final String permissions;
    private final String absolutePath;

    private static final SimpleDateFormat LIST_DATE_FORMAT = 
            new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);

    public FileItem(String name, long size, boolean isDirectory, long lastModified, String permissions, String absolutePath) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
        this.permissions = permissions;
        this.absolutePath = absolutePath;
    }

    public static FileItem fromFile(File file) {
        String perm = (file.isDirectory() ? "d" : "-") +
                (file.canRead() ? "r" : "-") +
                (file.canWrite() ? "w" : "-") +
                (file.canExecute() ? "x" : "-") + "rw-r--";
        return new FileItem(
                file.getName(),
                file.isDirectory() ? 0 : file.length(),
                file.isDirectory(),
                file.lastModified(),
                perm,
                file.getAbsolutePath()
        );
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }
    public long getLastModified() { return lastModified; }
    public String getPermissions() { return permissions; }
    public String getAbsolutePath() { return absolutePath; }

    public String getFormattedSize() {
        if (isDirectory) return "<DIR>";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    public String getFormattedDate() {
        return LIST_DATE_FORMAT.format(new Date(lastModified));
    }

    /**
     * Dinh dang dong ket qua cho lenh LIST (chuan Unix ls -l)
     */
    public String toFtpListFormat() {
        return String.format("%s 1 ftp ftp %12d %s %s\r\n",
                permissions,
                size,
                getFormattedDate(),
                name);
    }
}
