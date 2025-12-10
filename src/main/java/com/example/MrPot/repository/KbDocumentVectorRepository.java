package com.example.MrPot.repository;


import com.example.MrPot.model.KbDocument;
import com.example.MrPot.model.ScoredDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class KbDocumentVectorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 使用 pgvector 的 cosine distance 运算符 `<=>`，
     * 同时计算相似度 score = 1 - distance。
     */
    public List<ScoredDocument> findNearest(float[] embedding, int limit) {
        PGvector queryVector = new PGvector(embedding);

        String sql = """
                SELECT id,
                       doc_type,
                       content,
                       metadata,
                       1 - (embedding <=> ?) AS score
                FROM kb_documents
                ORDER BY embedding <=> ?
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, ps -> {
            ps.setObject(1, queryVector); // 用于 1 - (embedding <=> ?)
            ps.setObject(2, queryVector); // 用于 ORDER BY embedding <=> ?
            ps.setInt(3, limit);
        }, new ScoredDocumentRowMapper());
    }

    private class ScoredDocumentRowMapper implements RowMapper<ScoredDocument> {
        @Override
        public ScoredDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            KbDocument doc = new KbDocument();
            doc.setId(rs.getLong("id"));
            doc.setDocType(rs.getString("doc_type"));
            doc.setContent(rs.getString("content"));

            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(metadataJson);
                    doc.setMetadata(node);
                } catch (Exception e) {
                    // 解析失败就置空，不影响主流程
                    doc.setMetadata(null);
                }
            }

            double score = rs.getDouble("score");
            return new ScoredDocument(doc, score);
        }
    }
}
