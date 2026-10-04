package com.ftpsystem.server;

import com.ftpsystem.common.FtpConstants;

import java.io.*;
import java.net.*;
import java.util.Random;

/**
 * Xu ly Kenh Du Lieu (Data Channel) doc lap cho FTP Server.
 * Ho tro:
 * 1. Che do Bi dong (PASV - Passive Mode): Server mo ServerSocket, Client ket noi vao.
 * 2. Che do Chu dong (PORT - Active Mode): Client mo ServerSocket, Server ket noi sang.
 * 3. Tiep tuc truyen do dang (REST - Resumable Transfer) qua RandomAccessFile.
 * 4. Kiem soat toc do truyen tai (Bandwidth Throttling).
 */
public class DataChannelHandler {
    private ServerSocket pasvServerSocket;
    private Socket dataSocket;
    private FtpConstants.DataMode mode = FtpConstants.DataMode.PASSIVE;
    private String activeHost;
    private int activePort;

    public interface TransferListener {
        void onProgress(long transferredBytes, long totalBytes, double speedKbps);
    }

    // ---- Dai cong Kenh Du Lieu (PASV) doc tu config.properties, mac dinh 30000-31000 (1001 cong) ----
    private static final int PASV_MIN;
    private static final int PASV_MAX;
    private static final Random RND = new Random();

