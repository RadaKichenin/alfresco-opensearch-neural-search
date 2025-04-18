package org.alfresco.opensearch.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.AlfrescoContentApiClient;
import org.alfresco.opensearch.client.OpenSearchClient;
import org.alfresco.rest.DocumentBean;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Component for performing searches in OpenSearch.
 */
@Component
public class Search {
    private static final Logger LOG = LoggerFactory.getLogger(Search.class);

    @Value("${opensearch.index.name}")
    private String openSearchIndex;

    @Autowired
    private OpenSearchClient openSearchClient;

    @Autowired
    private AlfrescoContentApiClient alfrescoContentApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Performs a search using the specified query and search type.
     *
     * @param query      the search query
     * @param searchType the type of search (neural, keyword, hybrid)
     * @return a list of document beans matching the search criteria
     * @throws Exception if an error occurs during the search
     */
    public List<DocumentBean> search(String query, String searchType) throws Exception {
        String searchQuery;
        switch (searchType.toLowerCase()) {
            case "keyword":
                searchQuery = buildKeywordQuery(query);
                break;
            case "hybrid":
                searchQuery = buildHybridQuery(query);
                break;
            default:
                searchQuery = buildNeuralQuery(query);
                break;
        }

        JsonNode response = executeRequest("POST", "/" + openSearchIndex + "/_search", searchQuery);
        return processSearchResults(response);
    }

    /**
     * Executes a request against the OpenSearch API.
     *
     * @param method the HTTP method
     * @param endpoint the API endpoint
     * @param body the request body
     * @return the response as a JsonNode
     * @throws IOException if an I/O error occurs
     */
    public JsonNode executeRequest(String method, String endpoint, String body) throws IOException {
        String response = openSearchClient.executeRequest(method, endpoint, body);
        return objectMapper.readTree(response);
    }

    /**
     * Processes the search results and converts them to document beans.
     *
     * @param response the search response
     * @return a list of document beans
     */
    private List<DocumentBean> processSearchResults(JsonNode response) {
        List<DocumentBean> results = new ArrayList<>();
        JsonNode hits = response.path("hits").path("hits");

        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String id = source.path("id").asText();
            // Remove segment suffix if present (documentId_0 -> documentId)
            id = id.contains("_") ? id.substring(0, id.lastIndexOf("_")) : id;
            String name = source.path("name").asText();
            String text = source.path("text").asText();
            String nodeRef = source.has("nodeRef") ? source.path("nodeRef").asText() : "";

            results.add(DocumentBean.builder()
                .uuid(id)
                .name(name)
                .text(text)
                .nodeRef(nodeRef)
                .build());
        }

        return results;
    }

    /**
     * Builds a neural search query.
     *
     * @param query the search query
     * @return the neural search query as a JSON string
     */
    private String buildNeuralQuery(String query) {
        return """
            {
              "query": {
                "neural": {
                  "passage_embedding": {
                    "query_text": "%s",
                    "k": 20
                  }
                }
              },
              "size": 20
            }
            """.formatted(JsonUtils.escape(query));
    }

    /**
     * Builds a keyword search query.
     *
     * @param query the search query
     * @return the keyword search query as a JSON string
     */
    private String buildKeywordQuery(String query) {
        return """
            {
              "query": {
                "match": {
                  "text": {
                    "query": "%s"
                  }
                }
              },
              "size": 20
            }
            """.formatted(JsonUtils.escape(query));
    }

    /**
     * Builds a hybrid search query.
     *
     * @param query the search query
     * @return the hybrid search query as a JSON string
     */
    private String buildHybridQuery(String query) {
        return """
            {
              "query": {
                "bool": {
                  "should": [
                    {
                      "neural": {
                        "passage_embedding": {
                          "query_text": "%s",
                          "k": 10
                        }
                      }
                    },
                    {
                      "match": {
                        "text": {
                          "query": "%s"
                        }
                      }
                    }
                  ]
                }
              },
              "size": 20
            }
            """.formatted(JsonUtils.escape(query), JsonUtils.escape(query));
    }

    /**
     * Performs a search with ACL filtering.
     * This is a placeholder implementation that will be expanded later.
     *
     * @param query the search query
     * @param searchType the type of search (neural, keyword, hybrid)
     * @param username the username of the current user
     * @return the search results as a JsonNode
     * @throws IOException if an I/O error occurs
     */
    public JsonNode searchWithAcl(String query, String searchType, String username) throws IOException {
        // For now, just use the regular search query
        String searchQuery;
        switch (searchType.toLowerCase()) {
            case "keyword":
                searchQuery = buildKeywordQuery(query);
                break;
            case "hybrid":
                searchQuery = buildHybridQuery(query);
                break;
            default:
                searchQuery = buildNeuralQuery(query);
                break;
        }

        return executeRequest("POST", "/" + openSearchIndex + "/_search", searchQuery);
    }
}