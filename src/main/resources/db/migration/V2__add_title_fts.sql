-- Stored generated column. Postgres computes it on insert/update from
-- the title. The coalesce guards against NULL, though title is NOT NULL
-- today — belt and braces against a future schema change.
ALTER TABLE contract_opportunities
    ADD COLUMN title_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, ''))) STORED;

-- GIN is the right index for tsvector. fastupdate is on by default and
-- batches pending updates, which is what you want for a mostly-read table.
CREATE INDEX idx_opportunities_title_tsv
    ON contract_opportunities USING GIN (title_tsv);

ANALYZE contract_opportunities;