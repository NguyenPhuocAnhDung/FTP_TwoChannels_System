# FTP Server/Client Hai Kênh (RFC 959)

Đồ án cuối kỳ – Nhóm 03. Ứng dụng mô phỏng giao thức truyền tệp FTP với **hai kết nối TCP độc lập**:

- **Kênh Điều Khiển** (cổng 2121): lệnh `USER`, `PASS`, `LIST`, `RETR`, `STOR`, `REST`, ... và mã phản hồi.
- **Kênh Dữ Liệu** (cổng động 30000–31000): chỉ mở khi truyền file/danh sách (PASV hoặc PORT), truyền xong tự đóng.

Ngôn ngữ: Java 21+ (phát triển trên JDK 23) · IDE: Apache NetBeans 22 · CSDL: MySQL 8.

## Chức năng chính

- Tập lệnh RFC 959: USER, PASS, PWD, CWD, CDUP, MKD, RMD, DELE, LIST, RETR, STOR, REST, SIZE, TYPE, PASV, PORT, NOOP, FEAT, QUIT.
- Truyền tiếp file dở dang (REST, `RandomAccessFile`), kiểm tra toàn vẹn SHA-256.
- Giới hạn băng thông theo phiên, định ngạch dung lượng theo tài khoản.
- Sandbox chống Path Traversal, mật khẩu SHA-256 + Salt, `PORT` chỉ nhận IP của chính Client.
- Phòng cộng tác: Chủ phòng duyệt thành viên với vai trò Editor / Viewer.
- Client Swing hai khung (máy cục bộ / máy chủ), Server Dashboard, Web Portal cổng 8080.
- Trình theo dõi gói tin: cờ TCP được **suy diễn từ vòng đời Socket** (Java không lộ cờ TCP thật cho ứng dụng).

## Cách chạy

1. Cài JDK 21+ và Maven (NetBeans 22 đã kèm Maven).
2. *(Tuỳ chọn, để dùng MySQL)* sao chép `src/main/resources/config.properties.example` thành `config.properties` rồi điền mật khẩu MySQL. Nếu bỏ qua, hệ thống tự chạy ở chế độ In-Memory.
3. Mở thư mục trong Apache NetBeans (`File > Open Project`), hoặc dùng các file `.bat`:
   - `build.bat` – biên dịch
   - `run-server.bat` – chạy Server Dashboard
   - `run-client.bat` – chạy Client
4. Kiểm thử: `mvn clean test`

Tài khoản mẫu: `admin / admin123`, `user / 123456`, `sinhvien1 / 123456`.

## Cấu trúc

```
src/main/java/com/ftpsystem/
  common/    hằng số, mã phản hồi RFC 959, model, ChecksumUtil
  database/  DatabaseManager (MySQL + fallback), UserDAO, RoomDAO, LogDAO
  server/    FtpServer, ClientControlHandler, DataChannelHandler, PathSandbox, EmbeddedWebServer, gui/
  client/    FtpClientCore, gui/
src/main/resources/web/   Web Portal (HTML/CSS/JS)
database/schema.sql       Lược đồ MySQL
```
