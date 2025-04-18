package org.alfresco.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.alfresco.opensearch.client.AlfrescoContentApiClient;
import org.alfresco.opensearch.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Controller class for handling search requests with ACL filtering.
 */
@RestController
@RequestMapping("/search")
public class SearchController {
    private static final Logger LOG = LoggerFactory.getLogger(SearchController.class);

    private final Search search;
    private final AlfrescoContentApiClient alfrescoContentApiClient;

    @Autowired
    public SearchController(Search search, AlfrescoContentApiClient alfrescoContentApiClient) {
        this.search = search;
        this.alfrescoContentApiClient = alfrescoContentApiClient;
    }

    @GetMapping
    public List<DocumentBean> search(
            @RequestParam String query, 
            @RequestParam(defaultValue = "neural") String searchType,
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws Exception {

        // Extract username from auth header or use default
        String username = extractUsername(authHeader);
        LOG.debug("Performing search for user: {}", username);

        // Perform search with ACL filtering
        JsonNode results = search.searchWithAcl(query, searchType, username);

        // Extract hits from search results
        JsonNode hitsNode = results.path("hits").path("hits");

        // Process hits and create DocumentBean objects
        List<DocumentBean> documents = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            String id = sourceNode.path("id").asText();
            // Remove segment suffix if present (documentId_0 -> documentId)
            id = id.contains("_") ? id.substring(0, id.lastIndexOf("_")) : id;
            String name = sourceNode.path("name").asText();
            String text = sourceNode.path("text").asText();
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
     * Extracts username from Authorization header
     * 
     * @param authHeader The Authorization header
     * @return The username or "guest" if not available
     */
    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring("Basic ".length());
                byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(credDecoded, StandardCharsets.UTF_8);
                return credentials.split(":", 2)[0];
            } catch (Exception e) {
                LOG.warn("Error extracting username from auth header", e);
                return "guest";
            }
        }
        return "guest";
    }
}