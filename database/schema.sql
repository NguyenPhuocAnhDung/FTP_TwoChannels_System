-- ==========================================================
-- CO SO DU LIEU HE THONG FTP SERVER/CLIENT 2 KENH (RFC 959)
-- Database Name: ftp_system
-- Tuong thich: MySQL 5.7, 8.0, 8.4+, MariaDB
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `ftp_system` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ftp_system`;

-- 1. Bang quan ly nguoi dung (Users)
CREATE TABLE IF NOT EXISTS `users` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password_hash` VARCHAR(128) NOT NULL,
    `salt` VARCHAR(32) NOT NULL,
    `home_directory` VARCHAR(255) NOT NULL DEFAULT '/',
    `role` ENUM('ADMIN', 'USER', 'GUEST') NOT NULL DEFAULT 'USER',
    `can_read` TINYINT(1) NOT NULL DEFAULT 1,
    `can_write` TINYINT(1) NOT NULL DEFAULT 1,
    `can_delete` TINYINT(1) NOT NULL DEFAULT 1,
    `max_speed_kbps` INT NOT NULL DEFAULT 0,
    `quota_bytes` BIGINT NOT NULL DEFAULT 1073741824,
    `used_bytes` BIGINT NOT NULL DEFAULT 0,
    `is_active` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bang quan ly Phong / Khong gian lam viec nhom (Rooms / Workspaces)
CREATE TABLE IF NOT EXISTS `rooms` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `room_name` VARCHAR(50) NOT NULL UNIQUE,
    `owner_username` VARCHAR(50) NOT NULL,
    `description` VARCHAR(255) NULL,
    `storage_path` VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bang quan ly Thanh vien trong Phong (Room Members & Approvals)
CREATE TABLE IF NOT EXISTS `room_members` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `room_id` INT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `role` ENUM('OWNER', 'EDITOR', 'VIEWER') NOT NULL DEFAULT 'VIEWER',
    `status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    `can_upload` TINYINT(1) NOT NULL DEFAULT 1,
    `can_delete` TINYINT(1) NOT NULL DEFAULT 0,
    `joined_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `unique_room_user` (`room_id`, `username`),
    FOREIGN KEY (`room_id`) REFERENCES `rooms`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Bang nhat ky truyen tai va hoat dong (Transfer & Activity Logs)
CREATE TABLE IF NOT EXISTS `transfer_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL,
    `client_ip` VARCHAR(45) NOT NULL,
    `action` VARCHAR(20) NOT NULL,
    `filename` VARCHAR(255) NULL,
    `file_size_bytes` BIGINT NOT NULL DEFAULT 0,
    `duration_ms` BIGINT NOT NULL DEFAULT 0,
    `speed_kbps` DOUBLE NOT NULL DEFAULT 0.0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    `details` TEXT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Bang cau hinh he thong (System Configuration)
CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key` VARCHAR(100) PRIMARY KEY,
    `config_value` VARCHAR(255) NOT NULL,
    `description` VARCHAR(255) NULL,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Du lieu khoi tao mac dinh
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('control_port', '2121', 'Cong kenh dieu khien TCP'),
('pasv_port_min', '30000', 'Cong bat dau cho kenh du lieu PASV'),
('pasv_port_max', '30050', 'Cong ket thuc cho kenh du lieu PASV'),
('max_connections', '50', 'So luong ket noi dong thoi toi da'),
('idle_timeout_seconds', '300', 'Thoi gian tu dong ngat ket noi neu khong hoat dong'),
('welcome_message', '220 Chao mung ban den voi Chuyen De FTP Server 2 Kenh (RFC 959) Java 23')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

INSERT INTO `users` (`username`, `password_hash`, `salt`, `home_directory`, `role`, `can_read`, `can_write`, `can_delete`, `max_speed_kbps`, `quota_bytes`, `is_active`) VALUES
('admin', '1f4d2b5ac875802f6c16aa6d908db48b4a664336b7bb76d5cdef50a3385675db', 'ftpsalt123', 'storage/admin', 'ADMIN', 1, 1, 1, 0, 10737418240, 1),
('user', 'b402f6ff1cfbb027bf76cd9d4bdf4c0bae4546d11d029cef5efacbf3666bc500', 'ftpsalt456', 'storage/users/user', 'USER', 1, 1, 0, 512, 1073741824, 1),
('sinhvien1', 'b402f6ff1cfbb027bf76cd9d4bdf4c0bae4546d11d029cef5efacbf3666bc500', 'ftpsalt456', 'storage/users/sinhvien1', 'USER', 1, 1, 0, 512, 1073741824, 1),
('anonymous', '', '', 'storage/public', 'GUEST', 1, 0, 0, 256, 524288000, 1)
ON DUPLICATE KEY UPDATE `password_hash` = VALUES(`password_hash`), `is_active` = 1;

-- Mau mot phong mac dinh do sinhvien1 lam Chu phong
INSERT INTO `rooms` (`room_name`, `owner_username`, `description`, `storage_path`) VALUES
('DoAn_Mang_Nhom1', 'sinhvien1', 'Phong chia se tai lieu va source code Do An Mang May Tinh', 'storage/rooms/DoAn_Mang_Nhom1')
ON DUPLICATE KEY UPDATE `owner_username` = VALUES(`owner_username`);

INSERT INTO `room_members` (`room_id`, `username`, `role`, `status`, `can_upload`, `can_delete`)
SELECT `id`, 'sinhvien1', 'OWNER', 'APPROVED', 1, 1 FROM `rooms` WHERE `room_name` = 'DoAn_Mang_Nhom1'
ON DUPLICATE KEY UPDATE `role` = 'OWNER';
