package com.ftpsystem;

import com.ftpsystem.client.FtpClientCore;
import com.ftpsystem.client.TransferProgressCallback;
import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.server.FtpServer;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
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
}
