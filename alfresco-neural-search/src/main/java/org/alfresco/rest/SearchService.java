package org.alfresco.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.alfresco.opensearch.search.Search;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller class for handling search requests.
 */
@RestController
public class SearchService {
    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    private Search search;

    /**
     * Handles search requests and returns a list of {@link DocumentBean} objects based on the specified search type.
     *
     * <p>This method performs a search using the specified query and search type (neural, text, or hybrid). It extracts
     * the hits from the search results, processes each hit to create a {@link DocumentBean} object, and returns a list
     * of these objects.</p>
     *
     * @param query the search query string
     * @param searchType the type of search to perform; can be "neural", "keyword", or "hybrid"
     * @return a list of {@link DocumentBean} objects representing the search results
     * @throws Exception if an error occurs during the search or processing of results
     * @throws IllegalArgumentException if the provided search type is invalid
     */
    @GetMapping("/search")
    public List<DocumentBean> search(@RequestParam String query, @RequestParam(defaultValue = "neural") String searchType) throws Exception {
        LOG.info("Performing {} search for query: {}", searchType, query);

        // Get search results based on the search type
        JsonNode results;
        try {
            results = search.searchWithAcl(query, searchType, null);
        } catch (Exception e) {
            LOG.error("Error performing search", e);
            throw e;
        }

        // Extract hits from search results
        JsonNode hitsNode = results.path("hits").path("hits");
        LOG.debug("Found {} results", hitsNode.size());

        // Process hits and create DocumentBean objects
        List<DocumentBean> documents = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            String id = sourceNode.path("id").asText();
            String name = sourceNode.path("name").asText();
            // Escape special characters in text content
            String text = JsonUtils.escape(sourceNode.path("text").asText());
            String nodeRef = sourceNode.has("nodeRef") ? sourceNode.path("nodeRef").asText() : "";

            documents.add(DocumentBean.builder()
                .uuid(id)
                .name(name)
                .text(text)
                .nodeRef(nodeRef)
                .build());
        }

        return documents;
    }

    /**
     * Handles search requests with authentication and returns a list of {@link DocumentBean} objects.
     *
     * <p>This method performs a search using the specified query, search type, and user credentials. It applies
     * ACL filtering to ensure the user only sees results they have permission to access.</p>
     *
     * @param query the search query string
     * @param searchType the type of search to perform; can be "neural", "keyword", or "hybrid"
     * @param username the username for authentication
     * @return a list of {@link DocumentBean} objects representing the search results
     * @throws Exception if an error occurs during the search or processing of results
     */
    @GetMapping("/secure-search")
    public List<DocumentBean> secureSearch(
            @RequestParam String query, 
            @RequestParam(defaultValue = "neural") String searchType,
            @RequestParam String username) throws Exception {

        LOG.info("Performing secure {} search for query: {} as user: {}", searchType, query, username);

        // Get search results with ACL filtering
        JsonNode results = search.searchWithAcl(query, searchType, username);

        // Extract hits from search results
        JsonNode hitsNode = results.path("hits").path("hits");
        LOG.debug("Found {} results for user {}", hitsNode.size(), username);

        // Process hits and create DocumentBean objects
        List<DocumentBean> documents = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            String id = sourceNode.path("id").asText();
            String name = sourceNode.path("name").asText();
            // Escape special characters in text content
            String text = JsonUtils.escape(sourceNode.path("text").asText());
            String nodeRef = sourceNode.has("nodeRef") ? sourceNode.path("nodeRef").asText() : "";

            documents.add(DocumentBean.builder()
                .uuid(id)
                .name(name)
                .text(text)
                .nodeRef(nodeRef)
                .build());
        }

        return documents;
    }
}