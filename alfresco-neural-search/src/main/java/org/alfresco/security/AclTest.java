package org.alfresco.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.alfresco.opensearch.model.acl.AclEntry;
import org.alfresco.opensearch.search.Indexer;
import org.alfresco.opensearch.search.Search;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AclTest {
    @Autowired
    private Search search;

    @Autowired
    private Indexer indexer;

    @Test
    public void testAclFiltering() throws Exception {
        // Create test document with ACL
        List<AclEntry> acl = new ArrayList<>();
        acl.add(new AclEntry("admin", "Read"));
        acl.add(new AclEntry("GROUP_EVERYONE", "Read"));

        List<String> readers = Arrays.asList("admin", "GROUP_EVERYONE");

        // Index test document
        indexer.index("test_doc", 1L, "test_content", "Test Document", 
                     "This is a test document", acl, readers, "workspace://SpacesStore/test_doc");

        // Search as admin
        JsonNode results = search.searchWithAcl("test document", "keyword", "admin");
        assertTrue(results.path("hits").path("total").path("value").asInt() > 0);

        // Search as user without access
        results = search.searchWithAcl("test document", "keyword", "unauthorized_user");
        assertEquals(0, results.path("hits").path("total").path("value").asInt());
    }
}