    static {
        int min = FtpConstants.PASV_PORT_MIN;
        int max = FtpConstants.PASV_PORT_MAX;
        try (InputStream in = DataChannelHandler.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                min = Integer.parseInt(p.getProperty("server.pasv_port_min", String.valueOf(min)).trim());
                max = Integer.parseInt(p.getProperty("server.pasv_port_max", String.valueOf(max)).trim());
            }
        } catch (Exception ignored) {}
        if (min < 1024 || max > 65535 || min > max) {
            min = FtpConstants.PASV_PORT_MIN;
            max = FtpConstants.PASV_PORT_MAX;
        }
        PASV_MIN = min;
        PASV_MAX = max;
    }

    public static int getPasvPortMin() { return PASV_MIN; }
    public static int getPasvPortMax() { return PASV_MAX; }

    /** Callback de Server ghi nhan su kien TCP (bat tay / dong ket noi) cua Kenh Du Lieu. */
    private java.util.function.Consumer<com.ftpsystem.common.NetworkPacketInfo> packetListener;
    private String peerLabel = "CLIENT";

    public void setPacketListener(java.util.function.Consumer<com.ftpsystem.common.NetworkPacketInfo> l, String peerLabel) {
        this.packetListener = l;
        this.peerLabel = peerLabel;
    }

    private void emit(String flags, String note, int localPort) {
        if (packetListener == null) return;
        packetListener.accept(new com.ftpsystem.common.NetworkPacketInfo(
                "TRANSPORT", "DATA_CHANNEL", peerLabel, "SERVER:" + localPort,
                "TCP", flags, 0, "[Suy dien tu Socket] " + note));
    }

    /** Mo mot ServerSocket tren cong trong dai PASV, bat SO_REUSEADDR truoc khi bind. */
    private ServerSocket bindInRange(InetAddress serverAddress) throws IOException {
        int span = PASV_MAX - PASV_MIN + 1;
        int start = RND.nextInt(span);
        for (int i = 0; i < span; i++) {
            int candidate = PASV_MIN + (start + i) % span;
            ServerSocket ss = new ServerSocket();
            try {
                ss.setReuseAddress(true); // Cho phep dung lai cong dang TIME_WAIT
                ss.bind(new InetSocketAddress(serverAddress, candidate), 1);
                return ss;
            } catch (IOException busy) {
                try { ss.close(); } catch (IOException ignored) {}
            }
        }
        throw new IOException("Het cong trong dai PASV " + PASV_MIN + "-" + PASV_MAX);
    }

    /**
     * Mo ServerSocket cho che do PASV
     * @return Chuoi tham so (h1,h2,h3,h4,p1,p2) cho lenh 227
     */
    public synchronized String openPassive(InetAddress serverAddress) throws IOException {
        close();
        mode = FtpConstants.DataMode.PASSIVE;

        pasvServerSocket = bindInRange(serverAddress);
        int port = pasvServerSocket.getLocalPort();

        byte[] addr = serverAddress.getAddress();
        int p1 = port / 256;
        int p2 = port % 256;

        return String.format("%d,%d,%d,%d,%d,%d",
                addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF, p1, p2);
    }

    /**
     * Thiet lap che do Active (PORT)
     */
    public synchronized void setActiveMode(String host, int port) {
        close();
        this.mode = FtpConstants.DataMode.ACTIVE;
        this.activeHost = host;
        this.activePort = port;
    }

    /**
     * Lay socket du lieu da ket noi (chap nhan ket noi PASV hoac tao ket noi PORT)
     */
    public synchronized Socket obtainDataSocket() throws IOException {
        if (mode == FtpConstants.DataMode.PASSIVE) {
            if (pasvServerSocket == null || pasvServerSocket.isClosed()) {
                throw new IOException("Kenh du lieu PASV chua duoc khoi tao!");
            }
            pasvServerSocket.setSoTimeout(30000); // 30 giay timeout doi client ket noi
            dataSocket = pasvServerSocket.accept();
            dataSocket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);
            emitHandshake(dataSocket.getLocalPort());
            return dataSocket;
        } else {
            // Active Mode: Server chu dong ket noi toi Client IP:Port
            dataSocket = new Socket();
            dataSocket.connect(new InetSocketAddress(activeHost, activePort), 15000);
            dataSocket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);
            emitHandshake(dataSocket.getLocalPort());
            return dataSocket;
        }
    }

    /**
     * java.net.Socket che giau co TCP voi ung dung nen khong the "bat" SYN/ACK/FIN that.
     * Ta SUY DIEN cac co nay tu vong doi Socket (accept/connect thanh cong = bat tay 3 buoc xong).
     */
    private void emitHandshake(int localPort) {
        emit("SYN", "Client -> Server: yeu cau mo Kenh Du Lieu", localPort);
        emit("SYN,ACK", "Server -> Client: chap nhan ket noi", localPort);
        emit("ACK", "Bat tay 3 buoc hoan tat - Kenh Du Lieu ESTABLISHED", localPort);
    }

    /**
     * Gui chuoi du lieu (thuong dung cho ket qua lenh LIST)
     */
    public void sendDataString(String text) throws IOException {
        try (Socket socket = obtainDataSocket();
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8")) {
            writer.write(text);
            writer.flush();
        } finally {
            close();
        }
    }

    /**
     * Gui tep tin ve Client (Lenh RETR - Download)
     * Ho tro resume tu offset bang RandomAccessFile.
     */
    public TransferResult sendFile(File file, long offset, int maxSpeedKbps, TransferListener listener) throws IOException {
        long startTime = System.currentTimeMillis();
        long totalSize = file.length();
        long transferred = offset;

        try (Socket socket = obtainDataSocket();
             RandomAccessFile raf = new RandomAccessFile(file, "r");
             OutputStream out = new BufferedOutputStream(socket.getOutputStream(), FtpConstants.BUFFER_SIZE)) {

            if (offset > 0) {
                raf.seek(offset);
            }

            byte[] buffer = new byte[FtpConstants.BUFFER_SIZE];
            int bytesRead;
            long lastSpeedCalcTime = startTime;
            long bytesSinceLastCalc = 0;

            while ((bytesRead = raf.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                transferred += bytesRead;
                bytesSinceLastCalc += bytesRead;

                // Throttling (Gioi han bang thong neu co cau hinh)
                if (maxSpeedKbps > 0) {
                    throttle(bytesRead, maxSpeedKbps);
                }

                // Cap nhat tien do moi 200ms
                long now = System.currentTimeMillis();
                if (now - lastSpeedCalcTime >= 200 && listener != null) {
                    double speed = (bytesSinceLastCalc / 1024.0) / ((now - lastSpeedCalcTime) / 1000.0);
                    listener.onProgress(transferred, totalSize, speed);
                    lastSpeedCalcTime = now;
                    bytesSinceLastCalc = 0;
                }
            }
            out.flush();
        } finally {
            close();
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        double avgSpeed = ((transferred - offset) / 1024.0) / (duration / 1000.0);
        return new TransferResult(transferred - offset, duration, avgSpeed);
    }

    /**
     * Nhan tep tin tu Client tai len (Lenh STOR - Upload)
     * Ho tro resume bang cach ghi tiep vao cuoi file neu offset > 0.
     */
    public TransferResult receiveFile(File file, long offset, int maxSpeedKbps, TransferListener listener) throws IOException {
        long startTime = System.currentTimeMillis();
        long transferred = 0;

        try (Socket socket = obtainDataSocket();
             RandomAccessFile raf = new RandomAccessFile(file, "rw");
             InputStream in = new BufferedInputStream(socket.getInputStream(), FtpConstants.BUFFER_SIZE)) {

            if (offset > 0) {
                raf.seek(offset);
            } else {
                raf.setLength(0); // Ghi moi hoan toan neu khong resume
            }

            byte[] buffer = new byte[FtpConstants.BUFFER_SIZE];
            int bytesRead;
            long lastSpeedCalcTime = startTime;
            long bytesSinceLastCalc = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                raf.write(buffer, 0, bytesRead);
                transferred += bytesRead;
                bytesSinceLastCalc += bytesRead;

                if (maxSpeedKbps > 0) {
                    throttle(bytesRead, maxSpeedKbps);
                }

                long now = System.currentTimeMillis();
                if (now - lastSpeedCalcTime >= 200 && listener != null) {
                    double speed = (bytesSinceLastCalc / 1024.0) / ((now - lastSpeedCalcTime) / 1000.0);
                    listener.onProgress(transferred, transferred, speed);
                    lastSpeedCalcTime = now;
                    bytesSinceLastCalc = 0;
                }
            }
        } finally {
            close();
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        double avgSpeed = (transferred / 1024.0) / (duration / 1000.0);
        return new TransferResult(transferred, duration, avgSpeed);
    }

    // Trang thai gioi han bang thong cho MOI PHIEN truyen (reset khi Kenh Du Lieu dong)
    private long throttleStartNanos = 0;
    private long throttleBytes = 0;

    /**
     * Gioi han bang thong theo tong luong da gui: sau moi khoi du lieu, tinh thoi diem
     * ly tuong ma tong so byte duoc phep co mat roi ngu bu phan chenh lech.
     * Dam bao toc do trung binh cua phien KHONG vuot maxKbps du buffer lon hay nho.
     */
    private void throttle(int bytesJustSent, int maxKbps) {
        if (maxKbps <= 0) return;
        long now = System.nanoTime();
        if (throttleStartNanos == 0) throttleStartNanos = now;
        throttleBytes += bytesJustSent;
        long expectedNanos = (throttleBytes * 1_000_000_000L) / (maxKbps * 1024L);
        long sleepNanos = expectedNanos - (now - throttleStartNanos);
        if (sleepNanos > 0) {
            try {
                Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void close() {
        if (pasvServerSocket != null && !pasvServerSocket.isClosed()) {
            try { pasvServerSocket.close(); } catch (Exception ignored) {}
            pasvServerSocket = null;
        }
        if (dataSocket != null) {
            int port = dataSocket.getLocalPort();
            if (!dataSocket.isClosed()) {
                try { dataSocket.close(); } catch (Exception ignored) {}
            }
            dataSocket = null;
            emit("FIN,ACK", "Kenh Du Lieu dong sau khi truyen xong (4-way close)", port);
        }
        throttleStartNanos = 0;
        throttleBytes = 0;
    }

    public static class TransferResult {
        public final long bytesTransferred;
        public final long durationMs;
        public final double avgSpeedKbps;

        public TransferResult(long bytesTransferred, long durationMs, double avgSpeedKbps) {
            this.bytesTransferred = bytesTransferred;
            this.durationMs = durationMs;
            this.avgSpeedKbps = avgSpeedKbps;
        }
    }
}
