-- Additive replay ledger for the application-log consumer. MySQL 8 / InnoDB.
-- Stop old consumers before switching: old workers do not participate in these fences.
-- Retain this table across raw-log pruning. Deleting receipts permits old messages to execute again.
-- No existing raw rows or rollups are rewritten by this migration.
CREATE TABLE IF NOT EXISTS log_event_receipt (
  eventKey VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  processed BOOLEAN NOT NULL DEFAULT FALSE,
  requestPhases INT NOT NULL DEFAULT 0,
  PRIMARY KEY (eventKey)
) ENGINE=InnoDB;
