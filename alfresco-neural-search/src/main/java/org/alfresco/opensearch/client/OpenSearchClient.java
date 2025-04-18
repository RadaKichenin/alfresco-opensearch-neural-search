package org.alfresco.opensearch.client;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

/**
 * Client for interacting with OpenSearch.
 */
@Component
public class OpenSearchClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchClient.class);

    @Autowired
    private OpenSearchClientFactory openSearchClientFactory;

    /**
     * Executes a request against the OpenSearch API.
     *
     * @param method the HTTP method
     * @param endpoint the API endpoint
     * @param body the request body
     * @return the response as a string
     * @throws IOException if an I/O error occurs
     */
    public String executeRequest(String method, String endpoint, String body) throws IOException {
        RestClient client = openSearchClientFactory.getRestClient();
        Request request = new Request(method, endpoint);

        if (body != null && !body.isEmpty()) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }

        Response response = client.performRequest(request);

        try (InputStream is = response.getEntity().getContent()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}