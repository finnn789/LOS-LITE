-- V1: skema awal Mini-LOS.
-- Urutan CREATE TABLE mengikuti dependency Foreign Key: tabel tanpa FK dibuat duluan.
-- Tipe/precision/length di sini WAJIB sama persis dengan anotasi @Column di masing-masing
-- entity Java (domain/model/) karena spring.jpa.hibernate.ddl-auto=validate akan menolak
-- start aplikasi kalau ada mismatch.

-- =========================================================
-- 1. app_user — staff internal (OFFICER/ADMIN), login via JWT
-- =========================================================
CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_app_user_username UNIQUE (username)
) ENGINE = InnoDB;

-- =========================================================
-- 2. applicant — master data nasabah (1 NIK bisa punya banyak loan_application)
-- =========================================================
CREATE TABLE applicant (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(150) NOT NULL,
    nik            VARCHAR(255) NOT NULL,
    nik_hash       VARCHAR(64)  NOT NULL,
    date_of_birth  DATE         NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    email          VARCHAR(150) NULL,
    address        TEXT         NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uk_applicant_nik_hash UNIQUE (nik_hash)
) ENGINE = InnoDB;

-- =========================================================
-- 3. loan_application — pengajuan pinjaman (histori per applicant)
-- =========================================================
CREATE TABLE loan_application (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    applicant_id             BIGINT         NOT NULL,
    loan_amount_requested    DECIMAL(15, 2) NOT NULL,
    loan_tenor_months        INT            NOT NULL,
    purpose                  VARCHAR(255)   NULL,
    monthly_income           DECIMAL(15, 2) NOT NULL,
    monthly_debt_obligation  DECIMAL(15, 2) NOT NULL,
    status                   VARCHAR(30)    NOT NULL,
    submitted_at             DATETIME(6)    NULL,
    decided_at               DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    CONSTRAINT fk_loan_application_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicant (id)
) ENGINE = InnoDB;

-- =========================================================
-- 4. document — dokumen ter-upload (KTP, slip gaji) untuk satu loan_application
-- =========================================================
CREATE TABLE document (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_application_id  BIGINT       NOT NULL,
    document_type        VARCHAR(30)  NOT NULL,
    file_path            VARCHAR(500) NOT NULL,
    ocr_status           VARCHAR(20)  NOT NULL,
    ocr_raw_result       JSON         NULL,
    uploaded_at          DATETIME(6)  NOT NULL,
    CONSTRAINT fk_document_loan_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_application (id)
) ENGINE = InnoDB;

-- =========================================================
-- 5. scoring_result — histori hasil scoring (DTI + rule engine), versioned
-- =========================================================
CREATE TABLE scoring_result (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_application_id  BIGINT        NOT NULL,
    dti_ratio            DECIMAL(5, 4) NOT NULL,
    score_bucket         VARCHAR(20)   NOT NULL,
    decision             VARCHAR(20)   NOT NULL,
    rule_trace           JSON          NULL,
    calculated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_scoring_result_loan_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_application (id)
) ENGINE = InnoDB;

-- =========================================================
-- 6. audit_log — audit trail generik/polymorphic (entity_type + entity_id)
-- =========================================================
CREATE TABLE audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     BIGINT      NOT NULL,
    action        VARCHAR(50) NOT NULL,
    old_value     JSON        NULL,
    new_value     JSON        NULL,
    performed_by  BIGINT      NULL,
    performed_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_log_app_user
        FOREIGN KEY (performed_by) REFERENCES app_user (id)
) ENGINE = InnoDB;
