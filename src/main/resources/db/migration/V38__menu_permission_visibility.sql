CREATE TABLE sys_menu_permission (
    menu_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    creator VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (menu_id, permission_id),
    KEY idx_sys_menu_permission_permission (permission_id)
);
