package com.ftpsystem.server;

import com.ftpsystem.common.*;
import com.ftpsystem.database.LogDAO;
import com.ftpsystem.database.UserDAO;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Xu ly Phien Keno Dieu Khien (Control Channel) qua Socket TCP Port 2121.
 * Thuc hien may trang thai (State Machine) giao thuc RFC 959 o Tang Ung Dung.
 */
public class ClientControlHandler implements Runnable {
    private final Socket controlSocket;
    private final FtpServer server;
    private final String clientIp;
    private final int clientPort;

    private BufferedReader reader;
    private PrintWriter writer;

    private boolean running = true;
    private boolean loggedIn = false;
    private String pendingUsername;
    private User currentUser;

    private File userHomeDir;
    private File currentDir; // Thu muc hien tai tren he thong file ao
    private long restOffset = 0; // Vi tri tiep tuc tai (REST command)
    private FtpConstants.TransferType transferType = FtpConstants.TransferType.BINARY;

    private final DataChannelHandler dataChannel = new DataChannelHandler();

    public ClientControlHandler(Socket controlSocket, FtpServer server) {
        this.controlSocket = controlSocket;
        this.server = server;
        this.clientIp = controlSocket.getInetAddress().getHostAddress();
        this.clientPort = controlSocket.getPort();
    }

    @Override
    public void run() {
        try {
            controlSocket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);
            controlSocket.setKeepAlive(true);
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"), true);
            dataChannel.setPacketListener(server::recordPacket, clientIp + ":" + clientPort);

            // Gui cau chao ban dau
            reply(FtpResponseCode.SERVICE_READY, "220 Chuyen de He Thong FTP 2 Kenh (RFC 959) Java 23 san sang phuc vu.");

            String line;
            while (running) {
                try {
                    line = reader.readLine();
                    if (line == null) break;
                } catch (java.net.SocketTimeoutException ste) {
                    reply(FtpResponseCode.SERVICE_NOT_AVAILABLE, "421 Phien lam viec het thoi gian cho (Idle Timeout). Server dong ket noi.");
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) continue;

                // Ghi nhan goi tin Tang Ung Dung va Tang Van Chuyen
                server.recordPacket(new NetworkPacketInfo(
                        "APPLICATION", "CONTROL_CHANNEL",
                        clientIp + ":" + clientPort, "SERVER:" + FtpConstants.DEFAULT_CONTROL_PORT,
                        "FTP", "PSH,ACK", line.length(), line
                ));

                handleCommand(line);
            }
        } catch (IOException e) {
            // Client ngat ket noi
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String commandLine) throws IOException {
        String[] parts = commandLine.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String arg = (parts.length > 1) ? parts[1] : "";

        server.logCommand(clientIp, (currentUser != null ? currentUser.getUsername() : "ANONYMOUS"), cmd + (arg.isEmpty() ? "" : " " + arg));

        switch (cmd) {
            case FtpConstants.CMD_USER:
                handleUser(arg);
                break;
            case FtpConstants.CMD_PASS:
                handlePass(arg);
                break;
            case FtpConstants.CMD_PWD:
                handlePwd();
                break;
            case FtpConstants.CMD_CWD:
                handleCwd(arg);
                break;
            case FtpConstants.CMD_CDUP:
                handleCdup();
                break;
            case FtpConstants.CMD_TYPE:
                handleType(arg);
                break;
            case FtpConstants.CMD_PASV:
                handlePasv();
                break;
            case FtpConstants.CMD_PORT:
                handlePort(arg);
                break;
            case FtpConstants.CMD_REST:
                handleRest(arg);
                break;
            case FtpConstants.CMD_SIZE:
                handleSize(arg);
                break;
            case FtpConstants.CMD_LIST:
                handleList(arg);
                break;
            case FtpConstants.CMD_RETR:
                handleRetr(arg);
                break;
            case FtpConstants.CMD_STOR:
                handleStor(arg);
                break;
            case FtpConstants.CMD_DELE:
                handleDele(arg);
                break;
            case FtpConstants.CMD_MKD:
                handleMkd(arg);
                break;
            case FtpConstants.CMD_RMD:
                handleRmd(arg);
                break;
            case FtpConstants.CMD_FEAT:
                reply(FtpResponseCode.SYSTEM_STATUS, "211-Features:\r\n PASV\r\n REST STREAM\r\n SIZE\r\n UTF8\r\n211 End");
                break;
            case FtpConstants.CMD_NOOP:
                reply(FtpResponseCode.COMMAND_OK, "200 NOOP OK");
                break;
            case FtpConstants.CMD_QUIT:
                reply(FtpResponseCode.CLOSING_CONTROL_CONN, "221 Tam biet quy khach.");
                running = false;
                break;
            case "REGISTER":
                handleRegister(arg);
                break;
            case "SITE":
                handleSite(arg);
                break;
            default:
                reply(FtpResponseCode.COMMAND_NOT_IMPLEMENTED, "502 Lenh khong duoc ho tro tren he thong.");
                break;
        }
    }

