package com.ftpsystem.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Co che Sandbox chong tan cong Path Traversal.
 *
 * Nguyen tac:
 * 1. Tu choi som moi duong dan Client gui len co dau hieu doc hai:
 *    - ky tu NUL, ky tu dieu khien;
 *    - duong dan tuyet doi cua he dieu hanh (C:\..., D:/..., \\server\share, file:...);
 *    - cac ky tu ':' (o dia, NTFS Alternate Data Stream).
 * 2. Chuan hoa (normalize) duong dan roi QUY CHIEU ve duong dan THAT (real path,
 *    giai quyet ca symbolic link / junction) truoc khi so sanh.
 * 3. So sanh theo TUNG THANH PHAN duong dan (Path.startsWith), khong so sanh chuoi
 *    nen thu muc "sv" khong the "chui" vao thu muc anh em "sv2".
 * 4. Chi chap nhan khi ket qua nam TRONG mot trong cac thu muc goc duoc cap phep.
 *
 * Luu y: dau "/" dau chuoi theo chuan FTP la goc ao cua user (chroot) chu khong phai
 * goc o dia he thong, nen "/etc/passwd" chi co the tro toi "<home>/etc/passwd".
 */
public final class PathSandbox {

    private PathSandbox() {}

    /** Kiem tra chuoi duong dan tho do Client gui len co hop le khong. */
    public static boolean isSafeInput(String raw) {
        if (raw == null) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7F) return false;   // NUL & ky tu dieu khien
            if (c == ':') return false;                // o dia Windows / ADS / URL scheme
            if (c == '<' || c == '>' || c == '|' || c == '"' || c == '*' || c == '?') return false;
        }
        String s = raw.replace('\\', '/');
        if (s.startsWith("//")) return false;          // UNC \\server\share
        return true;
    }

    /**
     * Giai quyet duong dan Client gui len thanh Path that tren dia.
     *
     * @param currentDir   thu muc hien tai cua phien
     * @param virtualRoot  goc ao dung cho duong dan bat dau bang "/" (thu muc home cua user)
     * @param raw          duong dan tho do Client gui
     * @param allowedRoots cac thu muc goc duoc phep truy cap
     * @return Path da chuan hoa nam trong sandbox, hoac null neu bi chan
     */
    public static Path resolve(Path currentDir, Path virtualRoot, String raw, List<Path> allowedRoots) {
        if (!isSafeInput(raw)) return null;
        String s = raw.replace('\\', '/').trim();
        Path candidate;
        if (s.startsWith("/")) {
            candidate = virtualRoot.resolve(s.substring(1));
        } else {
            candidate = currentDir.resolve(s);
        }
        candidate = candidate.toAbsolutePath().normalize();
        return isInside(candidate, allowedRoots) ? candidate : null;
    }

    /** Kiem tra candidate co nam trong it nhat mot thu muc goc cho phep (theo real path). */
    public static boolean isInside(Path candidate, List<Path> allowedRoots) {
        Path real = realOf(candidate);
        for (Path root : allowedRoots) {
            if (real.startsWith(realOf(root))) return true;
        }
        return false;
    }

    /**
     * Real path cua mot Path co the CHUA TON TAI (vi du file sap duoc STOR):
     * lay real path cua tien to ton tai gan nhat roi noi phan con lai vao.
     */
    public static Path realOf(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        Path existing = abs;
        Path tail = Paths.get("");
        while (existing != null && !Files.exists(existing)) {
            Path name = existing.getFileName();
            if (name != null) tail = name.resolve(tail);
            existing = existing.getParent();
        }
        if (existing == null) return abs;
        try {
            return existing.toRealPath().resolve(tail).normalize();
        } catch (IOException e) {
            return abs;
        }
    }
}
