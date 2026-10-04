package com.ftpsystem.common;

/**
 * Tap ma phan hoi chuan RFC 959 (FTP Status Codes)
 */
public class FtpResponseCode {
    // 1xx: Positive Preliminary Reply
    public static final int RESTART_MARKER_REPLY = 110;
    public static final int FILE_STATUS_OK_OPENING_DATA_CONN = 150;

    // 2xx: Positive Completion Reply
    public static final int COMMAND_OK = 200;
    public static final int COMMAND_NOT_IMPLEMENTED_SUPERFLUOUS = 202;
    public static final int SYSTEM_STATUS = 211;
    public static final int DIRECTORY_STATUS = 212;
    public static final int FILE_STATUS = 213;
    public static final int SYSTEM_TYPE = 215;
    public static final int SERVICE_READY = 220;
    public static final int CLOSING_CONTROL_CONN = 221;
    public static final int CLOSING_DATA_CONN_SUCCESS = 226;
    public static final int ENTERING_PASSIVE_MODE = 227;
    public static final int USER_LOGGED_IN = 230;
    public static final int PATHNAME_CREATED = 257;

    // 3xx: Positive Intermediate Reply
    public static final int NEED_PASSWORD = 331;
    public static final int NEED_ACCOUNT = 332;
    public static final int PENDING_FURTHER_INFO = 350;

    // 4xx: Transient Negative Completion Reply
    public static final int SERVICE_NOT_AVAILABLE = 421;
    public static final int CANT_OPEN_DATA_CONN = 425;
    public static final int CONNECTION_CLOSED_TRANSFER_ABORTED = 426;

    // 5xx: Permanent Negative Completion Reply
    public static final int SYNTAX_ERROR_COMMAND_UNRECOGNIZED = 500;
    public static final int SYNTAX_ERROR_PARAMETERS = 501;
    public static final int COMMAND_NOT_IMPLEMENTED = 502;
    public static final int BAD_SEQUENCE_OF_COMMANDS = 503;
    public static final int NOT_LOGGED_IN = 530;
    public static final int FILE_UNAVAILABLE = 550;
    public static final int PAGE_TYPE_UNKNOWN = 551;
    public static final int EXCEEDED_STORAGE_ALLOCATION = 552;
    public static final int FILE_NAME_NOT_ALLOWED = 553;
    public static final int REQUESTED_ACTION_NOT_TAKEN = 554;
}
