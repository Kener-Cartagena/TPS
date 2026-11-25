-- V5: Account holders and customer accounts of the accounts module.
--
-- Every customer account is backed by its own ledger account (V3), which holds the
-- balance and the movements. This module adds who owns the account, its number, type and
-- status. Like the ledger, these records are never deleted.

-- ---- Account holders ---------------------------------------------------------------

CREATE TABLE account_holders (
    id               UUID         PRIMARY KEY,
    holder_type      VARCHAR(20)  NOT NULL,
    full_name        VARCHAR(150) NOT NULL,
    document_type    VARCHAR(20)  NOT NULL,
    document_number  VARCHAR(20)  NOT NULL,
    created_by       UUID         REFERENCES users (id),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,

    -- One holder per identity document. The constraint also provides the search index.
    CONSTRAINT uq_account_holders_document UNIQUE (document_number, document_type),
    CONSTRAINT ck_account_holders_holder_type CHECK (holder_type IN ('PERSON', 'BUSINESS')),
    CONSTRAINT ck_account_holders_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_account_holders_document_type CHECK (document_type IN ('DNI', 'RTN', 'PASSPORT')),
    -- Stored normalized: digits only for DNI and RTN, uppercase for passports.
    CONSTRAINT ck_account_holders_document_number CHECK (
        (document_type = 'DNI' AND document_number ~ '^[0-9]{13}$')
        OR (document_type = 'RTN' AND document_number ~ '^[0-9]{14}$')
        OR (document_type = 'PASSPORT' AND document_number ~ '^[A-Z0-9]{6,20}$')),
    -- A business is identified by its tax id (RTN).
    CONSTRAINT ck_account_holders_business_rtn CHECK (holder_type <> 'BUSINESS' OR document_type = 'RTN')
);

COMMENT ON TABLE account_holders IS 'People and businesses that own accounts';
COMMENT ON COLUMN account_holders.document_number IS
    'Honduran DNI (13 digits), RTN (14 digits) or passport number, normalized. Personal data: never logged';
COMMENT ON COLUMN account_holders.created_by IS 'User who registered the holder';

CREATE TRIGGER trg_account_holders_no_delete
    BEFORE DELETE ON account_holders
    FOR EACH ROW EXECUTE FUNCTION tps_reject_modification();
CREATE TRIGGER trg_account_holders_no_truncate
    BEFORE TRUNCATE ON account_holders
    FOR EACH STATEMENT EXECUTE FUNCTION tps_reject_modification();

-- ---- Accounts ----------------------------------------------------------------------

-- Source of account numbers: 10 digits, followed by 2 check digits (ISO 7064 MOD 97-10).
CREATE SEQUENCE account_number_seq AS BIGINT
    START WITH 1000000000 MINVALUE 1000000000 MAXVALUE 9999999999 NO CYCLE;

CREATE TABLE accounts (
    id                 UUID        PRIMARY KEY,
    account_number     VARCHAR(12) NOT NULL,
    holder_id          UUID        NOT NULL REFERENCES account_holders (id),
    type               VARCHAR(20) NOT NULL,
    currency           VARCHAR(3)  NOT NULL,
    status             VARCHAR(20) NOT NULL,
    ledger_account_id  UUID        NOT NULL,
    created_by         UUID        REFERENCES users (id),
    opened_at          TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    closed_at          TIMESTAMPTZ,
    version            BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    -- One ledger account per customer account, in the same currency.
    CONSTRAINT uq_accounts_ledger_account UNIQUE (ledger_account_id),
    CONSTRAINT fk_accounts_ledger_account FOREIGN KEY (ledger_account_id, currency)
        REFERENCES ledger_accounts (id, currency),
    -- 12 digits whose check digits are valid: the number modulo 97 is 1, as in an IBAN.
    CONSTRAINT ck_accounts_account_number
        CHECK (account_number ~ '^[0-9]{12}$' AND account_number::NUMERIC % 97 = 1),
    CONSTRAINT ck_accounts_type CHECK (type IN ('SAVINGS', 'CHECKING')),
    CONSTRAINT ck_accounts_currency CHECK (currency IN ('HNL', 'USD')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_accounts_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

COMMENT ON TABLE accounts IS 'Customer accounts; balances and movements are in the ledger account';
COMMENT ON COLUMN accounts.account_number IS '10-digit sequence + 2 ISO 7064 MOD 97-10 check digits';
COMMENT ON COLUMN accounts.ledger_account_id IS 'The LIABILITY ledger account that holds the balance';
COMMENT ON COLUMN accounts.version IS 'Optimistic locking version (JPA @Version)';

CREATE INDEX ix_accounts_holder ON accounts (holder_id);

CREATE TRIGGER trg_accounts_no_delete
    BEFORE DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION tps_reject_modification();
CREATE TRIGGER trg_accounts_no_truncate
    BEFORE TRUNCATE ON accounts
    FOR EACH STATEMENT EXECUTE FUNCTION tps_reject_modification();

-- ---- Status history ----------------------------------------------------------------

CREATE TABLE account_status_changes (
    id           UUID         PRIMARY KEY,
    account_id   UUID         NOT NULL REFERENCES accounts (id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)  NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    changed_by   UUID         REFERENCES users (id),
    changed_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_account_status_changes_from
        CHECK (from_status IS NULL OR from_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_account_status_changes_to CHECK (to_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_account_status_changes_changed CHECK (from_status IS DISTINCT FROM to_status),
    CONSTRAINT ck_account_status_changes_reason_not_blank CHECK (btrim(reason) <> '')
);

COMMENT ON TABLE account_status_changes IS
    'Every status of every account, with who changed it and why. The first row (from_status NULL) is the opening. Immutable';

CREATE INDEX ix_account_status_changes_account ON account_status_changes (account_id, changed_at DESC);

CREATE TRIGGER trg_account_status_changes_immutable
    BEFORE UPDATE OR DELETE ON account_status_changes
    FOR EACH ROW EXECUTE FUNCTION tps_reject_modification();
CREATE TRIGGER trg_account_status_changes_no_truncate
    BEFORE TRUNCATE ON account_status_changes
    FOR EACH STATEMENT EXECUTE FUNCTION tps_reject_modification();
