package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.Document;
import org.tobenamed.justusepostgres.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository repo;

    public DocumentService(DocumentRepository repo) {
        this.repo = repo;
    }

    public List<Document> findAll(String collection) {
        return repo.findByCollection(collection);
    }

    public List<Document> findByFilter(String collection, String jsonFilter) {
        return repo.findByFilter(collection, jsonFilter);
    }

    public Long insert(String collection, String jsonData) {
        return repo.insert(collection, jsonData);
    }

    public boolean update(Long id, String jsonPatch) {
        return repo.update(id, jsonPatch);
    }

    public boolean delete(Long id) {
        return repo.delete(id);
    }
}
