package com.ftpsystem.client;

import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.FtpConstants;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lõi FTP Client 2 Kênh (RFC 959)
 * Độc lập tách bạch:
 * 1. Kênh Điều Khiển (Control Channel - Port 2121)
 * 2. Kênh Dữ Liệu (Data Channel - Hỗ trợ cả PASV và PORT)
 * 3. Hỗ trợ tiếp tục tải dở dang (REST)
 * 4. Kiểm tra toàn vẹn Checksum SHA-256 sau khi tải
 */
public class FtpClientCore {
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    private boolean connected = false;
    private boolean loggedIn = false;
    private String currentServerDirectory = "/";

    private FtpConstants.DataMode dataMode = FtpConstants.DataMode.PASSIVE;
    private final List<CommandLogListener> logListeners = new ArrayList<>();

    public interface CommandLogListener {
        void onCommandSent(String command);
        void onResponseReceived(int code, String response);
    }

    public void addLogListener(CommandLogListener l) {
        logListeners.add(l);
    }

    public synchronized boolean connect(String host, int port) throws IOException {
        disconnect();

        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(host, port), 10000);
        controlSocket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);

        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
        writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"), true);

        // Doc cau chao ban dau tu Server
        String initialResponse = readResponse();
        if (initialResponse.startsWith("220")) {
            this.connected = true;
            return true;
        }
        return false;
    }

    public synchronized boolean login(String username, String password) throws IOException {
        if (!connected) return false;

        sendCommand(FtpConstants.CMD_USER + " " + username);
        String res = readResponse();
        if (res.startsWith("331")) {
            sendCommand(FtpConstants.CMD_PASS + " " + password);
            res = readResponse();
            if (res.startsWith("230")) {
                this.loggedIn = true;
                pwd();
                return true;
            }
        }
        return false;
    }

    public synchronized String pwd() throws IOException {
        sendCommand(FtpConstants.CMD_PWD);
        String res = readResponse();
        Pattern p = Pattern.compile("\"([^\"]+)\"");
        Matcher m = p.matcher(res);
        if (m.find()) {
            this.currentServerDirectory = m.group(1);
        }
        return currentServerDirectory;
    }

    public synchronized boolean cwd(String path) throws IOException {
        sendCommand(FtpConstants.CMD_CWD + " " + path);
        String res = readResponse();
        if (res.startsWith("250")) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized boolean cdup() throws IOException {
        sendCommand(FtpConstants.CMD_CDUP);
        String res = readResponse();
        if (res.startsWith("250")) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized List<FileItem> listFiles() throws IOException {
        List<FileItem> list = new ArrayList<>();
        Socket dataSocket = null;
        ServerSocket activeServerSocket = null;

        try {
            if (dataMode == FtpConstants.DataMode.PASSIVE) {
                dataSocket = openPassiveDataSocket();
                sendCommand(FtpConstants.CMD_LIST);
            } else {
                activeServerSocket = new ServerSocket(0, 1, controlSocket.getLocalAddress());
                sendPortCommand(activeServerSocket.getLocalPort());
                sendCommand(FtpConstants.CMD_LIST);
            }

            String prelim = readResponse();
            if (!prelim.startsWith("150")) {
                throw new IOException("Server khong mo kenh du lieu: " + prelim);
            }

            if (dataMode == FtpConstants.DataMode.ACTIVE) {
                activeServerSocket.setSoTimeout(15000);
                dataSocket = activeServerSocket.accept();
            }

            // Doc du lieu tu Data Channel
            try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = dataReader.readLine()) != null) {
                    FileItem item = parseListLine(line);
                    if (item != null) {
                        list.add(item);
                    }
                }
            }

            String finalRes = readResponse(); // 226 Transfer complete
        } finally {
            if (dataSocket != null) try { dataSocket.close(); } catch (Exception ignored) {}
            if (activeServerSocket != null) try { activeServerSocket.close(); } catch (Exception ignored) {}
        }

        return list;
    }

    public void downloadFile(String remoteFilename, File localTargetFile, long offset, TransferProgressCallback callback) {
        new Thread(() -> {
            Socket dataSocket = null;
            ServerSocket activeServerSocket = null;
            long startTime = System.currentTimeMillis();
            long transferred = offset;

            try {
                // Thiet lap Binary Type
                sendCommand(FtpConstants.CMD_TYPE + " I");
                readResponse();

                // Tiep tuc tai neu co offset
                if (offset > 0) {
                    sendCommand(FtpConstants.CMD_REST + " " + offset);
                    String restRes = readResponse();
                    if (!restRes.startsWith("350")) {
                        throw new IOException("Server tu choi tiep tuc tai: " + restRes);
                    }
                }

                // Lay dung luong file de ve Progress bar
                long totalSize = getFileSize(remoteFilename);
                if (totalSize <= 0) totalSize = 1;

                if (dataMode == FtpConstants.DataMode.PASSIVE) {
                    dataSocket = openPassiveDataSocket();
                    sendCommand(FtpConstants.CMD_RETR + " " + remoteFilename);
                } else {
                    activeServerSocket = new ServerSocket(0, 1, controlSocket.getLocalAddress());
                    sendPortCommand(activeServerSocket.getLocalPort());
                    sendCommand(FtpConstants.CMD_RETR + " " + remoteFilename);
                }

                String prelim = readResponse();
                if (!prelim.startsWith("150")) {
                    throw new IOException("Server tu choi truyen file: " + prelim);
                }

                if (dataMode == FtpConstants.DataMode.ACTIVE) {
                    activeServerSocket.setSoTimeout(15000);
                    dataSocket = activeServerSocket.accept();
                }

                // Ghi vao local file
                try (InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream(), FtpConstants.BUFFER_SIZE);
                     RandomAccessFile raf = new RandomAccessFile(localTargetFile, "rw")) {

                    if (offset > 0) {
                        raf.seek(offset);
                    } else {
                        raf.setLength(0);
                    }

                    byte[] buffer = new byte[FtpConstants.BUFFER_SIZE];
                    int bytesRead;
                    long lastTime = startTime;
                    long bytesSince = 0;

                    while ((bytesRead = dataIn.read(buffer)) != -1) {
                        raf.write(buffer, 0, bytesRead);
                        transferred += bytesRead;
                        bytesSince += bytesRead;

                        long now = System.currentTimeMillis();
                        if (now - lastTime >= 200 && callback != null) {
                            double spd = (bytesSince / 1024.0) / ((now - lastTime) / 1000.0);
                            int pct = (int) ((transferred * 100) / totalSize);
                            callback.onProgress(transferred, totalSize, spd, Math.min(100, pct));
                            lastTime = now;
                            bytesSince = 0;
                        }
                    }
                }

                String finalRes = readResponse(); // 226
                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                double avgSpeed = ((transferred - offset) / 1024.0) / (duration / 1000.0);

                // Tinh toan ma Checksum SHA-256 de xac minh tinh toan ven
                String sha256 = ChecksumUtil.calculateSHA256(localTargetFile);

                if (callback != null) {
                    callback.onComplete(transferred, duration, avgSpeed, sha256);
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                if (dataSocket != null) try { dataSocket.close(); } catch (Exception ignored) {}
                if (activeServerSocket != null) try { activeServerSocket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    public void uploadFile(File localFile, String remoteFilename, long offset, TransferProgressCallback callback) {
        new Thread(() -> {
            Socket dataSocket = null;
            ServerSocket activeServerSocket = null;
            long startTime = System.currentTimeMillis();
            long totalSize = localFile.length();
            long transferred = offset;

            try {
                sendCommand(FtpConstants.CMD_TYPE + " I");
                readResponse();

                if (offset > 0) {
                    sendCommand(FtpConstants.CMD_REST + " " + offset);
                    readResponse();
                }

                if (dataMode == FtpConstants.DataMode.PASSIVE) {
                    dataSocket = openPassiveDataSocket();
                    sendCommand(FtpConstants.CMD_STOR + " " + remoteFilename);
                } else {
                    activeServerSocket = new ServerSocket(0, 1, controlSocket.getLocalAddress());
                    sendPortCommand(activeServerSocket.getLocalPort());
                    sendCommand(FtpConstants.CMD_STOR + " " + remoteFilename);
                }

                String prelim = readResponse();
                if (!prelim.startsWith("150")) {
                    throw new IOException("Server tu choi nhan file: " + prelim);
                }

                if (dataMode == FtpConstants.DataMode.ACTIVE) {
                    activeServerSocket.setSoTimeout(15000);
                    dataSocket = activeServerSocket.accept();
                }

                try (OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream(), FtpConstants.BUFFER_SIZE);
                     RandomAccessFile raf = new RandomAccessFile(localFile, "r")) {

                    if (offset > 0) raf.seek(offset);

                    byte[] buffer = new byte[FtpConstants.BUFFER_SIZE];
                    int bytesRead;
                    long lastTime = startTime;
                    long bytesSince = 0;

                    while ((bytesRead = raf.read(buffer)) != -1) {
                        dataOut.write(buffer, 0, bytesRead);
                        transferred += bytesRead;
                        bytesSince += bytesRead;

                        long now = System.currentTimeMillis();
                        if (now - lastTime >= 200 && callback != null) {
                            double spd = (bytesSince / 1024.0) / ((now - lastTime) / 1000.0);
                            int pct = (int) ((transferred * 100) / totalSize);
                            callback.onProgress(transferred, totalSize, spd, Math.min(100, pct));
                            lastTime = now;
                            bytesSince = 0;
                        }
                    }
                    dataOut.flush();
                }

                String finalRes = readResponse(); // 226
                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                double avgSpeed = ((transferred - offset) / 1024.0) / (duration / 1000.0);
                String sha256 = ChecksumUtil.calculateSHA256(localFile);

                if (callback != null) {
                    callback.onComplete(transferred, duration, avgSpeed, sha256);
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                if (dataSocket != null) try { dataSocket.close(); } catch (Exception ignored) {}
                if (activeServerSocket != null) try { activeServerSocket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    public synchronized boolean deleteFile(String filename) throws IOException {
        sendCommand(FtpConstants.CMD_DELE + " " + filename);
        String res = readResponse();
        return res.startsWith("250");
    }

    public synchronized boolean makeDir(String dirName) throws IOException {
        sendCommand(FtpConstants.CMD_MKD + " " + dirName);
        String res = readResponse();
        return res.startsWith("257");
    }

    public synchronized boolean removeDir(String dirName) throws IOException {
        sendCommand(FtpConstants.CMD_RMD + " " + dirName);
        String res = readResponse();
        return res.startsWith("250");
    }

    public long getFileSize(String filename) {
        try {
            sendCommand(FtpConstants.CMD_SIZE + " " + filename);
            String res = readResponse();
            if (res.startsWith("213")) {
                return Long.parseLong(res.substring(4).trim());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private Socket openPassiveDataSocket() throws IOException {
        sendCommand(FtpConstants.CMD_PASV);
        String res = readResponse();
        Pattern p = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
        Matcher m = p.matcher(res);
        if (!m.find()) {
            throw new IOException("Phan hoi PASV khong hop le: " + res);
        }

        String dataHost = String.format("%s.%s.%s.%s", m.group(1), m.group(2), m.group(3), m.group(4));
        int dataPort = (Integer.parseInt(m.group(5)) * 256) + Integer.parseInt(m.group(6));

        Socket s = new Socket();
        s.connect(new InetSocketAddress(dataHost, dataPort), 10000);
        s.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);
        return s;
    }

    private void sendPortCommand(int localPort) throws IOException {
        InetAddress addr = controlSocket.getLocalAddress();
        byte[] b = addr.getAddress();
        int p1 = localPort / 256;
        int p2 = localPort % 256;
        String portArg = String.format("%d,%d,%d,%d,%d,%d",
                b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF, b[3] & 0xFF, p1, p2);
        sendCommand(FtpConstants.CMD_PORT + " " + portArg);
        readResponse();
    }

    private FileItem parseListLine(String line) {
        try {
            String[] tokens = line.trim().split("\\s+", 9);
            if (tokens.length >= 9) {
                String perm = tokens[0];
                boolean isDir = perm.startsWith("d");
                long size = Long.parseLong(tokens[4]);
                String name = tokens[8];
                return new FileItem(name, size, isDir, System.currentTimeMillis(), perm, name);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void sendCommand(String cmd) {
        writer.print(cmd + "\r\n");
        writer.flush();
        for (CommandLogListener l : logListeners) {
            l.onCommandSent(cmd);
        }
    }

    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Mat ket noi voi Server.");

        int code = 0;
        try {
            code = Integer.parseInt(line.substring(0, 3));
        } catch (Exception ignored) {}

        for (CommandLogListener l : logListeners) {
            l.onResponseReceived(code, line);
        }
        return line;
    }

    public synchronized void disconnect() {
        if (connected) {
            try {
                sendCommand(FtpConstants.CMD_QUIT);
                readResponse();
            } catch (Exception ignored) {}
        }
        connected = false;
        loggedIn = false;
        if (controlSocket != null && !controlSocket.isClosed()) {
            try { controlSocket.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized boolean register(String username, String password) throws IOException {
        sendCommand("REGISTER " + username + " " + password);
        String res = readResponse();
        return res.startsWith("200");
    }

    public synchronized String makeRoom(String roomName) throws IOException {
        sendCommand("SITE MAKEROOM " + roomName);
        return readResponse();
    }

    public synchronized String joinRoom(String roomName) throws IOException {
        sendCommand("SITE JOINROOM " + roomName);
        return readResponse();
    }

    public synchronized String approveMember(String roomName, String targetUser, String role) throws IOException {
        sendCommand("SITE APPROVE " + roomName + " " + targetUser + " " + role);
        return readResponse();
    }

    public synchronized String rejectMember(String roomName, String targetUser) throws IOException {
        sendCommand("SITE REJECT " + roomName + " " + targetUser);
        return readResponse();
    }

    public boolean isConnected() { return connected; }
    public boolean isLoggedIn() { return loggedIn; }
    public String getCurrentServerDirectory() { return currentServerDirectory; }
    public FtpConstants.DataMode getDataMode() { return dataMode; }
    public void setDataMode(FtpConstants.DataMode dataMode) { this.dataMode = dataMode; }
}
