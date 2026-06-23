package com.br.honeycombframework.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Utilitário: limpa coleções do artefato para reexecução do experimento do zero.
 * Rode: mvnw test -Dtest=MongoCollectionCleanerTest
 */
@SpringBootTest
class MongoCollectionCleanerTest {

    private static final List<String> COLLECTIONS = List.of(
            "manifests",
            "execution_logs");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void clearAllArtifactCollections() {
        for (String collection : COLLECTIONS) {
            long deleted = mongoTemplate.getCollection(collection).deleteMany(new org.bson.Document()).getDeletedCount();
            System.out.println(collection + ": " + deleted + " documento(s) removido(s)");
        }
    }
}
