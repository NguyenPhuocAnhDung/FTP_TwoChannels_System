package com.ftpsystem.server;

import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.common.NetworkPacketInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FTP Server Core (RFC 959)
 * Quản lý kênh điều khiển (Port 2121), danh sách client kết nối,
 * và thu thập các chỉ số mạng (Throughput, Packets, Active sessions).
 */
public final class FtpServer {
    // Singleton instance
    private static final FtpServer INSTANCE = new FtpServer();

    private int controlPort = FtpConstants.Ports.CONTROL;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final List<ClientControlHandler> activeClients = new CopyOnWriteArrayList<>();
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<NetworkPacketInfo> recentPackets = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong totalTransferredBytes = new AtomicLong(0);
    private volatile double currentThroughputKbps = 0.0;

    private ExecutorService threadPool;
    private EmbeddedWebServer webServer;

    public interface ServerEventListener {
        void onServerStateChanged(boolean running, int port);
        void onClientConnected(ClientControlHandler client);
        void onClientDisconnected(ClientControlHandler client);
        void onLog(String message, String type);
        void onPacket(NetworkPacketInfo packet);
        void onMetrics(int activeClientCount, long totalBytes, double throughputKbps);
    }

    private FtpServer() {}

    public static FtpServer getInstance() {
        return INSTANCE;
    }

    public synchronized boolean start(int port) {
        if (running) return true;
        this.controlPort = port;

        try {
            serverSocket = new ServerSocket(controlPort);
            running = true;

            // Giới hạn số thread để tránh quá tải
            threadPool = new ThreadPoolExecutor(
                    10, 50, 60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            // Luồng acceptor
            threadPool.submit(this::acceptLoop);

            // Khởi động Web Server
            try {
                webServer = new EmbeddedWebServer(FtpConstants.Ports.WEB, this);
                webServer.start();
                notifyLog("Web Admin Portal & REST API chạy tại http://localhost:" + webServer.getPort(), "INFO");
            } catch (Exception e) {
                notifyLog("Không thể bật Web Server: " + e.getMessage(), "WARN");
            }

            notifyServerState(true, controlPort);
            notifyLog("FTP Server khởi động trên Port " + controlPort, "SUCCESS");
            return true;
        } catch (IOException e) {
            notifyLog("Không thể mở cổng " + controlPort + ": " + e.getMessage(), "ERROR");
            return false;
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        // Đóng tất cả client
        for (ClientControlHandler client : activeClients) {
            client.cleanup();
        }
        activeClients.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException e) { notifyLog("Lỗi đóng server socket: " + e.getMessage(), "ERROR"); }
        }

        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }

        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        notifyServerState(false, controlPort);
        notifyLog("FTP Server đã dừng hoạt động.", "INFO");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientControlHandler handler = new ClientControlHandler(clientSocket, this);
                activeClients.add(handler);

                recordHandshake(clientSocket);

                notifyClientConnected(handler);
                notifyLog(String.format("Client [%s:%d] đã kết nối.", 
                        clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort()), "INFO");

                threadPool.submit(handler);
                updateMetrics();
            } catch (IOException e) {
                if (running) notifyLog("Lỗi accept client: " + e.getMessage(), "ERROR");
            }
        }
    }

    private void recordHandshake(Socket clientSocket) {
        String peer = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        String srv = "SERVER:" + controlPort;
        recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", peer, srv, "TCP", "SYN", 0, "Client yêu cầu mở kênh"));
        recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", srv, peer, "TCP", "SYN,ACK", 0, "Server chấp nhận kết nối"));
        recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", peer, srv, "TCP", "ACK", 0, "Bắt tay 3 bước hoàn tất"));
    }

    public void removeClient(ClientControlHandler client) {
        activeClients.remove(client);
        notifyClientDisconnected(client);
        notifyLog("Client " + client.getClientIp() + " đã ngắt kết nối.", "INFO");
        updateMetrics();
    }

    public void kickClient(ClientControlHandler client) {
        if (client != null) {
            notifyLog("Admin ngắt kết nối cưỡng bức Client: " + client.getClientIp(), "WARN");
            client.cleanup();
        }
    }

    public void logCommand(String clientIp, String username, String cmd) {
        notifyLog(String.format("[%s @ %s] %s", username, clientIp, cmd), "CMD");
    }

    public void recordPacket(NetworkPacketInfo packet) {
        recentPackets.add(0, packet);
        if (recentPackets.size() > 500) {
            recentPackets.remove(recentPackets.size() - 1);
        }
        for (ServerEventListener l : listeners) {
            l.onPacket(packet);
        }
    }

    public void addTransferredBytes(long bytes) {
        totalTransferredBytes.addAndGet(bytes);
        updateMetrics();
    }

    public void updateThroughput(double speedKbps) {
        this.currentThroughputKbps = speedKbps;
        updateMetrics();
    }

    private void updateMetrics() {
        int count = activeClients.size();
        long total = totalTransferredBytes.get();
        double spd = currentThroughputKbps;
        for (ServerEventListener l : listeners) {
            l.onMetrics(count, total, spd);
        }
    }

    public void addListener(ServerEventListener l) { listeners.add(l); }
    public void removeListener(ServerEventListener l) { listeners.remove(l); }

    private void notifyServerState(boolean state, int port) { for (ServerEventListener l : listeners) l.onServerStateChanged(state, port); }
    private void notifyClientConnected(ClientControlHandler c) { for (ServerEventListener l : listeners) l.onClientConnected(c); }
    private void notifyClientDisconnected(ClientControlHandler c) { for (ServerEventListener l : listeners) l.onClientDisconnected(c); }
    private void notifyLog(String msg, String type) { for (ServerEventListener l : listeners) l.onLog(msg, type); }

    // Getters
    public boolean isRunning() { return running; }
    public int getControlPort() { return controlPort; }
    public int getWebPort() { return webServer != null ? webServer.getPort() : FtpConstants.Ports.WEB; }
    public List<ClientControlHandler> getActiveClients() { return activeClients; }
    public List<NetworkPacketInfo> getRecentPackets() { return new ArrayList<>(recentPackets); }
    public long getTotalTransferredBytes() { return totalTransferredBytes.get(); }
    public double getCurrentThroughputKbps() { return currentThroughputKbps; }
}
