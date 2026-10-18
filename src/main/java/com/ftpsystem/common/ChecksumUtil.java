package com.ftpsystem.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Tien ich tinh toan ma bam Checksum (SHA-256 / MD5)
 * Dam bao tinh toan ven du lieu (Data Integrity) o Tang Ung Dung.
 */
public class ChecksumUtil {

    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return "";
        }
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * Kiem tra tinh toan ven cua tap tin bang cach so sanh ma SHA-256 tinh duoc voi ma mong doi.
     * Su dung de xac thuc file sau khi truyen tai (STOR/RETR hoac sau Resumable Transfer REST).
     * 
     * @param file Tap tin can xac thuc
     * @param expectedChecksum Ma SHA-256 goc tu phia doi tac
     * @return true neu ma bam trung khop 100%, nguoc lai false
     */
    public static boolean verifySHA256(File file, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.trim().isEmpty()) {
            return false;
        }
        String actualChecksum = calculateSHA256(file);
        return expectedChecksum.trim().equalsIgnoreCase(actualChecksum);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = password + salt;
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Loi ma hoa mat khau: " + e.getMessage(), e);
        }
    }
}
