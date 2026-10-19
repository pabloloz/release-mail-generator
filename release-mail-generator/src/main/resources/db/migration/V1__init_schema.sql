-- Esquema inicial para PostgreSQL (producción).
-- Replica exactamente las entidades JPA existentes (release_mail_generator.model),
-- para que `spring.jpa.hibernate.ddl-auto=validate` no encuentre diferencias.
-- Compatible con Spring Boot 4.0.6 / Flyway (classpath:db/migration).

CREATE TABLE rfc_technical_records (
    id                     VARCHAR(255) PRIMARY KEY,
    rfc_number             VARCHAR(255),
    change_name            VARCHAR(255),
    validation_date        VARCHAR(255),
    tester_name            VARCHAR(255),
    requester              VARCHAR(255),
    environment            VARCHAR(255),
    change_context         TEXT,
    main_objective         TEXT,
    specific_objectives    TEXT,
    modules                TEXT,
    stored_procedures      TEXT,
    jobs                   TEXT,
    tables                 TEXT,
    reports                TEXT,
    other_components       TEXT,
    business_rules         TEXT,
    test_cases             TEXT,
    related_bugs           TEXT,
    final_result           VARCHAR(255),
    observations           TEXT,
    risks                  TEXT,
    recommendations        TEXT,
    final_notes            TEXT,
    status                 VARCHAR(255),
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP
);

CREATE TABLE document_versions (
    id               VARCHAR(255) PRIMARY KEY,
    document_type    VARCHAR(20)  NOT NULL,
    document_ref     VARCHAR(500),
    title            VARCHAR(500),
    author           VARCHAR(200),
    content          TEXT,
    content_hash     VARCHAR(64),
    action           VARCHAR(20)  NOT NULL,
    format           VARCHAR(20),
    version_number   INTEGER      NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    content_size     BIGINT       NOT NULL
);

CREATE INDEX idx_doc_type    ON document_versions (document_type);
CREATE INDEX idx_doc_ref     ON document_versions (document_ref);
CREATE INDEX idx_created_at  ON document_versions (created_at);

-- Índice auxiliar para búsquedas por número de RFC (usado en validaciones de duplicados).
CREATE INDEX idx_rfc_number ON rfc_technical_records (rfc_number);
