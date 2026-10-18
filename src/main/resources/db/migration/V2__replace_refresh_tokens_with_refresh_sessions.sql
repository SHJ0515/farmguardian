-- Separate the login-session aggregate from issued Refresh Token generations.
-- Legacy Refresh Token rows contain raw credentials and are intentionally not migrated.
-- Create every replacement object before dropping the legacy credential table.

CREATE TABLE refresh_sessions (
    refresh_session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    client_uuid VARCHAR(255) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    reuse_detected_at DATETIME(6) NULL,
    active_slot TINYINT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_sessions PRIMARY KEY (refresh_session_id),
    CONSTRAINT uk_refresh_sessions_family_id UNIQUE (family_id),
    CONSTRAINT uk_refresh_sessions_user_client_active
        UNIQUE (user_id, client_uuid, active_slot),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_refresh_sessions_client_uuid
        CHECK (CHAR_LENGTH(TRIM(client_uuid)) > 0),
    CONSTRAINT ck_refresh_sessions_family_id_length
        CHECK (CHAR_LENGTH(family_id) = 36),
    CONSTRAINT ck_refresh_sessions_active_slot
        CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT ck_refresh_sessions_state CHECK (
        (
            active_slot IS NOT NULL
            AND revoked_at IS NULL
            AND reuse_detected_at IS NULL
        )
        OR
        (
            active_slot IS NULL
            AND revoked_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_refresh_sessions_revoked_time
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_refresh_sessions_reuse_time
        CHECK (reuse_detected_at IS NULL OR reuse_detected_at >= revoked_at),
    CONSTRAINT ck_refresh_sessions_version CHECK (version >= 0)
);

CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_sessions (user_id, active_slot);

CREATE INDEX idx_refresh_sessions_revoked_at
    ON refresh_sessions (revoked_at);

CREATE TABLE refresh_token_history (
    refresh_token_history_id BIGINT NOT NULL AUTO_INCREMENT,
    refresh_session_id BIGINT NOT NULL,
    jti VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    rotated_at DATETIME(6) NULL,
    replaced_by_jti VARCHAR(36) NULL,
    reuse_detected_at DATETIME(6) NULL,
    current_slot TINYINT NULL DEFAULT 1,
    CONSTRAINT pk_refresh_token_history PRIMARY KEY (refresh_token_history_id),
    CONSTRAINT uk_refresh_token_history_jti UNIQUE (jti),
    CONSTRAINT uk_refresh_token_history_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_token_history_replaced_jti UNIQUE (replaced_by_jti),
    CONSTRAINT uk_refresh_token_history_session_current
        UNIQUE (refresh_session_id, current_slot),
    CONSTRAINT fk_refresh_token_history_session
        FOREIGN KEY (refresh_session_id)
        REFERENCES refresh_sessions (refresh_session_id) ON DELETE CASCADE,
    CONSTRAINT ck_refresh_token_history_jti_length
        CHECK (CHAR_LENGTH(jti) = 36),
    CONSTRAINT ck_refresh_token_history_hash_length
        CHECK (CHAR_LENGTH(token_hash) = 64),
    CONSTRAINT ck_refresh_token_history_replacement_length
        CHECK (replaced_by_jti IS NULL OR CHAR_LENGTH(replaced_by_jti) = 36),
    CONSTRAINT ck_refresh_token_history_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_token_history_current_slot
        CHECK (current_slot IS NULL OR current_slot = 1),
    CONSTRAINT ck_refresh_token_history_state CHECK (
        (
            current_slot IS NOT NULL
            AND rotated_at IS NULL
            AND replaced_by_jti IS NULL
            AND reuse_detected_at IS NULL
        )
        OR
        (
            current_slot IS NULL
            AND rotated_at IS NOT NULL
            AND replaced_by_jti IS NOT NULL
        )
    ),
    CONSTRAINT ck_refresh_token_history_rotation_time
        CHECK (rotated_at IS NULL OR rotated_at >= issued_at),
    CONSTRAINT ck_refresh_token_history_reuse_time CHECK (
        reuse_detected_at IS NULL
        OR (
            rotated_at IS NOT NULL
            AND reuse_detected_at >= rotated_at
        )
    ),
    CONSTRAINT ck_refresh_token_history_replacement
        CHECK (replaced_by_jti IS NULL OR replaced_by_jti <> jti)
);

CREATE INDEX idx_refresh_token_history_session_issued
    ON refresh_token_history (refresh_session_id, issued_at);

CREATE INDEX idx_refresh_token_history_expires_at
    ON refresh_token_history (expires_at);

CREATE INDEX idx_refresh_token_history_reuse_detected_at
    ON refresh_token_history (reuse_detected_at);

DROP TABLE refresh_tokens;
