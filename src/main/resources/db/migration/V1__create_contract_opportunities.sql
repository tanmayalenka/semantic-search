CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE contract_opportunities (
                                        notice_id       VARCHAR(250) PRIMARY KEY,
                                        title           TEXT NOT NULL,
                                        solicitation    VARCHAR(250),
                                        department      VARCHAR(250),
                                        sub_tier        VARCHAR(250),
                                        office          VARCHAR(250),
                                        type            VARCHAR(250),
                                        description     TEXT,
                                        title_embedding VECTOR(768),
                                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW index for cosine similarity on the embedding column.
-- m=16, ef_construction=64 are pgvector defaults — good recall/speed tradeoff
-- for a few hundred thousand rows. Cosine ops because we L2-normalize.
CREATE INDEX idx_opportunities_title_embedding_hnsw
    ON contract_opportunities
    USING hnsw (title_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Cheap lookup for the backfill job ("rows still missing an embedding").
CREATE INDEX idx_opportunities_embedding_missing
    ON contract_opportunities (updated_at)
    WHERE title_embedding IS NULL;

-- Optional but useful if you later filter by agency before ranking.
CREATE INDEX idx_opportunities_department ON contract_opportunities (department);