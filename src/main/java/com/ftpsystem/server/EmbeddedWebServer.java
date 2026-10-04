package com.ftpsystem.server;

import com.ftpsystem.common.FileItem;
import com.ftpsystem.common.NetworkPacketInfo;
import com.ftpsystem.common.TransferLog;
import com.ftpsystem.database.DatabaseManager;
import com.ftpsystem.database.LogDAO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web Server HTTP/1.1 nhung tren Java (Port 8080)
 * Cung cap Web Admin Portal va REST API giam sat he thong mang,
 * the hien tinh da dang cong nghe (Java + HTML5/CSS3/JavaScript).
 */
public class EmbeddedWebServer {
    private final int port;
    private final FtpServer ftpServer;
    private HttpServer httpServer;

    public EmbeddedWebServer(int port, FtpServer ftpServer) {
        this.port = port;
        this.ftpServer = ftpServer;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoints
        httpServer.createContext("/api/stats", new StatsHandler());
        httpServer.createContext("/api/logs", new LogsHandler());
        httpServer.createContext("/api/packets", new PacketsHandler());
        httpServer.createContext("/api/files", new FilesHandler());

        // Static Web Files
        httpServer.createContext("/", new StaticFileHandler());

        httpServer.setExecutor(null); // Dung mac dinh
        httpServer.start();
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONObject json = new JSONObject();
            json.put("running", ftpServer.isRunning());
            json.put("controlPort", ftpServer.getControlPort());
            json.put("activeClients", ftpServer.getActiveClients().size());
            json.put("totalTransferredBytes", ftpServer.getTotalTransferredBytes());
            json.put("throughputKbps", ftpServer.getCurrentThroughputKbps());
            json.put("databaseConnected", !DatabaseManager.getInstance().isMockMode());
            json.put("databaseName", DatabaseManager.getInstance().getDbName());

            sendJsonResponse(exchange, json.toString());
        }
    }

    private class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<TransferLog> logs = LogDAO.getInstance().getRecentLogs(30);
            JSONArray array = new JSONArray();
            for (TransferLog l : logs) {
                JSONObject obj = new JSONObject();
                obj.put("id", l.getId());
                obj.put("username", l.getUsername());
                obj.put("clientIp", l.getClientIp());
                obj.put("action", l.getAction());
                obj.put("filename", l.getFilename() != null ? l.getFilename() : "");
                obj.put("fileSizeBytes", l.getFileSizeBytes());
                obj.put("durationMs", l.getDurationMs());
                obj.put("speedKbps", l.getSpeedKbps());
                obj.put("status", l.getStatus());
                obj.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : "");
                array.put(obj);
            }
            sendJsonResponse(exchange, array.toString());
        }
    }

    private class PacketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<NetworkPacketInfo> packets = ftpServer.getRecentPackets();
            JSONArray array = new JSONArray();
            int limit = Math.min(50, packets.size());
            for (int i = 0; i < limit; i++) {
                NetworkPacketInfo p = packets.get(i);
                JSONObject obj = new JSONObject();
                obj.put("timestamp", p.getTimestamp());
                obj.put("layer", p.getLayer());
                obj.put("channel", p.getChannel());
                obj.put("source", p.getSourceAddress());
                obj.put("dest", p.getDestAddress());
                obj.put("protocol", p.getProtocol());
                obj.put("flags", p.getFlags());
                obj.put("size", p.getPayloadSize());
                obj.put("content", p.getContent());
                array.put(obj);
            }
            sendJsonResponse(exchange, array.toString());
        }
    }

    private class FilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File pubDir = new File("storage/public");
            JSONArray array = new JSONArray();
            if (pubDir.exists() && pubDir.isDirectory()) {
                File[] files = pubDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        FileItem item = FileItem.fromFile(f);
                        JSONObject obj = new JSONObject();
                        obj.put("name", item.getName());
                        obj.put("size", item.getSize());
                        obj.put("formattedSize", item.getFormattedSize());
                        obj.put("isDirectory", item.isDirectory());
                        obj.put("modified", item.getFormattedDate());
                        array.put(obj);
                    }
                }
            }
            sendJsonResponse(exchange, array.toString());
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Doc tu classpath resources/web hoac thu muc thuc
            InputStream is = getClass().getClassLoader().getResourceAsStream("web" + path);
            if (is == null) {
                File localFile = new File("src/main/resources/web" + path);
                if (localFile.exists()) {
                    is = new FileInputStream(localFile);
                }
            }

            if (is == null) {
                String notFound = "404 Not Found - FTP System Web Portal";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/html; charset=UTF-8";
            if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png")) contentType = "image/png";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, 0);

            try (InputStream in = is; OutputStream out = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
