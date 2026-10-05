package com.ftpsystem;

import com.ftpsystem.client.FtpClientCore;
import com.ftpsystem.client.TransferProgressCallback;
import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.server.FtpServer;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiem thu Tu dong Toan dien He thong FTP 2 Kenh (RFC 959)
 * Kiem tra:
 * 1. Khoi dong Server tren Port 2125
 * 2. Ket noi Kenh Dieu Khien (Port 2125)
 * 3. Dang nhap xac thuc User
 * 4. Lay danh sach file qua Kenh Du Lieu (PASV mode)
 * 5. Upload file va kiem tra toan ven Checksum SHA-256
 * 6. Download file va so sanh Checksum
 * 7. Ngat ket noi an toan
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FtpSystemIntegrationTest {

    private static final int TEST_PORT = 2125;
    private static FtpServer server;
    private static FtpClientCore client;

    private static File tempUploadFile;
    private static File tempDownloadFile;
    private static String expectedChecksum;

    @BeforeAll
    public static void setUpAll() throws Exception {
        // 1. Khoi tao Server
        server = FtpServer.getInstance();
        boolean started = server.start(TEST_PORT);
        assertTrue(started, "Server phai khoi dong thanh cong tren port " + TEST_PORT);

        // 2. Tao file mau de test truyen tai
        tempUploadFile = File.createTempFile("ftp_test_upload_", ".txt");
        try (FileWriter fw = new FileWriter(tempUploadFile)) {
            fw.write("DAY LA DU LIEU THU NGHIEM KIEM TRA TOAN VEN CUA HE THONG FTP 2 KENH!\n");
            for (int i = 1; i <= 100; i++) {
                fw.write("Dong so " + i + ": Ket noi Socket TCP hoat dong hoan hao.\n");
            }
        }
        expectedChecksum = ChecksumUtil.calculateSHA256(tempUploadFile);

        tempDownloadFile = File.createTempFile("ftp_test_download_", ".txt");

        // 3. Khoi tao Client
        client = new FtpClientCore();
    }

    @AfterAll
    public static void tearDownAll() {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        if (server != null && server.isRunning()) {
            server.stop();
        }
        if (tempUploadFile != null && tempUploadFile.exists()) tempUploadFile.delete();
        if (tempDownloadFile != null && tempDownloadFile.exists()) tempDownloadFile.delete();
    }

    @Test
    @Order(1)
    public void testConnectAndLogin() throws Exception {
        boolean connected = client.connect("127.0.0.1", TEST_PORT);
        assertTrue(connected, "Client phai ket noi duoc vao Kenh Dieu Khien 2125");

        boolean loggedIn = client.login("admin", "admin123");
        assertTrue(loggedIn, "Dang nhap tai khoan admin phai thanh cong");
        assertTrue(client.isLoggedIn());
    }

    @Test
    @Order(2)
    public void testPwdAndList() throws Exception {
        String pwd = client.pwd();
        assertNotNull(pwd);

        List<FileItem> items = client.listFiles();
        assertNotNull(items, "Danh sach file khong duoc null");
    }

    @Test
    @Order(3)
    public void testUploadAndChecksum() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        client.uploadFile(tempUploadFile, tempUploadFile.getName(), 0, new TransferProgressCallback() {
            @Override
            public void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent) {}

            @Override
            public void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                latch.countDown();
            }
        });

        boolean completedInTime = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completedInTime, "Upload khong duoc timeout");
        assertTrue(success.get(), "Upload phai thanh cong qua Kenh Du Lieu");
    }

    @Test
    @Order(4)
    public void testDownloadAndVerifyIntegrity() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        client.downloadFile(tempUploadFile.getName(), tempDownloadFile, 0, new TransferProgressCallback() {
            @Override
            public void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent) {}

            @Override
            public void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                latch.countDown();
            }
        });

        boolean completedInTime = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completedInTime, "Download khong duoc timeout");
        assertTrue(success.get(), "Download phai thanh cong");

        // XAC THUC TINH TOAN VEN (DATA INTEGRITY)
        String actualChecksum = ChecksumUtil.calculateSHA256(tempDownloadFile);
        assertEquals(expectedChecksum, actualChecksum, "Ma SHA-256 Checksum cua file tai ve phai khop 100% voi file goc!");
    }

    @Test
    @Order(5)
    public void testDeleteFile() throws Exception {
        boolean deleted = client.deleteFile(tempUploadFile.getName());
        assertTrue(deleted, "Xoa file tren server phai thanh cong");
    }

    @Test
    @Order(6)
    public void testUserRegistrationAndRoomWorkflow() throws Exception {
        long ts = System.currentTimeMillis();
        // 1. Dang ky tai khoan moi
        String newUser = "sv_test_" + ts;
        String newPass = "pass123";
        boolean reg = client.register(newUser, newPass);
        assertTrue(reg, "Dang ky user moi phai thanh cong");

        // 2. Tao client thu 2 de dang nhap voi user moi
        FtpClientCore client2 = new FtpClientCore();
        assertTrue(client2.connect("127.0.0.1", TEST_PORT));
        assertTrue(client2.login(newUser, newPass));

        // 3. User moi tu tao Phong lam Chu phong (Owner)
        String testRoom = "Nhom_Test_" + ts;
        String roomRes = client2.makeRoom(testRoom);
        assertTrue(roomRes.startsWith("200"), "Tao phong moi phai thanh cong va la Chu phong");

        // 4. Admin xin gia nhap phong
        String joinRes = client.joinRoom(testRoom);
        assertTrue(joinRes.startsWith("200"), "Gui yeu cau xin vao phong phai thanh cong");

        // 5. Chu phong phe duyet Admin vao phong voi quyen EDITOR
        String appRes = client2.approveMember(testRoom, "admin", "EDITOR");
        assertTrue(appRes.startsWith("200"), "Chu phong phe duyet phai thanh cong");

        client2.disconnect();
    }

    @Test
    @Order(7)
    public void testWebServerEndpoints() throws Exception {
        int webPort = server.getWebPort();
        assertTrue(webPort > 0, "Web Port phai lon hon 0");

        // 1. Kiem tra Web Portal UI (HTML static)
        URL indexUrl = new URI("http://127.0.0.1:" + webPort + "/").toURL();
        HttpURLConnection connIndex = (HttpURLConnection) indexUrl.openConnection();
        connIndex.setRequestMethod("GET");
        connIndex.setConnectTimeout(5000);
        connIndex.setReadTimeout(5000);
        assertEquals(200, connIndex.getResponseCode(), "Web Portal phai tra ve HTTP 200 OK");

        StringBuilder indexHtml = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connIndex.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) indexHtml.append(line);
        }
        assertTrue(indexHtml.toString().contains("FTP System"), "Trang web phai chua giao dien FTP System");

        // 2. Kiem tra REST API /api/stats
        URL statsUrl = new URI("http://127.0.0.1:" + webPort + "/api/stats").toURL();
        HttpURLConnection connStats = (HttpURLConnection) statsUrl.openConnection();
        connStats.setRequestMethod("GET");
        assertEquals(200, connStats.getResponseCode());
        StringBuilder statsJson = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connStats.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) statsJson.append(line);
        }
        assertTrue(statsJson.toString().contains("\"running\":true"), "API stats phai bao server dang running");

        // 3. Kiem tra REST API /api/logs
        URL logsUrl = new URI("http://127.0.0.1:" + webPort + "/api/logs").toURL();
        HttpURLConnection connLogs = (HttpURLConnection) logsUrl.openConnection();
        connLogs.setRequestMethod("GET");
        assertEquals(200, connLogs.getResponseCode());

        // 4. Kiem tra REST API /api/packets
        URL packetsUrl = new URI("http://127.0.0.1:" + webPort + "/api/packets").toURL();
        HttpURLConnection connPackets = (HttpURLConnection) packetsUrl.openConnection();
        connPackets.setRequestMethod("GET");
        assertEquals(200, connPackets.getResponseCode());

        // 5. Kiem tra REST API /api/files
        URL filesUrl = new URI("http://127.0.0.1:" + webPort + "/api/files").toURL();
        HttpURLConnection connFiles = (HttpURLConnection) filesUrl.openConnection();
        connFiles.setRequestMethod("GET");
        assertEquals(200, connFiles.getResponseCode());
    }

    @Test
    @Order(8)
    public void testRoomRolePermissionsComprehensive() throws Exception {
        long ts = System.currentTimeMillis();
        String owner = "room_own_" + ts;
        String editor = "room_edt_" + ts;
        String viewer = "room_viw_" + ts;
        String pass = "pass123";
        String room = "Room_Collab_" + ts;

        // Dang ky 3 user
        FtpClientCore regClient = new FtpClientCore();
        assertTrue(regClient.connect("127.0.0.1", TEST_PORT));
        assertTrue(regClient.register(owner, pass));
        assertTrue(regClient.register(editor, pass));
        assertTrue(regClient.register(viewer, pass));
        regClient.disconnect();

        // Khoi tao ket noi cho 3 client
        FtpClientCore cOwner = new FtpClientCore();
        assertTrue(cOwner.connect("127.0.0.1", TEST_PORT));
        assertTrue(cOwner.login(owner, pass));

        FtpClientCore cEditor = new FtpClientCore();
        assertTrue(cEditor.connect("127.0.0.1", TEST_PORT));
        assertTrue(cEditor.login(editor, pass));

        FtpClientCore cViewer = new FtpClientCore();
        assertTrue(cViewer.connect("127.0.0.1", TEST_PORT));
        assertTrue(cViewer.login(viewer, pass));

        try {
            // 1. Owner tao phong
            String mkRoom = cOwner.makeRoom(room);
            assertTrue(mkRoom.startsWith("200"), "Owner tao phong thanh cong");

            // 2. Editor & Viewer xin vao phong
            assertTrue(cEditor.joinRoom(room).startsWith("200"));
            assertTrue(cViewer.joinRoom(room).startsWith("200"));

            // 3. Owner phe duyet Editor voi quyen EDITOR va Viewer voi quyen VIEWER
            assertTrue(cOwner.approveMember(room, editor, "EDITOR").startsWith("200"));
            assertTrue(cOwner.approveMember(room, viewer, "VIEWER").startsWith("200"));

            // 4. File test
            File fOwner = File.createTempFile("own_file_", ".txt");
            try (FileWriter fw = new FileWriter(fOwner)) { fw.write("Hello from Owner"); }
            fOwner.deleteOnExit();

            File fEditor = File.createTempFile("edt_file_", ".txt");
            try (FileWriter fw = new FileWriter(fEditor)) { fw.write("Hello from Editor"); }
            fEditor.deleteOnExit();

            File fViewer = File.createTempFile("viw_file_", ".txt");
            try (FileWriter fw = new FileWriter(fViewer)) { fw.write("Hello from Viewer"); }
            fViewer.deleteOnExit();

            // 5. Kiem tra UPLOAD:
            // a) Owner upload vao phong -> THANH CONG
            CountDownLatch l1 = new CountDownLatch(1);
            AtomicBoolean s1 = new AtomicBoolean(false);
            cOwner.uploadFile(fOwner, "[PHONG]_" + room + "/owner_doc.txt", 0, new SimpleCallback(l1, s1));
            assertTrue(l1.await(5, TimeUnit.SECONDS) && s1.get(), "Owner phai upload duoc");

            // b) Editor upload vao phong -> THANH CONG
            CountDownLatch l2 = new CountDownLatch(1);
            AtomicBoolean s2 = new AtomicBoolean(false);
            cEditor.uploadFile(fEditor, "[PHONG]_" + room + "/editor_doc.txt", 0, new SimpleCallback(l2, s2));
            assertTrue(l2.await(5, TimeUnit.SECONDS) && s2.get(), "Editor phai upload duoc");

            // c) Viewer upload vao phong -> BI TU CHOI (Viewer khong co quyen ghi)
            CountDownLatch l3 = new CountDownLatch(1);
            AtomicBoolean s3 = new AtomicBoolean(false);
            cViewer.uploadFile(fViewer, "[PHONG]_" + room + "/viewer_doc.txt", 0, new SimpleCallback(l3, s3));
            l3.await(5, TimeUnit.SECONDS);
            assertFalse(s3.get(), "Viewer KHONG duoc phep upload vao phong");

            // 6. Kiem tra DOWNLOAD:
            // Viewer download file cua Owner trong phong -> THANH CONG
            File dlViewer = File.createTempFile("dl_viw_", ".txt");
            dlViewer.deleteOnExit();
            CountDownLatch l4 = new CountDownLatch(1);
            AtomicBoolean s4 = new AtomicBoolean(false);
            cViewer.downloadFile("[PHONG]_" + room + "/owner_doc.txt", dlViewer, 0, new SimpleCallback(l4, s4));
            assertTrue(l4.await(5, TimeUnit.SECONDS) && s4.get(), "Viewer phai download duoc file trong phong");
            assertEquals("Hello from Owner", new String(java.nio.file.Files.readAllBytes(dlViewer.toPath())));

            // 7. Kiem tra DELETE:
            // a) Viewer xoa file trong phong -> BI TU CHOI (false)
            boolean vDel = cViewer.deleteFile("[PHONG]_" + room + "/owner_doc.txt");
            assertFalse(vDel, "Viewer KHONG duoc phep xoa file trong phong");

            // b) Editor xoa file editor_doc.txt -> THANH CONG
            boolean eDel = cEditor.deleteFile("[PHONG]_" + room + "/editor_doc.txt");
            assertTrue(eDel, "Editor phai duoc phep xoa file trong phong");

            // c) Owner xoa file owner_doc.txt -> THANH CONG
            boolean oDel = cOwner.deleteFile("[PHONG]_" + room + "/owner_doc.txt");
            assertTrue(oDel, "Owner phai duoc phep xoa file trong phong");

            // 8. Kiem tra XOA trong thu muc ca nhan (Home Directory):
            // Upload 1 file vao home cua Viewer roi xoa -> phai duoc phep
            CountDownLatch l5 = new CountDownLatch(1);
            AtomicBoolean s5 = new AtomicBoolean(false);
            cViewer.uploadFile(fViewer, "my_personal.txt", 0, new SimpleCallback(l5, s5));
            assertTrue(l5.await(5, TimeUnit.SECONDS) && s5.get(), "User phai upload duoc trong thu muc ca nhan");
            boolean myDel = cViewer.deleteFile("my_personal.txt");
            assertTrue(myDel, "User phai duoc phep xoa file trong thu muc ca nhan cua chinh minh");

        } finally {
            cOwner.disconnect();
            cEditor.disconnect();
            cViewer.disconnect();
        }
    }

    private static class SimpleCallback implements TransferProgressCallback {
        private final CountDownLatch latch;
        private final AtomicBoolean success;

        public SimpleCallback(CountDownLatch latch, AtomicBoolean success) {
            this.latch = latch;
            this.success = success;
        }

        @Override public void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent) {}
        @Override public void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256) {
            success.set(true);
            latch.countDown();
        }
        @Override public void onError(String errorMessage) {
            success.set(false);
            latch.countDown();
        }
    }
}
