package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.HybridSearchResult;
import org.tobenamed.justusepostgres.repository.HybridSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Service for hybrid search: BM25 + vector similarity with RRF scoring.
 * Replaces a multi-service pipeline of Elasticsearch + Pinecone.
 */
@Service
public class HybridSearchService {

    private final HybridSearchRepository repo;

    public HybridSearchService(HybridSearchRepository repo) {
        this.repo = repo;
    }

    /**
     * Hybrid search combining keyword (BM25) and vector (cosine) search.
     * If no query vector is provided, generates a random demo vector.
     */
    public List<HybridSearchResult> hybridSearch(String query, float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
            queryVector = generateDemoVector(384);
        }
        return repo.hybridSearch(query, queryVector, limit);
    }

    /** Keyword-only search for comparison. */
    public List<HybridSearchResult> keywordOnly(String query, int limit) {
        return repo.keywordOnly(query, limit);
    }

    /** Generate a random 384-dim vector for demo purposes. */
    private float[] generateDemoVector(int dimensions) {
        Random rng = new Random();
        float[] v = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            v[i] = rng.nextFloat() - 0.5f;
        }
        return v;
    }
}
