package com.example.contracts.repository;

import com.example.contracts.dto.OpportunityHit;
import com.example.contracts.dto.SearchResultItem;
import com.example.contracts.entity.ContractOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractOpportunityRepository extends JpaRepository<ContractOpportunity, String> {

    /**
     * Ranker 1 — semantic. Cosine similarity over the title embedding.
     * score is in [0, 1] for text embeddings; higher is better.
     */
    @Query(value = """
        SELECT o.notice_id      AS noticeId,
               o.title          AS title,
               o.solicitation   AS solicitation,
               o.department     AS department,
               o.sub_tier       AS subTier,
               o.office         AS office,
               o.type           AS type,
               1 - (o.title_embedding <=> CAST(:queryEmbedding AS vector)) AS score
        FROM contract_opportunities o
        WHERE o.title_embedding IS NOT NULL
        ORDER BY o.title_embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """,
            nativeQuery = true)
    List<OpportunityHit> searchByVector(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    /**
     * Ranker 2 — keyword. Postgres full-text search over the generated
     * tsvector column. score is ts_rank, which is unbounded but in
     * practice falls in ~[0.05, 0.5] for title-length text.
     *
     * websearch_to_tsquery is deliberately used over plainto_tsquery:
     * it accepts the syntax users actually type ("cloud -aws", quoted
     * phrases, OR) without throwing on malformed input.
     */
    @Query(value = """
        SELECT o.notice_id      AS noticeId,
               o.title          AS title,
               o.solicitation   AS solicitation,
               o.department     AS department,
               o.sub_tier       AS subTier,
               o.office         AS office,
               o.type           AS type,
               ts_rank(o.title_tsv,
                       websearch_to_tsquery('english', :query)) AS score
        FROM contract_opportunities o
        WHERE o.title_tsv @@ websearch_to_tsquery('english', :query)
        ORDER BY score DESC
        LIMIT :limit
        """,
            nativeQuery = true)
    List<OpportunityHit> searchByKeyword(
            @Param("query") String query,
            @Param("limit") int limit);

    /**
     * Semantic search over titles. Returns a projection directly into
     * SearchResultItem via Spring Data's constructor-expression support.
     *
     * Why the constructor expression: it avoids Object[] -> DTO mapping
     * boilerplate and, more importantly, lets the compiler catch column
     * mismatches at repository-query time rather than at runtime.
     *
     * minScore is applied in SQL so the DB never sends us rows we'll
     * throw away. Cosine similarity = 1 - distance, so filtering on
     * (1 - distance) >= minScore is equivalent to distance <= (1 - minScore).
     */
    @Query(value = """
        SELECT o.notice_id      AS noticeId,
               o.title          AS title,
               o.solicitation   AS solicitation,
               o.department     AS department,
               o.sub_tier       AS subTier,
               o.office         AS office,
               o.type           AS type,
               1 - (o.title_embedding <=> CAST(:queryEmbedding AS vector)) AS score
        FROM contract_opportunities o
        WHERE o.title_embedding IS NOT NULL
          AND 1 - (o.title_embedding <=> CAST(:queryEmbedding AS vector)) >= :minScore
        ORDER BY o.title_embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """,
            nativeQuery = true)
    List<SearchResultItem> searchByTitleEmbedding(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("minScore") double minScore,
            @Param("limit") int limit);

    /**
     * Rows that still need an embedding computed (or whose title changed).
     * The partial index on (updated_at) WHERE title_embedding IS NULL
     * makes this a fast scan.
     */
    @Query(value = """
            SELECT * FROM contract_opportunities
            WHERE title_embedding IS NULL
            ORDER BY updated_at
            LIMIT :batchSize
            """, nativeQuery = true)
    List<ContractOpportunity> findMissingEmbeddings(@Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            UPDATE contract_opportunities
            SET title_embedding = CAST(:embedding AS vector),
                updated_at = now()
            WHERE notice_id = :noticeId
            """, nativeQuery = true)
    int updateTitleEmbedding(@Param("noticeId") String noticeId,
                             @Param("embedding") String embedding);

    @Modifying
    @Query(value = """
            UPDATE contract_opportunities
            SET title_embedding = CAST(:embedding AS vector),
                updated_at = now()
            WHERE notice_id IN (:noticeIds)
            """, nativeQuery = true)
    int updateTitleEmbeddings(@Param("noticeIds") List<String> noticeIds,
                              @Param("embedding") List<String> embeddings);

    /**
     * Fetch (notice_id, title) pairs for rows missing an embedding.
     * Returns Object[] rows because we only need two columns and don't
     * want to materialize full entities (2M rows × full entity = waste).
     */
    @Query(value = """
        SELECT notice_id, title
        FROM contract_opportunities
        WHERE title_embedding IS NULL
        ORDER BY updated_at
        LIMIT :batchSize
        """, nativeQuery = true)
    List<Object[]> findMissingEmbeddingRows(@Param("batchSize") int batchSize);
}
