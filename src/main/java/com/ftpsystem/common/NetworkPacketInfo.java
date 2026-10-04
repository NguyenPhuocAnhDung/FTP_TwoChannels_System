package com.ftpsystem.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mo hinh mo phong goi tin o Tang Van Chuyen (Transport Layer - TCP)
 * va Tang Ung Dung (Application Layer - FTP/HTTP).
 * Phuc vu truc quan hoa hoat dong mang cho giang vien quan sat.
 */
public class NetworkPacketInfo {
    private final String timestamp;
    private final String layer;            // TRANSPORT (TCP) hoac APPLICATION (FTP/HTTP)
    private final String channel;          // CONTROL_CHANNEL (2121) hoac DATA_CHANNEL
    private final String sourceAddress;    // IP:Port gui
    private final String destAddress;      // IP:Port nhan
    private final String protocol;         // TCP, FTP, HTTP
    private final String flags;            // SYN, ACK, PSH, FIN (mo phong TCP flags)
    private final int payloadSize;         // Kich thuoc du lieu (Bytes)
    private final String content;          // Noi dung lenh/du lieu

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public NetworkPacketInfo(String layer, String channel, String sourceAddress, String destAddress,
                             String protocol, String flags, int payloadSize, String content) {
        this.timestamp = LocalDateTime.now().format(FORMATTER);
        this.layer = layer;
        this.channel = channel;
        this.sourceAddress = sourceAddress;
        this.destAddress = destAddress;
        this.protocol = protocol;
        this.flags = flags;
        this.payloadSize = payloadSize;
        this.content = content;
    }

    public String getTimestamp() { return timestamp; }
    public String getLayer() { return layer; }
    public String getChannel() { return channel; }
    public String getSourceAddress() { return sourceAddress; }
    public String getDestAddress() { return destAddress; }
    public String getProtocol() { return protocol; }
    public String getFlags() { return flags; }
    public int getPayloadSize() { return payloadSize; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return String.format("[%s] [%s/%s] %s -> %s | Flags: %s | Size: %d B | %s",
                timestamp, layer, protocol, sourceAddress, destAddress, flags, payloadSize, content);
    }
}
