-- V4: Internal (system) accounts of the general ledger.
--
-- Settlement accounts represent the money TPS holds at its financial institutions, one
-- per currency. Deposits and withdrawals (Phase 4) move money between a settlement
-- account and a customer account:
--
--   deposit of 100.00 HNL:   DEBIT  SETTLEMENT-HNL 100.00   (asset grows)
--                            CREDIT CUS-<number>   100.00   (what TPS owes the customer grows)
--
-- The ids are fixed so the application can refer to these accounts directly; they must
-- match io.github.kenercartagena.tps.ledger.SystemLedgerAccounts.

INSERT INTO ledger_accounts (id, code, name, type, currency, balance, allow_negative, status,
                             created_at, updated_at)
VALUES ('00000000-0000-7000-8000-000000000101', 'SETTLEMENT-HNL', 'Settlement account (HNL)',
        'ASSET', 'HNL', 0, FALSE, 'OPEN', now(), now()),
       ('00000000-0000-7000-8000-000000000102', 'SETTLEMENT-USD', 'Settlement account (USD)',
        'ASSET', 'USD', 0, FALSE, 'OPEN', now(), now());
