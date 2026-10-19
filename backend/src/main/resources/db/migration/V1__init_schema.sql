-- JobTrack: esquema inicial

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         varchar(254) NOT NULL,
    display_name  varchar(100) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

CREATE TABLE profiles (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    degree              varchar(200),
    experience_summary  text,
    years_experience    numeric(4, 1),
    location            varchar(200),
    preferred_work_mode varchar(16),
    expected_salary     numeric(12, 2),
    salary_currency     varchar(3),
    preferences         text,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_profiles_work_mode CHECK (preferred_work_mode IN ('REMOTE', 'HYBRID', 'ON_SITE')),
    CONSTRAINT ck_profiles_years CHECK (years_experience >= 0),
    CONSTRAINT ck_profiles_salary CHECK (expected_salary >= 0)
);

CREATE TABLE profile_skills (
    user_id uuid         NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    name    varchar(80)  NOT NULL,
    PRIMARY KEY (user_id, name)
);

CREATE TABLE profile_languages (
    user_id  uuid         NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    language varchar(60)  NOT NULL,
    level    varchar(40)  NOT NULL,
    PRIMARY KEY (user_id, language)
);

CREATE TABLE profile_desired_roles (
    user_id uuid          NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    role    varchar(120)  NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE job_offers (
    id                   uuid PRIMARY KEY,
    user_id              uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                varchar(200)  NOT NULL,
    company              varchar(200)  NOT NULL,
    description          text,
    url                  varchar(2000),
    location             varchar(200),
    work_mode            varchar(16),
    published_date       date,
    detected_date        date          NOT NULL DEFAULT current_date,
    applied_date         date,
    salary_min           numeric(12, 2),
    salary_max           numeric(12, 2),
    currency             varchar(3),
    contract_type        varchar(80),
    working_hours        varchar(80),
    min_years_experience numeric(4, 1),
    requirements         text,
    notes                text,
    status               varchar(24)   NOT NULL DEFAULT 'SAVED',
    favorite             boolean       NOT NULL DEFAULT false,
    compatibility_score  smallint,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_offers_work_mode CHECK (work_mode IN ('REMOTE', 'HYBRID', 'ON_SITE')),
    CONSTRAINT ck_offers_status CHECK (status IN ('SAVED', 'PENDING_TO_APPLY', 'APPLIED', 'IN_PROCESS',
                                                  'INTERVIEW', 'OFFER_RECEIVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_offers_salary CHECK (salary_min >= 0 AND salary_max >= 0
                                       AND (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)),
    CONSTRAINT ck_offers_years CHECK (min_years_experience >= 0),
    CONSTRAINT ck_offers_score CHECK (compatibility_score BETWEEN 0 AND 100)
);

CREATE INDEX ix_offers_user_status   ON job_offers (user_id, status);
CREATE INDEX ix_offers_user_favorite ON job_offers (user_id, favorite);
CREATE INDEX ix_offers_user_detected ON job_offers (user_id, detected_date DESC);
CREATE INDEX ix_offers_user_company  ON job_offers (user_id, lower(company));
CREATE INDEX ix_offers_user_score    ON job_offers (user_id, compatibility_score);
CREATE INDEX ix_offers_user_applied  ON job_offers (user_id, applied_date);

CREATE TABLE job_offer_skills (
    offer_id uuid         NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    name     varchar(80)  NOT NULL,
    PRIMARY KEY (offer_id, name)
);

CREATE TABLE status_history (
    id          uuid PRIMARY KEY,
    offer_id    uuid         NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_status varchar(24),
    to_status   varchar(24)  NOT NULL,
    changed_at  timestamptz  NOT NULL DEFAULT now(),
    note        varchar(1000)
);

CREATE INDEX ix_history_offer ON status_history (offer_id, changed_at DESC);
CREATE INDEX ix_history_user  ON status_history (user_id, changed_at DESC);
