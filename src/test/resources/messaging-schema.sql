-- README 3장 DDL과 같다. 서비스는 이 내용을 자기 Flyway 파일로 넣는다. shedlock은 테스트용
DROP TABLE IF EXISTS outbox;
DROP TABLE IF EXISTS processed_message;
DROP TABLE IF EXISTS shedlock;

CREATE TABLE outbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id   VARCHAR(36)  NOT NULL,
    message_type VARCHAR(30)  NOT NULL,
    queue_name   VARCHAR(200) NOT NULL,
    group_id     VARCHAR(64)  NULL,
    payload      TEXT         NOT NULL,
    trace_parent VARCHAR(55)  NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    published_at DATETIME(6)  NULL,
    UNIQUE KEY uk_outbox_message_id (message_id),
    KEY idx_outbox_unpublished (published, id)
);

CREATE TABLE processed_message (
    message_id   VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL
);

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL
);
