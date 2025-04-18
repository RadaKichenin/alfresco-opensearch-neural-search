package org.alfresco.opensearch.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.OpenSearchClientFactory;
import org.alfresco.opensearch.index.OpenSearchConfiguration;
import org.alfresco.utils.JsonUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Component for executing searches in OpenSearch.
 */
@Component
public class Search {

    @Value("${opensearch.index.name}")
    private String indexName;

    @Value("${opensearch.ingest.pipeline.name}")
    private String pipelineName;

    @Value("${opensearch.results.count}")
    private int resultsCount;

    @Autowired
    private OpenSearchConfiguration openSearchConfiguration;

    @Autowired
    private OpenSearchClientFactory openSearchClientFactory;

    @Autowired
    private AlfrescoContentApiClient alfrescoContentApiClient;

private static final Logger LOG = LoggerFactory.getLogger(Search.class);
    /**
     * Retrieves an instance of RestClient from the factory.
     *
     * @return RestClient instance
     */
    private RestClient restClient() {
        return openSearchClientFactory.getRestClient();
    }

    /**
     * Executes a keyword search query in OpenSearch.
     *
     * @param query the search query
     * @return the search result as a JsonNode
     * @throws IOException if an I/O error occurs during the request
     */
    public JsonNode keywordSearch(String query) throws IOException {
        Request request = new Request("GET", "/" + indexName + "/_search");
        String jsonSearchPayload = """
            {
              "_source": {
                "excludes": [
                  "passage_embedding"
                ]
              },
              "query": {
                "match": {
                  "text": {
                    "query": "%s"
                  }
                }
              }
            }
            """;

        String escapedQuery = JsonUtils.escape(query);
        request.setEntity(new StringEntity(String.format(jsonSearchPayload, escapedQuery), ContentType.APPLICATION_JSON));
        return search(request);
    }

    /**
     * Executes a neural search query in OpenSearch.
     *
     * @param query the search query
     * @return the search result as a JsonNode
     * @throws IOException if an I/O error occurs during the request
     */
    public JsonNode neuralSearch(String query) throws IOException {
        Request request = new Request("GET", "/" + indexName + "/_search");
        String jsonSearchPayload = """
             {
               "_source": {
                 "excludes": [
                   "passage_embedding"
                 ]
               },
               "query": {
                 "neural": {
                   "passage_embedding": {
                     "query_text": "%s",
                     "model_id": "%s",
                     "k": %s
                   }
                 }
               }
             }
             """;

        String escapedQuery = JsonUtils.escape(query);
        String modelId = openSearchConfiguration.getModelId();
        request.setEntity(new StringEntity(String.format(jsonSearchPayload, escapedQuery, modelId, resultsCount), ContentType.APPLICATION_JSON));
        return search(request);
    }

    /**
     * Executes a hybrid search (keyword + neural) query in OpenSearch.
     *
     * @param query the search query
     * @return the search result as a JsonNode
     * @throws IOException if an I/O error occurs during the request
     */
    public JsonNode hybridSearch(String query) throws IOException {
        Request request = new Request("GET", "/" + indexName + "/_search?search_pipeline=" + pipelineName);
        String jsonSearchPayload = """
            {
              "_source": {
                "exclude": [
                  "passage_embedding"
                ]
              },
              "query": {
                "hybrid": {
                  "queries": [
                    {
                      "match": {
                        "text": {
                          "query": "%s"
                        }
                      }
                    },
                    {
                      "neural": {
                        "passage_embedding": {
                          "query_text": "%s",
                          "model_id": "%s",
                          "k": %s
                        }
                      }
                    }
                  ]
                }
              }
            }
            """;

        String escapedQuery = JsonUtils.escape(query);
        String modelId = openSearchConfiguration.getModelId();
        request.setEntity(new StringEntity(String.format(jsonSearchPayload, escapedQuery, escapedQuery, modelId, resultsCount), ContentType.APPLICATION_JSON));
        return search(request);
    }

    /**
     * Executes a search request using the provided {@link Request} object and parses the response into a {@link JsonNode}.
     *
     * <p>This method uses an HTTP client to perform the search request, then converts the response content into a JSON
     * representation using the Jackson library.</p>
     *
     * @param request the search {@link Request} to be executed
     * @return a {@link JsonNode} representing the JSON response from the search request
     * @throws IOException if an I/O error occurs during the execution of the request or parsing the response
     */
    private JsonNode search(Request request) throws IOException {
        Response response = restClient().performRequest(request);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readTree(response.getEntity().getContent());
    }
    /**
     * Performs a search with ACL filtering
     * 
     * @param query The search query
     * @param searchType The type of search (neural, keyword, hybrid)
     * @param username The username of the current user
     * @return Search results filtered by ACL
     * @throws IOException If an error occurs
     */
    public JsonNode searchWithAcl(String query, String searchType, String username) throws IOException {
        // Get user authorities
        List<String> authorities;
        try {
            authorities = alfrescoContentApiClient.getCurrentUserAuthorities();
        } catch (Exception e) {
            LOG.error("Error getting user authorities", e);
            authorities = Collections.singletonList(username);
        }

        // Build the search query with ACL filter
        String searchQuery = buildSearchQueryWithAcl(query, searchType, authorities);

        // Execute the search
        return openSearchClient.executeRequest("POST", "/" + openSearchIndex + "/_search", searchQuery);
    }

