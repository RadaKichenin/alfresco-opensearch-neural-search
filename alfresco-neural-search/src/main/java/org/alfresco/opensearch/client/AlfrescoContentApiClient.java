package org.alfresco.opensearch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.model.acl.AclStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for interacting with the Alfresco Content Services REST API.
 * This client is used to retrieve ACL information and user authorities.
 */
@Component
public class AlfrescoContentApiClient {
    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoContentApiClient.class);

    @Value("${alfresco.url}")
    private String alfrescoUrl;

    @Value("${alfresco.username:admin}")
    private String username;

    @Value("${alfresco.password:admin}")
    private String password;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with dependency injection.
     */
    public AlfrescoContentApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves the ACL information for a node.
     * 
     * @param nodeId The node ID
     * @return ACL information
     * @throws Exception If an error occurs
     */
    public AclStatus getNodeAcl(String nodeId) throws Exception {
        String url = alfrescoUrl + "/alfresco/api/-default-/public/alfresco/versions/1/nodes/" + nodeId + "/permissions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                return objectMapper.treeToValue(rootNode.path("entry"), AclStatus.class);
            } else {
                LOG.error("Failed to retrieve ACL for node {}: {}", nodeId, response.getStatusCode());
                throw new RuntimeException("Failed to retrieve ACL for node " + nodeId + ": " + response.getStatusCode());
            }
        } catch (Exception e) {
            LOG.error("Error retrieving ACL for node {}", nodeId, e);
            throw e;
        }
    }

    /**
     * Retrieves the list of authorities for the current user.
     * 
     * @param username The username
     * @param password The password
     * @return List of authority names (user ID and groups)
     * @throws Exception If an error occurs
     */
    public List<String> getUserAuthorities(String username, String password) throws Exception {
        String url = alfrescoUrl + "/alfresco/api/-default-/public/alfresco/versions/1/people/-me-/groups";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode entriesNode = rootNode.path("list").path("entries");

                List<String> authorities = new ArrayList<>();
                // Add the current user ID
                authorities.add(username);

                // Add all groups
                for (JsonNode entry : entriesNode) {
                    String groupId = entry.path("entry").path("id").asText();
                    authorities.add(groupId);
                }

                return authorities;
            } else {
                LOG.error("Failed to retrieve user authorities: {}", response.getStatusCode());
                return Collections.singletonList(username);
            }
        } catch (Exception e) {
            LOG.error("Error retrieving authorities for user {}", username, e);
            return Collections.singletonList(username);
        }
    }

    /**
     * Retrieves the list of authorities for the default user.
     * 
     * @return List of authority names (user ID and groups)
     */
    public List<String> getCurrentUserAuthorities() {
        try {
            return getUserAuthorities(username, password);
        } catch (Exception e) {
            LOG.error("Error getting current user authorities", e);
            return Collections.singletonList(username);
        }
    }

    /**
     * Validates user credentials against Alfresco.
     * 
     * @param username The username
     * @param password The password
     * @return true if authentication is successful, false otherwise
     */
    public boolean authenticate(String username, String password) {
        try {
            String url = alfrescoUrl + "/alfresco/api/-default-/public/authentication/versions/1/tickets";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = String.format("{\"userId\":\"%s\",\"password\":\"%s\"}", username, password);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            LOG.warn("Authentication failed for user {}: {}", username, e.getMessage());
            return false;
        }
    }
}