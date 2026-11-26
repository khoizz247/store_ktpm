-- Tăng token_version khi đổi mật khẩu để thu hồi mọi JWT đã cấp trước đó.
ALTER TABLE users
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER status;
