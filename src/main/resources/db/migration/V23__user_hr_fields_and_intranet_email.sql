ALTER TABLE sys_user
    ADD COLUMN intranet_email VARCHAR(128) NULL COMMENT '内网邮箱' AFTER email,
    ADD COLUMN job_title VARCHAR(128) NULL COMMENT '职务' AFTER dept_code,
    ADD COLUMN direct_leader_raw VARCHAR(256) NULL COMMENT '直属上级原始值' AFTER job_title,
    ADD COLUMN leader_ref VARCHAR(64) NULL COMMENT '上级工号引用' AFTER direct_leader_raw,
    ADD COLUMN account_status VARCHAR(32) NULL COMMENT '账号状态原始值' AFTER leader_ref,
    ADD COLUMN employment_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '在职状态' AFTER account_status;

CREATE UNIQUE INDEX uk_sys_user_intranet_email
    ON sys_user (intranet_email);
