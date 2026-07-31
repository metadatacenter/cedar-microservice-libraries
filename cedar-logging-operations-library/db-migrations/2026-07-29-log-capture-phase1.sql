-- =============================================================================
-- CEDAR log capture — Phase 1 migration
-- =============================================================================
-- Adds the columns the new logging code writes and the aggregator will read:
--   log_request : status (HTTP code), apiKeyHash (md5 of the API key), aggregatedAt (rollup marker)
--   log_cypher  : aggregatedAt (rollup marker)
--
-- Target DB : the dbLogging database (CEDAR_LOG_MYSQL_DB — e.g. cedar_log_production on prod).
-- Scope     : LIVE tables only. The frozen history tables log_request_pre284 / log_cypher_pre284
--             are INTENTIONALLY LEFT UNTOUCHED — the Phase 2 aggregator reads them with a SELECT
--             that projects the missing columns as constants (NULL AS status, ...). Never add
--             indexes to those multi-GB tables: it forces ALGORITHM=COPY (the ~13h rebuild we
--             deliberately escaped on 2026-07-28).
--
-- Column names are camelCase to match Hibernate's physical naming on these tables
-- (cf. globalRequestId, jwtTokenHash, ...). Types match the entity mapping (Hibernate 6 / MySQL 8):
--   Instant -> datetime(6), Integer -> int, String(32) -> varchar(32).
--
-- ORDERING: run this ONCE, BEFORE deploying the new build. If the new build (hbm2ddl.auto=update)
--   is deployed first it will add the three COLUMNS automatically but NOT reliably the INDEXES —
--   in that case skip section 1 and run only section 2.
--
-- SAFETY: all column adds are nullable => ALGORITHM=INSTANT (metadata-only, no rebuild, no lock).
--   Index creation is INPLACE/LOCK=NONE and is cheap NOW while log_request/log_cypher are the small
--   fresh post-rename tables — do it now, not after they grow. Run inside tmux regardless.
--
-- Requires MySQL 8.0.12+ for ALGORITHM=INSTANT on ADD COLUMN.
-- =============================================================================

-- Pre-flight (inspect, don't change) ------------------------------------------
-- SELECT VERSION();
-- SHOW CREATE TABLE log_request\G
-- SHOW CREATE TABLE log_cypher\G
-- SELECT COUNT(*) FROM log_request;   -- confirm these are the small fresh tables
-- SELECT COUNT(*) FROM log_cypher;

-- =============================================================================
-- Section 1 — add columns (INSTANT). Skip if a new build already added them.
-- =============================================================================
ALTER TABLE log_request
  ADD COLUMN status       int         NULL,
  ADD COLUMN apiKeyHash   varchar(32) NULL,
  ADD COLUMN aggregatedAt datetime(6) NULL,
  ALGORITHM=INSTANT;

ALTER TABLE log_cypher
  ADD COLUMN aggregatedAt datetime(6) NULL,
  ALGORITHM=INSTANT;

-- =============================================================================
-- Section 2 — indexes (INPLACE, online). Do this while the tables are small.
-- =============================================================================
CREATE INDEX IDX_log_request_status       ON log_request (status)       ALGORITHM=INPLACE, LOCK=NONE;
CREATE INDEX IDX_log_request_apiKeyHash    ON log_request (apiKeyHash)    ALGORITHM=INPLACE, LOCK=NONE;
CREATE INDEX IDX_log_request_aggregatedAt  ON log_request (aggregatedAt)  ALGORITHM=INPLACE, LOCK=NONE;
CREATE INDEX IDX_log_cypher_aggregatedAt   ON log_cypher  (aggregatedAt)  ALGORITHM=INPLACE, LOCK=NONE;

-- =============================================================================
-- Verify ----------------------------------------------------------------------
-- SHOW CREATE TABLE log_request\G   -- expect the 3 columns + 3 indexes
-- SHOW CREATE TABLE log_cypher\G    -- expect aggregatedAt + its index
-- =============================================================================

-- =============================================================================
-- Rollback (all additive => reversible, and DROP COLUMN is also INSTANT) -------
-- DROP INDEX IDX_log_request_status      ON log_request;
-- DROP INDEX IDX_log_request_apiKeyHash   ON log_request;
-- DROP INDEX IDX_log_request_aggregatedAt ON log_request;
-- DROP INDEX IDX_log_cypher_aggregatedAt  ON log_cypher;
-- ALTER TABLE log_request DROP COLUMN status, DROP COLUMN apiKeyHash, DROP COLUMN aggregatedAt, ALGORITHM=INSTANT;
-- ALTER TABLE log_cypher  DROP COLUMN aggregatedAt, ALGORITHM=INSTANT;
-- =============================================================================
