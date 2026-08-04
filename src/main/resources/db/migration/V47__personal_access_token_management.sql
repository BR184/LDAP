ALTER TABLE sys_personal_access_token
    ADD COLUMN description VARCHAR(255) NULL COMMENT '密钥用途说明' AFTER name,
    ADD COLUMN scope_mode VARCHAR(32) NOT NULL DEFAULT 'FIXED' COMMENT '权限模式：FIXED或FOLLOW_ACCOUNT' AFTER description,
    ADD COLUMN secret_ciphertext VARCHAR(512) NULL COMMENT 'AES-GCM加密后的完整密钥' AFTER secret_hash,
    ADD COLUMN secret_key_id VARCHAR(64) NULL COMMENT '密钥加密主密钥ID' AFTER secret_ciphertext;

UPDATE sys_personal_access_token
SET scope_mode = 'FIXED'
WHERE scope_mode IS NULL OR scope_mode = '';
