package com.ftpsystem.server;

import com.ftpsystem.common.FtpConstants;
import com.ftpsystem.common.NetworkPacketInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * May chu FTP Server Core (RFC 959)
 * Quan ly Kenh Dieu Khien (Port 2121), danh sach Client dang ket noi,
 * va thu thap cac chi so mang (Throughput, Packets, Active sessions).
 */
public class FtpServer {
    private static FtpServer instance;

    private int controlPort = FtpConstants.DEFAULT_CONTROL_PORT;
    private ServerSocket serverSocket;
    private boolean running = false;

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

    public static synchronized FtpServer getInstance() {
        if (instance == null) {
            instance = new FtpServer();
        }
        return instance;
    }

    public synchronized boolean start(int port) {
        if (running) return true;
        this.controlPort = port;

        try {
            serverSocket = new ServerSocket(controlPort);
            running = true;
            threadPool = Executors.newCachedThreadPool();

            // Khoi dong luong chap nhan ket noi (Acceptor Thread)
            threadPool.submit(this::acceptLoop);

            // Khoi dong Embedded Web Server (Port 8080)
            try {
                webServer = new EmbeddedWebServer(FtpConstants.DEFAULT_WEB_PORT, this);
                webServer.start();
                notifyLog("Web Admin Portal & REST API da khoi dong tai http://localhost:" + FtpConstants.DEFAULT_WEB_PORT, "INFO");
            } catch (Exception e) {
                notifyLog("Khong the bat Web Server: " + e.getMessage(), "WARN");
            }

            notifyServerState(true, controlPort);
            notifyLog("FTP Server da khoi dong tren Port " + controlPort + " (Tang Van Chuyen: TCP)", "SUCCESS");
            return true;
        } catch (IOException e) {
            notifyLog("Khong the mo cong " + controlPort + ": " + e.getMessage(), "ERROR");
            return false;
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        // Dong tat ca Client dang ket noi
        for (ClientControlHandler client : activeClients) {
            client.cleanup();
        }
        activeClients.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (Exception ignored) {}
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
        notifyLog("FTP Server da dung hoat dong.", "INFO");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientControlHandler handler = new ClientControlHandler(clientSocket, this);
                activeClients.add(handler);

                // Ghi nhan bat tay 3 buoc TCP (SUY DIEN tu accept() thanh cong - Java khong lo co TCP that)
                String peer = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                String srv = "SERVER:" + controlPort;
                recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", peer, srv,
                        "TCP", "SYN", 0, "[Suy dien tu Socket] Client yeu cau mo Kenh Dieu Khien"));
                recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", srv, peer,
                        "TCP", "SYN,ACK", 0, "[Suy dien tu Socket] Server chap nhan ket noi"));
                recordPacket(new NetworkPacketInfo("TRANSPORT", "CONTROL_CHANNEL", peer, srv,
                        "TCP", "ACK", 0, "[Suy dien tu Socket] Bat tay 3 buoc hoan tat - ESTABLISHED"));

                notifyClientConnected(handler);
                notifyLog(String.format("Client [%s:%d] da ket noi vao Kenh Dieu Khien.", 
                        clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort()), "INFO");

                threadPool.submit(handler);
                updateMetrics();
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    public void removeClient(ClientControlHandler client) {
        activeClients.remove(client);
        notifyClientDisconnected(client);
        notifyLog(String.format("Client [%s:%d] da ngat ket noi.", client.getClientIp(), client.getClientPort()), "INFO");
        updateMetrics();
    }

    public void kickClient(ClientControlHandler client) {
        if (client != null) {
            notifyLog("Admin da ngat ket noi cuong buc Client: " + client.getClientIp(), "WARN");
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

    public void addListener(ServerEventListener l) {
        listeners.add(l);
    }

    public void removeListener(ServerEventListener l) {
        listeners.remove(l);
    }

    private void notifyServerState(boolean state, int port) {
        for (ServerEventListener l : listeners) l.onServerStateChanged(state, port);
    }

    private void notifyClientConnected(ClientControlHandler c) {
        for (ServerEventListener l : listeners) l.onClientConnected(c);
    }

    private void notifyClientDisconnected(ClientControlHandler c) {
        for (ServerEventListener l : listeners) l.onClientDisconnected(c);
    }

    private void notifyLog(String msg, String type) {
        for (ServerEventListener l : listeners) l.onLog(msg, type);
    }

    public boolean isRunning() { return running; }
    public int getControlPort() { return controlPort; }
    public List<ClientControlHandler> getActiveClients() { return activeClients; }
    public List<NetworkPacketInfo> getRecentPackets() { return new ArrayList<>(recentPackets); }
    public long getTotalTransferredBytes() { return totalTransferredBytes.get(); }
    public double getCurrentThroughputKbps() { return currentThroughputKbps; }
}
