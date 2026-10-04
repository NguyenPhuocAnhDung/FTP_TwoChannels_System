package com.ftpsystem;

import com.ftpsystem.client.FtpClientCore;
import com.ftpsystem.client.TransferProgressCallback;
import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.server.DataChannelHandler;
import com.ftpsystem.server.FtpServer;
import com.ftpsystem.server.PathSandbox;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiem thu cac diem giang vien luu y:
 *  1. Sandbox chong Path Traversal (don vi + tich hop qua giao thuc FTP that)
 *  2. Dai cong Kenh Du Lieu du lon + SO_REUSEADDR (khong het cong khi mo/dong lien tuc)
 *  3. Resumable Transfer (REST) bang RandomAccessFile cho ca Upload va Download
 *  4. Gioi han bang thong (Bandwidth Throttling) moi phien
 *  5. Phan quyen thanh vien phong (Viewer khong duoc ghi, Editor duoc ghi)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityAndTransportTest {

    private static final int PORT = 2126;
    private static FtpServer server;
    private static final List<File> cleanup = new ArrayList<>();

    @BeforeAll
    static void start() {
        server = FtpServer.getInstance();
        assertTrue(server.start(PORT), "Server phai khoi dong tren cong " + PORT);
    }

    @AfterAll
    static void stop() {
        if (server != null && server.isRunning()) server.stop();
        for (File f : cleanup) {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) k.delete();
            }
            f.delete();
        }
    }

    // ------------------------------------------------------------------ helpers
    private static class Result implements TransferProgressCallback {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean ok;
        volatile String error;
        volatile long durationMs;
        volatile double avgKbps;

        public void onProgress(long t, long total, double s, int p) {}
        public void onComplete(long total, long dur, double avg, String sha) {
            ok = true; durationMs = dur; avgKbps = avg; latch.countDown();
        }
        public void onError(String msg) { ok = false; error = msg; latch.countDown(); }
        Result await() throws InterruptedException {
            assertTrue(latch.await(60, TimeUnit.SECONDS), "Giao dich truyen file bi treo");
            return this;
        }
    }

    private static FtpClientCore login(String user, String pass) throws Exception {
        FtpClientCore c = new FtpClientCore();
        assertTrue(c.connect("127.0.0.1", PORT));
        assertTrue(c.login(user, pass), "Dang nhap " + user);
        return c;
    }

    private static File tempFile(int bytes) throws IOException {
        File f = File.createTempFile("ftp_sec_", ".bin");
        byte[] data = new byte[bytes];
        new Random(42).nextBytes(data);
        Files.write(f.toPath(), data);
        f.deleteOnExit();
        return f;
    }

    // ------------------------------------------------------- 1. PathSandbox (unit)
    @Test
    @Order(1)
    void sandboxBlocksTraversalPatterns(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("users/a"));
        Path sibling = Files.createDirectories(tmp.resolve("users/a2")); // ten bat dau giong "a"
        Path pub = Files.createDirectories(tmp.resolve("public"));
        Files.writeString(tmp.resolve("secret.txt"), "top secret");
        Files.writeString(home.resolve("ok.txt"), "ok");

        List<Path> roots = List.of(home, pub);

        String[] mustBlock = {
                "../a2/x.txt",                  // thu muc anh em co tien to giong nhau
                "../../secret.txt",
                "..\\..\\secret.txt",           // dau gach nguoc kieu Windows
                "docs/../../../secret.txt",
                "/../../secret.txt",            // "/" la goc ao nhung van khong duoc thoat
                "C:\\Windows\\win.ini",         // duong dan tuyet doi o dia
                "D:/data/x",
                "\\\\server\\share\\x",         // UNC
                "//server/share/x",
                "ok.txt:hidden",                // NTFS Alternate Data Stream
                "a\u0000b",                     // ky tu NUL
                "file:///etc/passwd"
        };
        for (String p : mustBlock) {
            assertNull(PathSandbox.resolve(home, home, p, roots), "Phai chan: " + p.replace('\u0000', '0'));
        }

        // Cac duong dan hop le van phai dung duoc
        assertNotNull(PathSandbox.resolve(home, home, "ok.txt", roots));
        assertNotNull(PathSandbox.resolve(home, home, "/ok.txt", roots));
        assertNotNull(PathSandbox.resolve(home, home, "docs/../ok.txt", roots));
        assertNotNull(PathSandbox.resolve(home, home, "newfile.txt", roots), "File chua ton tai (STOR) van hop le");
        assertNotNull(PathSandbox.resolve(home, home, "../../public/readme.txt", roots), "Thu muc public duoc cap phep");
        // "/etc/passwd" chi la duong dan ao nam trong home, khong phai file he thong
        Path v = PathSandbox.resolve(home, home, "/etc/passwd", roots);
        assertNotNull(v);
        assertTrue(v.startsWith(home));
    }

    @Test
    @Order(2)
    void sandboxFollowsSymlinks(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "x");
        Path link = home.resolve("shortcut");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "Khong tao duoc symlink tren may nay (can quyen): " + e);
        }
        assertNull(PathSandbox.resolve(home, home, "shortcut/secret.txt", List.of(home)),
                "Symlink tro ra ngoai sandbox phai bi chan");
    }

    // ---------------------------------------- 2. Dai cong du lieu + SO_REUSEADDR
    @Test
    @Order(3)
    void passivePortPoolIsLargeAndReusable() throws Exception {
        int min = DataChannelHandler.getPasvPortMin();
        int max = DataChannelHandler.getPasvPortMax();
        assertTrue(max - min + 1 >= 500, "Dai cong PASV phai du rong (>= 500 cong), hien tai: " + (max - min + 1));

        InetAddress lo = InetAddress.getLoopbackAddress();

        // (a) Mo dong thoi nhieu hon 51 cong (kich thuoc dai cu) -> khong duoc loi
        List<DataChannelHandler> open = new ArrayList<>();
        Set<Integer> ports = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            DataChannelHandler h = new DataChannelHandler();
            String p = h.openPassive(lo); // "127,0,0,1,p1,p2"
            String[] s = p.split(",");
            int port = Integer.parseInt(s[4]) * 256 + Integer.parseInt(s[5]);
            assertTrue(port >= min && port <= max, "Cong nam ngoai dai cau hinh: " + port);
            assertTrue(ports.add(port), "Hai phien khong duoc cung cap mot cong: " + port);
            open.add(h);
        }
        open.forEach(DataChannelHandler::close);

        // (b) Mo/dong lien tuc 1500 lan (> kich thuoc dai) -> khong het cong
        for (int i = 0; i < 1500; i++) {
            DataChannelHandler h = new DataChannelHandler();
            assertDoesNotThrow(() -> h.openPassive(lo));
            h.close();
        }
    }

    // --------------------------------------------- 3. Path Traversal qua giao thuc
    @Test
    @Order(4)
    void serverRejectsTraversalOverRealProtocol() throws Exception {
        FtpClientCore c = login("user", "123456");
        try {
            assertTrue(c.makeDir("docs"));
            cleanup.add(new File("storage/users/user/docs"));
            assertTrue(c.cwd("docs"), "Thu muc hop le phai vao duoc");
            assertTrue(c.cdup());

            // Thoat khoi home sang user khac / goc storage / o dia he thong
            assertFalse(c.cwd("../sinhvien1"), "Khong duoc doc thu muc nguoi dung khac");
            assertFalse(c.cwd("../../admin"));
            assertFalse(c.cwd("../.."));
            assertFalse(c.cwd("..\\..\\.."));
            assertFalse(c.cwd("C:\\Windows"));
            assertFalse(c.cwd("/../../.."));
            // Phong chua duoc duyet khong vao duoc du biet ten
            assertFalse(c.cwd("[PHONG]_DoAn_Mang_Nhom1"), "Chua la thanh vien phong thi khong duoc vao");
            // Tao thu muc thoat sandbox
            assertFalse(c.makeDir("../hacked"));
            assertFalse(new File("storage/users/hacked").exists());

            // Tai file ngoai sandbox
            File dest = File.createTempFile("leak_", ".tmp");
            dest.deleteOnExit();
            Result r = new Result();
            c.downloadFile("../../../pom.xml", dest, 0, r);
            assertFalse(r.await().ok, "Khong duoc tai pom.xml nam ngoai sandbox");
            assertEquals(0, dest.length());

            // Upload ra ngoai sandbox
            Result up = new Result();
            c.uploadFile(tempFile(100), "../../escaped.bin", 0, up);
            assertFalse(up.await().ok);
            assertFalse(new File("storage/escaped.bin").exists());

            // Van o trong phien hop le sau tat ca cac lan bi chan
            assertEquals("/", c.pwd());
        } finally {
            c.disconnect();
        }
    }

    @Test
    @Order(5)
    void registerRejectsDangerousNames() throws Exception {
        FtpClientCore c = new FtpClientCore();
        assertTrue(c.connect("127.0.0.1", PORT));
        try {
            assertFalse(c.register("../evil", "pass123"));
            assertFalse(c.register("a/b", "pass123"));
            assertFalse(c.register("a\\b", "pass123"));
            assertFalse(c.register("ab", "pass123"), "Qua ngan");
            assertFalse(new File("storage/evil").exists());
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------ 4. Resumable Transfer (REST)
    @Test
    @Order(6)
    void resumeUploadAndDownloadWithRandomAccessFile() throws Exception {
        File full = tempFile(300_000);
        int half = 120_000;
        String expected = ChecksumUtil.calculateSHA256(full);
        String name = "resume_" + System.nanoTime() + ".bin";
        cleanup.add(new File("storage/" + name));

        FtpClientCore c = login("admin", "admin123");
        try {
            // Upload dang do: chi gui 'half' byte dau
            File partial = File.createTempFile("partial_", ".bin");
            partial.deleteOnExit();
            try (RandomAccessFile in = new RandomAccessFile(full, "r")) {
                byte[] b = new byte[half];
                in.readFully(b);
                Files.write(partial.toPath(), b);
            }
            Result r1 = new Result();
            c.uploadFile(partial, name, 0, r1);
            assertTrue(r1.await().ok, String.valueOf(r1.error));
            assertEquals(half, new File("storage/" + name).length());

            // Tiep tuc upload tu byte 'half' (REST half) -> phai ghep dung thanh file day du
            Result r2 = new Result();
            c.uploadFile(full, name, half, r2);
            assertTrue(r2.await().ok, String.valueOf(r2.error));
            assertEquals(expected, ChecksumUtil.calculateSHA256(new File("storage/" + name)),
                    "File sau khi resume upload phai trung SHA-256");

            // Download dang do: cat file local ve 'half' roi tiep tuc
            File dl = File.createTempFile("dl_", ".bin");
            dl.deleteOnExit();
            try (RandomAccessFile raf = new RandomAccessFile(dl, "rw")) {
                byte[] head = new byte[half];
                try (RandomAccessFile src = new RandomAccessFile(full, "r")) { src.readFully(head); }
                raf.write(head);
            }
            Result r3 = new Result();
            c.downloadFile(name, dl, half, r3);
            assertTrue(r3.await().ok, String.valueOf(r3.error));
            assertEquals(expected, ChecksumUtil.calculateSHA256(dl), "File sau khi resume download phai trung SHA-256");

            // REST vuot qua kich thuoc file -> Server tu choi (khong tao file "thung lo")
            Result r4 = new Result();
            c.uploadFile(full, name, 10_000_000L, r4);
            assertFalse(r4.await().ok, "REST vuot qua do dai file phai bi tu choi");
        } finally {
            c.disconnect();
        }
    }

    // -------------------------------------------------- 5. Bandwidth Throttling
    @Test
    @Order(7)
    void bandwidthIsLimitedPerSession() throws Exception {
        // Tai khoan 'user' bi gioi han 512 KB/s
        File f = tempFile(800 * 1024); // 800 KB -> toi thieu ~1.5s neu bi gioi han
        String name = "throttle_" + System.nanoTime() + ".bin";
        cleanup.add(new File("storage/users/user/" + name));

        FtpClientCore c = login("user", "123456");
        try {
            Result r = new Result();
            c.uploadFile(f, name, 0, r);
            assertTrue(r.await().ok, String.valueOf(r.error));
            assertTrue(r.durationMs >= 1200, "800KB o 512KB/s phai mat >= 1.2s, thuc te " + r.durationMs + " ms");
            assertTrue(r.avgKbps <= 512 * 1.35, "Toc do TB vuot gioi han: " + r.avgKbps + " KB/s");
        } finally {
            c.disconnect();
        }

        // Admin khong bi gioi han -> nhanh hon ro ret
        String name2 = "nolimit_" + System.nanoTime() + ".bin";
        cleanup.add(new File("storage/" + name2));
        FtpClientCore a = login("admin", "admin123");
        try {
            Result r = new Result();
            a.uploadFile(f, name2, 0, r);
            assertTrue(r.await().ok);
            assertTrue(r.durationMs < 1200, "Admin khong bi gioi han nen phai nhanh, thuc te " + r.durationMs + " ms");
        } finally {
            a.disconnect();
        }
    }

    // ----------------------------------------------- 6. Phan quyen thanh vien phong
    @Test
    @Order(8)
    void roomRolesControlWriteAccess() throws Exception {
        long ts = System.currentTimeMillis();
        String ownerName = "own_" + ts, viewerName = "view_" + ts, room = "Room_" + ts;
        cleanup.add(new File("storage/rooms/" + room));
        cleanup.add(new File("storage/users/" + ownerName));
        cleanup.add(new File("storage/users/" + viewerName));

        FtpClientCore reg = new FtpClientCore();
        assertTrue(reg.connect("127.0.0.1", PORT));
        assertTrue(reg.register(ownerName, "pass123"));
        assertTrue(reg.register(viewerName, "pass123"));
        reg.disconnect();

        FtpClientCore owner = login(ownerName, "pass123");
        FtpClientCore viewer = login(viewerName, "pass123");
        try {
            assertTrue(owner.makeRoom(room).startsWith("200"));
            assertFalse(viewer.cwd("[PHONG]_" + room), "Chua duoc duyet -> khong vao duoc phong");

            assertTrue(viewer.joinRoom(room).startsWith("200"));
            assertTrue(owner.approveMember(room, viewerName, "VIEWER").startsWith("200"));
            assertTrue(viewer.cwd("[PHONG]_" + room), "Da duoc duyet -> vao duoc phong");

            // Viewer chi xem: khong duoc upload / tao thu muc
            Result denied = new Result();
            viewer.uploadFile(tempFile(1000), "[PHONG]_" + room + "/v.bin", 0, denied);
            assertFalse(denied.await().ok, "Viewer khong duoc upload");
            assertFalse(viewer.makeDir("[PHONG]_" + room + "/folder"));

            // Chu phong luon duoc ghi
            Result ownerUp = new Result();
            owner.uploadFile(tempFile(1000), "[PHONG]_" + room + "/o.bin", 0, ownerUp);
            assertTrue(ownerUp.await().ok, String.valueOf(ownerUp.error));

            // Nang quyen len Editor -> duoc upload
            assertTrue(owner.approveMember(room, viewerName, "EDITOR").startsWith("200"));
            Result ok = new Result();
            viewer.uploadFile(tempFile(1000), "[PHONG]_" + room + "/e.bin", 0, ok);
            assertTrue(ok.await().ok, "Editor phai duoc upload: " + ok.error);
        } finally {
            owner.disconnect();
            viewer.disconnect();
        }
    }
}
