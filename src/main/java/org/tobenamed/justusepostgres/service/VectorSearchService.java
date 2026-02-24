package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.VectorDocument;
import org.tobenamed.justusepostgres.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Service for vector similarity search.
 *
 * In production, you would:
 * <ol>
 *   <li>Call an embedding API (OpenAI, Cohere, Hugging Face) to get vectors</li>
 *   <li>Store the vectors via {@code insert()}</li>
 *   <li>Search using the query vector via {@code search()}</li>
 * </ol>
 *
 * The pgai extension can auto-sync embeddings as data changes.
 */
@Service
public class VectorSearchService {

    private final VectorSearchRepository repo;

    public VectorSearchService(VectorSearchRepository repo) {
        this.repo = repo;
    }

    public List<VectorDocument> search(float[] queryVector, int limit) {
        return repo.findSimilar(queryVector, limit);
    }

    public Long addDocument(String title, String content) {
        // In production: call an embedding model here (OpenAI, Hugging Face, etc.)
        float[] fakeEmbedding = generateRandomEmbedding(384);
        return repo.insert(title, content, fakeEmbedding);
    }

    public Long addDocumentWithEmbedding(String title, String content, float[] embedding) {
        return repo.insert(title, content, embedding);
    }

    /** Generate a random vector for demo purposes. */
    public float[] generateRandomEmbedding(int dimensions) {
        Random random = new Random();
        float[] embedding = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = random.nextFloat() - 0.5f;
        }
        return embedding;
    }
}
