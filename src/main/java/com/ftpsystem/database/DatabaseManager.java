package com.ftpsystem.database;

import com.ftpsystem.common.ChecksumUtil;
import com.ftpsystem.common.User;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Quan ly Ket noi MySQL Database
 * Tich hop che do Tu dong Fallback (Mock Mode) khi chua khoi dong MySQL
 * de ung dung khong bao gio bi crash khi mang di demo.
 */
public class DatabaseManager {
    private static DatabaseManager instance;

    private String dbHost = "127.0.0.1";
    private int dbPort = 3306;
    private String dbName = "ftp_system";
    private String dbUser = "root";
    private String dbPassword = "";

    private boolean mockMode = false;
    private String lastError = "";

    private DatabaseManager() {
        loadConfig();
        checkAndInitConnection();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                this.dbHost = props.getProperty("db.host", "127.0.0.1");
                this.dbPort = Integer.parseInt(props.getProperty("db.port", "3306"));
                this.dbName = props.getProperty("db.name", "ftp_system");
                this.dbUser = props.getProperty("db.user", "root");
                this.dbPassword = props.getProperty("db.password", "");
            }
        } catch (Exception e) {
            System.err.println("[DB] Khong doc duoc config.properties, dung cau hinh mac dinh.");
        }
    }

    public synchronized boolean checkAndInitConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String testUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    dbHost, dbPort);
            try (Connection conn = DriverManager.getConnection(testUrl, dbUser, dbPassword);
                 Statement stmt = conn.createStatement()) {
                // Tao database neu chua co
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            }

            // Ket noi vao database chinh thuc va chay schema
            String fullUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8",
                    dbHost, dbPort, dbName);
            try (Connection conn = DriverManager.getConnection(fullUrl, dbUser, dbPassword)) {
                runSchemaInit(conn);
            }

            this.mockMode = false;
            this.lastError = "";
            System.out.println("[DB] Ket noi thanh cong den MySQL Database: " + dbName);
            return true;
        } catch (Exception e) {
            this.mockMode = true;
            this.lastError = e.getMessage();
            System.err.println("[DB WARNING] Chua khoi dong MySQL (Port 3306). Chuyen sang Che do Du phong (In-Memory Mock Mode): " + e.getMessage());
            return false;
        }
    }

    private void runSchemaInit(Connection conn) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) {
                // Thu doc truc tiep tu thu muc database/schema.sql
                java.io.File schemaFile = new java.io.File("database/schema.sql");
                if (schemaFile.exists()) {
                    runScriptFromFile(conn, schemaFile);
                }
                return;
            }
            runScriptFromStream(conn, is);
        } catch (Exception e) {
            System.err.println("[DB] Loi khoi tao schema: " + e.getMessage());
        }
    }

    private void runScriptFromFile(Connection conn, java.io.File file) {
        try (BufferedReader br = new BufferedReader(new java.io.FileReader(file));
             Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.startsWith("/*") || line.isEmpty()) continue;
                sb.append(line).append(" ");
                if (line.endsWith(";")) {
                    try {
                        stmt.execute(sb.toString());
                    } catch (Exception ignored) {}
                    sb.setLength(0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void runScriptFromStream(Connection conn, InputStream is) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is));
             Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.startsWith("/*") || line.isEmpty()) continue;
                sb.append(line).append(" ");
                if (line.endsWith(";")) {
                    try {
                        stmt.execute(sb.toString());
                    } catch (Exception ignored) {}
                    sb.setLength(0);
                }
            }
        } catch (Exception ignored) {}
    }

    public Connection getConnection() throws SQLException {
        if (mockMode) {
            throw new SQLException("Database is in Mock Mode");
        }
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8",
                dbHost, dbPort, dbName);
        return DriverManager.getConnection(url, dbUser, dbPassword);
    }

    public boolean isMockMode() { return mockMode; }
    public String getLastError() { return lastError; }

    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }
    public int getDbPort() { return dbPort; }
    public void setDbPort(int dbPort) { this.dbPort = dbPort; }
    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }
    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }
}
