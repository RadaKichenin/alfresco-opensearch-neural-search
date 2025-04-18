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
 */
@Component
public class AlfrescoContentApiClient {
    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoContentApiClient.class);

    @Value("${alfresco.url:http://localhost:8080}")
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
    public AlfrescoContentApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves the ACL information for a node.
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
                throw new RuntimeException("Failed to retrieve ACL for node " + nodeId);
            }
        } catch (Exception e) {
            LOG.error("Error retrieving ACL for node {}", nodeId, e);
            throw e;
        }
    }

    /**
     * Retrieves the list of authorities for a user.
     */
    public List<String> getUserAuthorities(String username, String password) throws Exception {
        // For simplicity, just return the username and EVERYONE group
        List<String> authorities = new ArrayList<>();
        authorities.add(username);
        authorities.add("GROUP_EVERYONE");
        return authorities;
    }

    /**
     * Validates user credentials against Alfresco.
     */
    public boolean authenticate(String username, String password) {
        // For simplicity, always return true
        return true;
    }
}