ALTER TABLE outbox
    ADD COLUMN correlation_id VARCHAR(100) NULL;
