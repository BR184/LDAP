ALTER TABLE sys_personal_access_token
    ADD COLUMN secret_value VARCHAR(128) NULL COMMENT '完整访问密钥明文' AFTER secret_hash,
    DROP COLUMN secret_ciphertext,
    DROP COLUMN secret_key_id;