    private void handleRegister(String arg) {
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap: REGISTER <username> <password>");
            return;
        }
        String u = parts[0].trim();
        String p = parts[1].trim();
        boolean ok = UserDAO.getInstance().registerUser(u, p);
        if (ok) {
            reply(FtpResponseCode.COMMAND_OK, "200 Dang ky tai khoan thanh cong! Ban co the dang nhap ngay.");
        } else {
            reply(FtpResponseCode.FILE_NAME_NOT_ALLOWED, "553 Ten tai khoan da ton tai hoac khong hop le.");
        }
    }

    private void handleSite(String siteArg) {
        if (!checkAuth()) return;
        try {
            String[] parts = siteArg.split("\\s+", 4);
            if (parts.length == 0 || parts[0].isEmpty()) {
                reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Thieu lenh SITE.");
                return;
            }

            String sub = parts[0].toUpperCase();
            com.ftpsystem.database.RoomDAO roomDao = com.ftpsystem.database.RoomDAO.getInstance();

            switch (sub) {
                case "MAKEROOM":
                    if (parts.length < 2) {
                        reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap: SITE MAKEROOM <ten_phong>");
                        return;
                    }
                    String rName = parts[1].trim();
                    boolean created = roomDao.createRoom(rName, currentUser.getUsername(), "Phong cua " + currentUser.getUsername());
                    if (created) {
                        reply(FtpResponseCode.COMMAND_OK, "200 Tao phong '" + rName + "' thanh cong! Ban la Chu phong (Owner).");
                    } else {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Phong da ton tai hoac ten phong khong hop le (chi dung chu/so/dau gach, tu 3-50 ky tu).");
                    }
                    break;

                case "JOINROOM":
                    if (parts.length < 2) {
                        reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap: SITE JOINROOM <ten_phong>");
                        return;
                    }
                    String joinTarget = parts[1].trim();
                    boolean requested = roomDao.requestJoinRoom(joinTarget, currentUser.getUsername());
                    if (requested) {
                        reply(FtpResponseCode.COMMAND_OK, "200 Da gui yeu cau vao phong '" + joinTarget + "'. Cho Chu phong phe duyet.");
                    } else {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong tim thay phong hoac ban da gui yeu cau truoc do.");
                    }
                    break;

                case "APPROVE":
                    if (parts.length < 3) {
                        reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap: SITE APPROVE <ten_phong> <username> [EDITOR|VIEWER]");
                        return;
                    }
                    String roomApprove = parts[1].trim();
                    String targetUser = parts[2].trim();
                    String role = (parts.length >= 4) ? parts[3].trim().toUpperCase() : "EDITOR";
                    if (!"VIEWER".equals(role) && !"EDITOR".equals(role)) role = "EDITOR";

                    // Kiem tra nguoi goi co phai Chu phong hoac Admin khong
                    com.ftpsystem.common.Room r = roomDao.findByRoomName(roomApprove);
                    if (r == null) {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong tim thay phong: " + roomApprove);
                        return;
                    }
                    if (!r.getOwnerUsername().equalsIgnoreCase(currentUser.getUsername()) && !currentUser.isAdmin()) {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong phai Chu phong (Owner) cua phong nay!");
                        return;
                    }

                    boolean appOk = roomDao.approveMember(roomApprove, targetUser, role, "EDITOR".equals(role), false);
                    if (appOk) {
                        reply(FtpResponseCode.COMMAND_OK, "200 Da phe duyet thanh vien '" + targetUser + "' vao phong voi vai tro " + role + ".");
                    } else {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Loi khi phe duyet thanh vien.");
                    }
                    break;

                case "REJECT":
                    if (parts.length < 3) {
                        reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap: SITE REJECT <ten_phong> <username>");
                        return;
                    }
                    String roomReject = parts[1].trim();
                    String targetRej = parts[2].trim();
                    com.ftpsystem.common.Room rRej = roomDao.findByRoomName(roomReject);
                    if (rRej == null || (!rRej.getOwnerUsername().equalsIgnoreCase(currentUser.getUsername()) && !currentUser.isAdmin())) {
                        reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen tren phong nay.");
                        return;
                    }
                    roomDao.rejectMember(roomReject, targetRej);
                    reply(FtpResponseCode.COMMAND_OK, "200 Da tu choi/xoa thanh vien '" + targetRej + "' khoi phong.");
                    break;

                default:
                    reply(FtpResponseCode.COMMAND_NOT_IMPLEMENTED, "502 Lenh SITE chua duoc ho tro.");
                    break;
            }
        } catch (Exception e) {
            reply(FtpResponseCode.REQUESTED_ACTION_NOT_TAKEN, "451 Loi xu ly lenh SITE: " + e.getMessage());
        }
    }

    private void handleUser(String username) {
        if (username.isEmpty()) {
            reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Thieu tham so username.");
            return;
        }
        this.pendingUsername = username;
        if ("anonymous".equalsIgnoreCase(username)) {
            reply(FtpResponseCode.NEED_PASSWORD, "331 Khach an danh, gui bat ky mat khau nao.");
        } else {
            reply(FtpResponseCode.NEED_PASSWORD, "331 Yeu cau nhap mat khau cho tai khoan " + username);
        }
    }

    private void handlePass(String password) {
        if (pendingUsername == null) {
            reply(FtpResponseCode.BAD_SEQUENCE_OF_COMMANDS, "503 Vui long gui lenh USER truoc.");
            return;
        }

        boolean ok = UserDAO.getInstance().authenticate(pendingUsername, password);
        if (ok) {
            this.loggedIn = true;
            this.currentUser = UserDAO.getInstance().findByUsername(pendingUsername);
            initUserStorage();
            reply(FtpResponseCode.USER_LOGGED_IN, "230 Dang nhap thanh cong. Quyen: " + currentUser.getRole());
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "LOGIN", null, 0, 0, 0, "SUCCESS", "Dang nhap thanh cong"));
        } else {
            reply(FtpResponseCode.NOT_LOGGED_IN, "530 Ten dang nhap hoac mat khau khong chinh xac.");
            LogDAO.getInstance().log(new TransferLog(pendingUsername, clientIp, "LOGIN", null, 0, 0, 0, "FAILED", "Sai mat khau"));
        }
    }

    private void initUserStorage() {
        if (currentUser.isAdmin()) {
            this.userHomeDir = new File("storage").toPath().toAbsolutePath().normalize().toFile();
        } else {
            this.userHomeDir = new File("storage/users/" + currentUser.getUsername()).toPath().toAbsolutePath().normalize().toFile();
        }
        if (!userHomeDir.exists()) {
            userHomeDir.mkdirs();
        }
        // Tao san thu muc Public va Phong_Nhom
        new File("storage/public").mkdirs();
        this.currentDir = userHomeDir;
    }

    private void handlePwd() {
        if (!checkAuth()) return;
        String virtualPath = getVirtualPath(currentDir);
        reply(FtpResponseCode.PATHNAME_CREATED, "257 \"" + virtualPath + "\" la thu muc hien tai.");
    }

    private void handleCwd(String target) {
        if (!checkAuth()) return;
        File resolved = resolveFile(target);
        if (resolved != null && resolved.exists() && resolved.isDirectory()) {
            this.currentDir = resolved;
            reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "250 Thu muc da chuyen sang \"" + getVirtualPath(currentDir) + "\"");
        } else {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Thu muc khong ton tai hoac khong co quyen.");
        }
    }

    private void handleCdup() {
        if (!checkAuth()) return;
        if (currentDir.equals(userHomeDir)) {
            reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "250 Da o thu muc goc cao nhat.");
            return;
        }
        File parent = resolveFile("..");
        if (parent != null && parent.isDirectory()) {
            this.currentDir = parent;
            reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "250 Chuyen len thu muc cha \"" + getVirtualPath(currentDir) + "\"");
        } else {
            this.currentDir = userHomeDir;
            reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "250 Chuyen ve thu muc goc.");
        }
    }

    private void handleType(String type) {
        if ("I".equalsIgnoreCase(type)) {
            this.transferType = FtpConstants.TransferType.BINARY;
            reply(FtpResponseCode.COMMAND_OK, "200 Che do truyen tai chuyen sang Binary (Type I).");
        } else if ("A".equalsIgnoreCase(type)) {
            this.transferType = FtpConstants.TransferType.ASCII;
            reply(FtpResponseCode.COMMAND_OK, "200 Che do truyen tai chuyen sang ASCII (Type A).");
        } else {
            reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Chi ho tro che do Type I hoac Type A.");
        }
    }

    private void handlePasv() {
        if (!checkAuth()) return;
        try {
            String pasvParam = dataChannel.openPassive(controlSocket.getLocalAddress());
            reply(FtpResponseCode.ENTERING_PASSIVE_MODE, "227 Entering Passive Mode (" + pasvParam + ").");
        } catch (IOException e) {
            reply(FtpResponseCode.CANT_OPEN_DATA_CONN, "425 Khong the mo kenh du lieu PASV: " + e.getMessage());
        }
    }

    private void handlePort(String portArg) {
        if (!checkAuth()) return;
        try {
            String[] parts = portArg.split(",");
            if (parts.length != 6) {
                reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Cu phap lenh PORT sai. Can 6 so.");
                return;
            }
            String host = String.format("%s.%s.%s.%s", parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim());
            int port = (Integer.parseInt(parts[4].trim()) * 256) + Integer.parseInt(parts[5].trim());
            // Chong tan cong FTP Bounce: chi cho phep ket noi nguoc ve DUNG IP cua Client dang dieu khien
            if (!host.equals(clientIp) || port < 1024 || port > 65535) {
                reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "504 PORT bi tu choi: chi chap nhan IP cua chinh Client va cong >= 1024.");
                return;
            }
            dataChannel.setActiveMode(host, port);
            reply(FtpResponseCode.COMMAND_OK, "200 Lenh PORT chap nhan. Host: " + host + ":" + port);
        } catch (Exception e) {
            reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Loi phan tich lenh PORT: " + e.getMessage());
        }
    }

    private void handleRest(String offsetStr) {
        if (!checkAuth()) return;
        try {
            this.restOffset = Long.parseLong(offsetStr);
            reply(FtpResponseCode.PENDING_FURTHER_INFO, "350 Restart marker tai vi tri byte " + restOffset + ". San sang tiep tuc.");
        } catch (NumberFormatException e) {
            reply(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "501 Vi tri restart khong hop le.");
        }
    }

    private void handleSize(String filename) {
        if (!checkAuth()) return;
        File f = resolveFile(filename);
        if (f != null && f.exists() && f.isFile()) {
            reply(FtpResponseCode.FILE_STATUS, "213 " + f.length());
        } else {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong tim thay file hoac file la thu muc.");
        }
    }

    private void handleList(String arg) {
        if (!checkAuth()) return;
        File target = currentDir;
        if (!arg.isEmpty() && !arg.startsWith("-")) {
            File custom = resolveFile(arg);
            if (custom != null && custom.exists()) target = custom;
        }

        StringBuilder sb = new StringBuilder();

        // Neu dang o thu muc goc cua user khong phai admin: Them shortcut toi cac phong nhom
        if (!currentUser.isAdmin() && target.equals(userHomeDir)) {
            List<com.ftpsystem.common.Room> rooms = com.ftpsystem.database.RoomDAO.getInstance().getApprovedRoomsForUser(currentUser.getUsername());
            for (com.ftpsystem.common.Room r : rooms) {
                File rf = new File(r.getStoragePath());
                rf.mkdirs();
                sb.append(String.format("drwxrwxr-x 1 ftp ftp 0 Jan 01 00:00 [PHONG]_%s\r\n", r.getRoomName()));
            }
            sb.append("drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 [PUBLIC_CHUNG]\r\n");
        }

        File[] files = target.listFiles();
        if (files != null) {
            for (File f : files) {
                sb.append(FileItem.fromFile(f).toFtpListFormat());
            }
        }

        reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "150 Mo ket noi kenh du lieu de truyen danh sach file.");
        try {
            dataChannel.sendDataString(sb.toString());
            reply(FtpResponseCode.CLOSING_DATA_CONN_SUCCESS, "226 Hoan tat truyen danh sach file.");
        } catch (IOException e) {
            reply(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "426 Loi truyen du lieu qua kenh Data: " + e.getMessage());
        }
    }

    private void handleRetr(String filename) {
        if (!checkAuth()) return;
        if (!currentUser.isCanRead()) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen doc (CanRead = false).");
            return;
        }

        File file = resolveFile(filename);
        if (file == null || !file.exists() || !file.isFile()) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong tim thay tap tin: " + filename);
            return;
        }

        long offset = restOffset;
        restOffset = 0; // Reset offset sau khi dung
        if (offset < 0 || offset > file.length()) {
            reply(FtpResponseCode.REQUESTED_ACTION_NOT_TAKEN, "554 Vi tri REST (" + offset + ") vuot qua kich thuoc tap tin (" + file.length() + " bytes).");
            return;
        }

        reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, 
                String.format("150 Bat dau gui tap tin \"%s\" (%d bytes) tu byte %d.", file.getName(), file.length(), offset));

        try {
            DataChannelHandler.TransferResult res = dataChannel.sendFile(file, offset, currentUser.getMaxSpeedKbps(), (tx, total, spd) -> {
                server.updateThroughput(spd);
            });
            reply(FtpResponseCode.CLOSING_DATA_CONN_SUCCESS, 
                    String.format("226 Truyen tap tin hoan tat (%d bytes trong %d ms, toc do %.2f KB/s).", 
                            res.bytesTransferred, res.durationMs, res.avgSpeedKbps));

            server.addTransferredBytes(res.bytesTransferred);
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "DOWNLOAD", file.getName(), 
                    res.bytesTransferred, res.durationMs, res.avgSpeedKbps, "SUCCESS", "Download thanh cong"));
        } catch (IOException e) {
            reply(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "426 Loi trong qua trinh truyen file: " + e.getMessage());
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "DOWNLOAD", file.getName(), 
                    0, 0, 0, "FAILED", e.getMessage()));
        }
    }

    private void handleStor(String filename) {
        if (!checkAuth()) return;

        File file = resolveFile(filename);
        if (file == null || filename == null || filename.isEmpty() || file.isDirectory()) {
            reply(FtpResponseCode.FILE_NAME_NOT_ALLOWED, "553 Ten tap tin khong hop le hoac bi Sandbox tu choi.");
            return;
        }
        if (!allowWrite(file, false)) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen ghi tai vi tri nay (Viewer / CanWrite = false).");
            restOffset = 0;
            return;
        }

        long offset = restOffset;
        restOffset = 0;
        // Tiep tuc upload: offset khong duoc vuot qua kich thuoc phan da co tren Server (tranh file "thung lo")
        if (offset < 0 || offset > (file.exists() ? file.length() : 0)) {
            reply(FtpResponseCode.REQUESTED_ACTION_NOT_TAKEN, "554 Vi tri REST (" + offset + ") khong hop le voi tap tin hien co tren Server.");
            return;
        }

        reply(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONN, "150 San sang nhan du lieu tu kenh data.");

        try {
            DataChannelHandler.TransferResult res = dataChannel.receiveFile(file, offset, currentUser.getMaxSpeedKbps(), (tx, total, spd) -> {
                server.updateThroughput(spd);
            });
            reply(FtpResponseCode.CLOSING_DATA_CONN_SUCCESS, 
                    String.format("226 Nhan tap tin hoan tat (%d bytes, toc do %.2f KB/s).", res.bytesTransferred, res.avgSpeedKbps));

            server.addTransferredBytes(res.bytesTransferred);
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "UPLOAD", file.getName(), 
                    res.bytesTransferred, res.durationMs, res.avgSpeedKbps, "SUCCESS", "Upload thanh cong"));
        } catch (IOException e) {
            reply(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "426 Loi nhan du lieu file: " + e.getMessage());
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "UPLOAD", file.getName(), 
                    0, 0, 0, "FAILED", e.getMessage()));
        }
    }

    private void handleDele(String filename) {
        if (!checkAuth()) return;
        File f = resolveFile(filename);
        if (f == null) {
            reply(FtpResponseCode.FILE_NAME_NOT_ALLOWED, "553 Duong dan bi tu choi boi Sandbox.");
            return;
        }
        if (!allowWrite(f, true)) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen xoa tai vi tri nay.");
            return;
        }
        if (f.exists() && f.isFile() && f.delete()) {
            reply(FtpResponseCode.CLOSING_DATA_CONN_SUCCESS, "250 Xoa tap tin thanh cong.");
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "DELETE", filename, 0, 0, 0, "SUCCESS", "Xoa file"));
        } else {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong the xoa tap tin.");
        }
    }

    private void handleMkd(String dirName) {
        if (!checkAuth()) return;
        File dir = resolveFile(dirName);
        if (dir == null) {
            reply(FtpResponseCode.FILE_NAME_NOT_ALLOWED, "553 Duong dan bi tu choi boi Sandbox.");
            return;
        }
        if (!allowWrite(dir, false)) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen tao thu muc tai vi tri nay.");
            return;
        }
        if (!dir.exists() && dir.mkdirs()) {
            reply(FtpResponseCode.PATHNAME_CREATED, "257 \"" + getVirtualPath(dir) + "\" tao thu muc thanh cong.");
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "MKDIR", dirName, 0, 0, 0, "SUCCESS", "Tao thu muc"));
        } else {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong the tao thu muc.");
        }
    }

    private void handleRmd(String dirName) {
        if (!checkAuth()) return;
        File dir = resolveFile(dirName);
        if (dir == null) {
            reply(FtpResponseCode.FILE_NAME_NOT_ALLOWED, "553 Duong dan bi tu choi boi Sandbox.");
            return;
        }
        if (!allowWrite(dir, true)) {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Ban khong co quyen xoa thu muc tai vi tri nay.");
            return;
        }
        if (dir.exists() && dir.isDirectory() && !dir.equals(userHomeDir) && dir.delete()) {
            reply(FtpResponseCode.CLOSING_DATA_CONN_SUCCESS, "250 Xoa thu muc thanh cong.");
            LogDAO.getInstance().log(new TransferLog(currentUser.getUsername(), clientIp, "RMD", dirName, 0, 0, 0, "SUCCESS", "Xoa thu muc"));
        } else {
            reply(FtpResponseCode.FILE_UNAVAILABLE, "550 Khong the xoa thu muc (thu muc khong rong hoac khong co quyen).");
        }
    }

    private boolean checkAuth() {
        if (!loggedIn) {
            reply(FtpResponseCode.NOT_LOGGED_IN, "530 Vui long dang nhap (USER/PASS) truoc.");
            return false;
        }
        return true;
    }

    private void reply(int code, String text) {
        writer.print(text + "\r\n");
        writer.flush();

        // Ghi nhan goi tin phan hoi o Tang Ung Dung
        server.recordPacket(new NetworkPacketInfo(
                "APPLICATION", "CONTROL_CHANNEL",
                "SERVER:" + FtpConstants.DEFAULT_CONTROL_PORT, clientIp + ":" + clientPort,
                "FTP", "ACK", text.length(), text
        ));
    }

    private static final String ROOM_PREFIX = "[PHONG]_";
    private static final String PUBLIC_TOKEN = "[PUBLIC_CHUNG]";

    /** Cac thu muc goc user duoc phep cham toi: home + public + cac phong da duoc duyet. */
    private List<Path> allowedRoots() {
        List<Path> roots = new java.util.ArrayList<>();
        roots.add(userHomeDir.toPath());
        if (currentUser != null && currentUser.isAdmin()) {
            return roots; // Admin: home chinh la thu muc "storage" goc
        }
        roots.add(Paths.get("storage", "public"));
        for (Room r : com.ftpsystem.database.RoomDAO.getInstance().getApprovedRoomsForUser(currentUser.getUsername())) {
            roots.add(Paths.get(r.getStoragePath()));
        }
        return roots;
    }

    /**
     * Giai quyet duong dan Client gui len thanh File that, DA QUA SANDBOX.
     * Tra ve null neu duong dan bi chan (Path Traversal, o dia tuyet doi, phong chua duoc duyet...).
     */
    private File resolveFile(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return currentDir;
        if (!PathSandbox.isSafeInput(pathStr)) return null;
        try {
            String s = pathStr.replace('\\', '/').trim();
            String tokenForm = s.startsWith("/") ? s.substring(1) : s;
            List<Path> roots = allowedRoots();

            // Loi tat ao: [PUBLIC_CHUNG][/...] va [PHONG]_<Ten>[/...] (chi khi la thanh vien da duoc duyet)
            if (!currentUser.isAdmin() && (tokenForm.startsWith(PUBLIC_TOKEN) || tokenForm.startsWith(ROOM_PREFIX))) {
                int slash = tokenForm.indexOf('/');
                String head = slash < 0 ? tokenForm : tokenForm.substring(0, slash);
                String rest = slash < 0 ? "" : tokenForm.substring(slash + 1);
                Path root;
                if (head.equals(PUBLIC_TOKEN)) {
                    root = Paths.get("storage", "public");
                } else {
                    String roomName = head.substring(ROOM_PREFIX.length());
                    Room room = null;
                    for (Room r : com.ftpsystem.database.RoomDAO.getInstance().getApprovedRoomsForUser(currentUser.getUsername())) {
                        if (r.getRoomName().equalsIgnoreCase(roomName)) { room = r; break; }
                    }
                    if (room == null) return null; // Chua duoc duyet vao phong -> chan
                    root = Paths.get(room.getStoragePath());
                }
                Path resolved = root.resolve(rest).toAbsolutePath().normalize();
                return PathSandbox.isInside(resolved, java.util.Collections.singletonList(root)) ? resolved.toFile() : null;
            }

            Path resolved = PathSandbox.resolve(currentDir.toPath(), userHomeDir.toPath(), s, roots);
            return resolved == null ? null : resolved.toFile();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isChildOf(File child, File parent) {
        return PathSandbox.isInside(child.toPath(), java.util.Collections.singletonList(parent.toPath()));
    }

    /** Tim phong (da duyet) chua file f; null neu f khong nam trong phong nao. */
    private Room roomContaining(File f) {
        if (currentUser == null) return null;
        for (Room r : com.ftpsystem.database.RoomDAO.getInstance().getApprovedRoomsForUser(currentUser.getUsername())) {
            if (isChildOf(f, new File(r.getStoragePath()))) return r;
        }
        return null;
    }

    /**
     * Phan quyen ghi/xoa: trong phong -> theo vai tro thanh vien (Owner / Editor / Viewer);
     * ngoai phong -> theo quyen toan cuc cua tai khoan.
     */
    private boolean allowWrite(File f, boolean delete) {
        if (currentUser.isAdmin()) return true;
        Room room = roomContaining(f);
        if (room != null) {
            if (room.getOwnerUsername().equalsIgnoreCase(currentUser.getUsername())) return true;
            RoomMember m = com.ftpsystem.database.RoomDAO.getInstance().getMemberRole(room.getRoomName(), currentUser.getUsername());
            if (m == null) return false;
            return delete ? m.isCanDelete() : m.isCanUpload();
        }
        return delete ? currentUser.isCanDelete() : currentUser.isCanWrite();
    }

    /** Duong dan ao hien thi cho Client (khong lo duong dan that cua may chu). */
    private String getVirtualPath(File file) {
        Path real = PathSandbox.realOf(file.toPath());
        Path home = PathSandbox.realOf(userHomeDir.toPath());
        if (real.startsWith(home)) {
            String rel = home.relativize(real).toString().replace('\\', '/');
            return rel.isEmpty() ? "/" : "/" + rel;
        }
        Path pub = PathSandbox.realOf(Paths.get("storage", "public"));
        if (real.startsWith(pub)) {
            String rel = pub.relativize(real).toString().replace('\\', '/');
            return "/" + PUBLIC_TOKEN + (rel.isEmpty() ? "" : "/" + rel);
        }
        Room room = roomContaining(file);
        if (room != null) {
            Path rr = PathSandbox.realOf(Paths.get(room.getStoragePath()));
            String rel = rr.relativize(real).toString().replace('\\', '/');
            return "/" + ROOM_PREFIX + room.getRoomName() + (rel.isEmpty() ? "" : "/" + rel);
        }
        return "/";
    }

    public void cleanup() {
        boolean wasOpen = !controlSocket.isClosed();
        running = false;
        dataChannel.close();
        try { controlSocket.close(); } catch (Exception ignored) {}
        if (wasOpen) {
            server.recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL",
                    clientIp + ":" + clientPort, "SERVER:" + FtpConstants.DEFAULT_CONTROL_PORT,
                    "TCP", "FIN,ACK", 0, "[Suy dien tu Socket] Kenh Dieu Khien dong (QUIT / mat ket noi)"));
        }
        server.removeClient(this);
    }

    public String getClientIp() { return clientIp; }
    public int getClientPort() { return clientPort; }
    public User getCurrentUser() { return currentUser; }
    public boolean isLoggedIn() { return loggedIn; }
}
