package org.alfresco.opensearch.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.OpenSearchClient;
import org.alfresco.opensearch.model.acl.AclEntry;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component for indexing documents in OpenSearch.
 */
@Component
public class Indexer {
    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

    @Value("${opensearch.index.name}")
    private String openSearchIndex;

    @Autowired
    private OpenSearchClient openSearchClient;

    /**
     * Indexes a document in OpenSearch.
     *
     * @param id the document ID
     * @param dbid the database ID
     * @param contentId the content ID
     * @param name the document name
     * @param text the document text
     */
    public void index(String id, Long dbid, String contentId, String name, String text) {
        try {
            String document = """
                {
                  "id": "%s",
                  "dbid": %d,
                  "contentId": "%s",
                  "name": "%s",
                  "text": "%s"
                }
                """.formatted(
                    id, 
                    dbid, 
                    contentId, 
                    JsonUtils.escape(name), 
                    JsonUtils.escape(text)
                );

            openSearchClient.executeRequest("POST", "/" + openSearchIndex + "/_doc/" + id, document);
        } catch (Exception e) {
            LOG.error("Error indexing document {}", id, e);
        }
    }

    /**
     * Indexes a document with ACL information.
     * This is a placeholder implementation that will be expanded later.
     *
     * @param id the document ID
     * @param dbid the database ID
     * @param contentId the content ID
     * @param name the document name
     * @param text the document text
     * @param acl the ACL entries
     * @param readers the list of readers
     * @param nodeRef the node reference
     */
    public void index(String id, Long dbid, String contentId, String name, String text, 
                     List<AclEntry> acl, List<String> readers, String nodeRef) {
        // For now, just use the regular index method
        index(id, dbid, contentId, name, text);
    }

    /**
     * Deletes a document from the index if it exists.
     *
     * @param id the document ID
     */
    public void deleteDocumentIfExists(String id) {
        try {
            openSearchClient.executeRequest("DELETE", "/" + openSearchIndex + "/_doc/" + id, null);
        } catch (Exception e) {
            LOG.debug("Error deleting document {}: {}", id, e.getMessage());
        }
    }

    /**
     * Alias for deleteDocumentIfExists for backward compatibility.
     *
     * @param id the document ID
     */
    public void deleteDocument(String id) {
        deleteDocumentIfExists(id);
    }

    /**
     * Gets the content ID for a document.
     *
     * @param id the document ID
     * @return the content ID, or an empty string if not found
     */
    public String getContentId(String id) {
        try {
            String response = openSearchClient.executeRequest("GET", "/" + openSearchIndex + "/_doc/" + id, null);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(response).path("_source").path("contentId").asText("");
        } catch (Exception e) {
            LOG.debug("Error getting content ID for document {}: {}", id, e.getMessage());
            return "";
        }
    }

    /**
     * Verifies the status of the index.
     *
     * @return true if the index is healthy, false otherwise
     */
    public boolean verifyIndexStatus() {
        try {
            String response = openSearchClient.executeRequest("GET", "/_cluster/health/" + openSearchIndex, null);
            ObjectMapper mapper = new ObjectMapper();
            String status = mapper.readTree(response).path("status").asText();
            return "green".equals(status) || "yellow".equals(status);
        } catch (Exception e) {
            LOG.error("Error verifying index status", e);
            return false;
        }
    }
}