    private String buildSearchQueryWithAcl(String query, String searchType, List<String> authorities) {
        // Base query based on search type
        JsonNode baseQuery;
        try {
            baseQuery = switch (searchType.toLowerCase()) {
                case "keyword" -> buildKeywordQuery(query);
                case "hybrid" -> buildHybridQuery(query);
                default -> buildNeuralQuery(query);
            };
        } catch (Exception e) {
            LOG.error("Error building base query", e);
            throw new RuntimeException("Error building search query", e);
        }

        // Build the authorities filter
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode authoritiesArray = mapper.createArrayNode();
        for (String authority : authorities) {
            authoritiesArray.add(authority);
        }

        // Create the complete query with ACL filter
        ObjectNode fullQuery = mapper.createObjectNode();
        ObjectNode boolQuery = mapper.createObjectNode();
        fullQuery.set("query", boolQuery);

        // Add the base query as "must"
        ArrayNode mustArray = mapper.createArrayNode();
        mustArray.add(baseQuery);
        boolQuery.set("must", mustArray);

        // Add the ACL filter
        ObjectNode filterNode = mapper.createObjectNode();
        ObjectNode termsNode = mapper.createObjectNode();
        termsNode.set("readers", authoritiesArray);
        filterNode.set("terms", termsNode);
        boolQuery.set("filter", filterNode);

        // Add size and other parameters
        fullQuery.put("size", 20);

        try {
            return mapper.writeValueAsString(fullQuery);
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing query", e);
            throw new RuntimeException("Error building search query", e);
        }
    }

    // Helper methods to build base queries
    private JsonNode buildNeuralQuery(String query) throws Exception {
        // Implementation for neural search query
    }

    private JsonNode buildKeywordQuery(String query) throws Exception {
        // Implementation for keyword search query
    }

    private JsonNode buildHybridQuery(String query) throws Exception {
        // Implementation for hybrid search query
    }
    /**
     * Performs a search with ACL filtering
     * 
     * @param query The search query
     * @param searchType The type of search (neural, keyword, hybrid)
     * @param username The username of the current user
     * @return Search results filtered by ACL
     * @throws IOException If an error occurs
     */
    public JsonNode searchWithAcl(String query, String searchType, String username) throws IOException {
        // Get user authorities
        List<String> authorities;
        try {
            authorities = alfrescoContentApiClient.getUserAuthorities(username, "admin");
        } catch (Exception e) {
            LOG.error("Error getting user authorities", e);
            authorities = Collections.singletonList(username);
        }

        // Build the search query with ACL filter
        String searchQuery = buildSearchQueryWithAcl(query, searchType, authorities);

        // Execute the search
        return executeRequest("POST", "/" + openSearchIndex + "/_search", searchQuery);
    }

    private String buildSearchQueryWithAcl(String query, String searchType, List<String> authorities) {
        // Base query based on search type
        String baseQuery;
        try {
            baseQuery = switch (searchType.toLowerCase()) {
                case "keyword" -> buildKeywordQueryString(query);
                case "hybrid" -> buildHybridQueryString(query);
                default -> buildNeuralQueryString(query);
            };
        } catch (Exception e) {
            LOG.error("Error building base query", e);
            throw new RuntimeException("Error building search query", e);
        }

        // Convert authorities to JSON array
        StringBuilder authoritiesJson = new StringBuilder("[");
        for (int i = 0; i < authorities.size(); i++) {
            if (i > 0) {
                authoritiesJson.append(",");
            }
            authoritiesJson.append("\"").append(authorities.get(i)).append("\"");
        }
        authoritiesJson.append("]");

        // Create the complete query with ACL filter
        return """
            {
              "query": {
                "bool": {
                  "must": %s,
                  "filter": {
                    "terms": {
                      "readers": %s
                    }
                  }
                }
              },
              "size": 20
            }
            """.formatted(baseQuery, authoritiesJson);
    }

    // Helper methods to build base queries as strings
    private String buildNeuralQueryString(String query) throws Exception {
        // Implementation for neural search query
        return """
            {
              "neural": {
                "passage_embedding": {
                  "query_text": "%s",
                  "k": 20
                }
              }
            }
            """.formatted(JsonUtils.escape(query));
    }

    private String buildKeywordQueryString(String query) throws Exception {
        // Implementation for keyword search query
        return """
            {
              "match": {
                "text": {
                  "query": "%s"
                }
              }
            }
            """.formatted(JsonUtils.escape(query));
    }

    private String buildHybridQueryString(String query) throws Exception {
        // Implementation for hybrid search query
        return """
            [
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
            """.formatted(JsonUtils.escape(query), JsonUtils.escape(query));
    }
}