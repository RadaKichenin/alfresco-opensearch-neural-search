package org.alfresco.security;

import org.alfresco.opensearch.client.AlfrescoContentApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling authentication with Alfresco.
 */
@Service
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

    private final AlfrescoContentApiClient alfrescoContentApiClient;

    @Autowired
    public AuthenticationService(AlfrescoContentApiClient alfrescoContentApiClient) {
        this.alfrescoContentApiClient = alfrescoContentApiClient;
    }

    /**
     * Validates user credentials against Alfresco
     * 
     * @param username The username
     * @param password The password
     * @return true if authentication is successful, false otherwise
     */
    public boolean authenticate(String username, String password) {
        return alfrescoContentApiClient.authenticate(username, password);
    }

    /**
     * Gets the list of authorities for a user
     * 
     * @param username The username
     * @param password The password
     * @return List of authority IDs
     */
    public List<String> getUserAuthorities(String username, String password) {
        try {
            return alfrescoContentApiClient.getUserAuthorities(username, password);
        } catch (Exception e) {
            LOG.error("Error getting authorities for user {}", username, e);
            return List.of(username);
        }
    }
}