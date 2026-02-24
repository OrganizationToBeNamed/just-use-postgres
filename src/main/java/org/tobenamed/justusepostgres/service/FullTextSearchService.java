package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.SearchResult;
import org.tobenamed.justusepostgres.repository.FullTextSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FullTextSearchService {

    private final FullTextSearchRepository repo;

    public FullTextSearchService(FullTextSearchRepository repo) {
        this.repo = repo;
    }

    /** BM25-ranked search (like Elasticsearch). */
    public List<SearchResult> search(String query, int limit) {
        return repo.search(query, limit);
    }

    /** Fuzzy/typo-tolerant search (like Elasticsearch's fuzziness parameter). */
    public List<SearchResult> fuzzySearch(String query, double threshold, int limit) {
        return repo.fuzzySearch(query, threshold, limit);
    }

    public Long addArticle(String title, String body) {
        return repo.insert(title, body);
    }
}
