package com.ftpsystem.common;

/**
 * Tap hang so he thong FTP 2 Kenh (RFC 959)
 */
public class FtpConstants {
    // Cong mac dinh
    public static final int DEFAULT_CONTROL_PORT = 2121;
    public static final int DEFAULT_WEB_PORT = 8080;
    public static final int PASV_PORT_MIN = 30000;
    public static final int PASV_PORT_MAX = 31000; // 1001 cong: du cho nhieu phien, tranh het cong do TIME_WAIT

    // Buffer size tang van chuyen
    public static final int BUFFER_SIZE = 8192; // 8KB TCP buffer
    public static final int SOCKET_TIMEOUT_MS = 60000; // 60s timeout

    // Cac lenh FTP chuan RFC 959 o Tang Ung Dung
    public static final String CMD_USER = "USER";
    public static final String CMD_PASS = "PASS";
    public static final String CMD_PWD  = "PWD";
    public static final String CMD_CWD  = "CWD";
    public static final String CMD_CDUP = "CDUP";
    public static final String CMD_MKD  = "MKD";
    public static final String CMD_RMD  = "RMD";
    public static final String CMD_DELE = "DELE";
    public static final String CMD_LIST = "LIST";
    public static final String CMD_RETR = "RETR"; // Download
    public static final String CMD_STOR = "STOR"; // Upload
    public static final String CMD_TYPE = "TYPE"; // A (ASCII) / I (Binary)
    public static final String CMD_PASV = "PASV"; // Passive Mode
    public static final String CMD_PORT = "PORT"; // Active Mode
    public static final String CMD_REST = "REST"; // Resumable Transfer (Restart marker)
    public static final String CMD_SIZE = "SIZE"; // Lay kich thuoc file
    public static final String CMD_FEAT = "FEAT"; // Feature negotiation
    public static final String CMD_NOOP = "NOOP"; // Keep-alive
    public static final String CMD_QUIT = "QUIT"; // Ngat ket noi

    // Che do du lieu
    public enum DataMode {
        PASSIVE,
        ACTIVE
    }

    // Loai truyen tai du lieu (Data Representation)
    public enum TransferType {
        BINARY, // Type I
        ASCII   // Type A
    }
}
