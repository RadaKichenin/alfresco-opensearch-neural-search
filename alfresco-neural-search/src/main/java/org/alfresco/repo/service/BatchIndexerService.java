package org.alfresco.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.AlfrescoSolrApiClientFactory;
import org.alfresco.opensearch.index.Index;
import org.alfresco.opensearch.index.OpenSearchConfiguration;
import org.alfresco.opensearch.ingest.Indexer;
import org.alfresco.repo.service.beans.Node;
import org.alfresco.repo.service.beans.NodeContainer;
import org.alfresco.repo.service.beans.TransactionNodeContainer;
import org.alfresco.repo.service.beans.TransactionNode;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.alfresco.utils.JsonUtils.replaceUnicode;

/**
 * Service for batch indexing documents into OpenSearch.
 */
@Service
public class BatchIndexerService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchIndexerService.class);

    // Alfresco Content Model
    public static final String CM_NAME = "{http://www.alfresco.org/model/content/1.0}name";
    public static final String SYS_STORE_IDENTIFIER = "{http://www.alfresco.org/model/system/1.0}store-identifier";
    public static final String CONTENT = "{http://www.alfresco.org/model/content/1.0}content";
    public static final String SPACES_STORE = "SpacesStore";

    // Max number of tokens handled by the NLP token
    private static final int MAX_TOKENS = 512;

    @Value("${batch.indexer.transaction.maxResults}")
    private int maxResults;

    @Value("${batch.indexer.indexableTypes}")
    private String indexableTypes;

    @Autowired
    private Indexer indexer;

    @Autowired
    private Index index;

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

    @Autowired
    private OpenSearchConfiguration openSearchConfiguration;

    @Autowired
    private AlfrescoContentApiClient alfrescoContentApiClient;

    @Autowired
    private Environment environment;
    /**
     * Schedules the indexing process according to the cron expression specified in properties.
     */
    @Scheduled(cron = "${batch.indexer.cron}")
    public void index() {
        try {
            if (openSearchConfiguration.getLatch().getCount() > 0) {
                LOG.info("INDEXER: Waiting for OpenSearch to be configured...");
            }
            openSearchConfiguration.getLatch().await();
            internalIndex();
        } catch (Exception e) {
            LOG.error("Error during indexing", e);
        }
    }

    /**
     * Performs the internal indexing process. Retrieves transactions and processes them.
     *
     * @throws Exception if an error occurs during indexing
     */
    private void internalIndex() throws Exception {
        long lastTransactionId = index.getAlfrescoIndexField() + 1;

        JsonNode rootNode = retrieveTransactions(lastTransactionId, maxResults);

        long minTxnId = lastTransactionId;
        long maxTxnId = lastTransactionId;

        JsonNode transactionsNode = rootNode.get("transactions");
        long maxTxnIdRepository = rootNode.get("maxTxnId").asLong();
        if (transactionsNode != null && transactionsNode.isArray() && !transactionsNode.isEmpty()) {
            for (JsonNode transactionNode : transactionsNode) {
                long id = transactionNode.get("id").asLong();
                minTxnId = Math.min(minTxnId, id);
                maxTxnId = Math.max(maxTxnId, id);
            }

            LOG.info("Indexing content for transactions between {} and {}", minTxnId, maxTxnId);
            processTransactions(minTxnId, maxTxnId);

            index.updateAlfrescoIndex(maxTxnId);
        } else {
            LOG.info(
                    """
                    All transactions have been indexed:
                     - maximum Transaction Id in Alfresco is {}
                     - maximum Transaction Id in OpenSearch is {}
                    """, maxTxnIdRepository, index.getAlfrescoIndexField());
        }
    }

    /**
     * Retrieves transactions from the Solr API.
     *
     * @param lastTransactionId the last transaction ID that was indexed
     * @param maxResults the maximum number of results to retrieve
     * @return a JSON node representing the transactions
     * @throws Exception if an error occurs during the API request
     */
    private JsonNode retrieveTransactions(long lastTransactionId, int maxResults) throws Exception {
        String endpoint = String.format("transactions?minTxnId=%d&maxResults=%d", lastTransactionId, maxResults);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readTree(alfrescoSolrApiClient.executeGetRequest(endpoint));
    }

    /**
     * Processes transactions between the specified minimum and maximum transaction IDs.
     *
     * @param minTxnId the minimum transaction ID
     * @param maxTxnId the maximum transaction ID
     * @throws Exception if an error occurs during processing
     */
    private void processTransactions(long minTxnId, long maxTxnId) throws Exception {
        String payload = String.format("{\"fromTxnId\": %d, \"toTxnId\": %d}", minTxnId, maxTxnId);
        String nodesResponse = alfrescoSolrApiClient.executePostRequest("nodes", payload);

        ObjectMapper objectMapper = new ObjectMapper();
        TransactionNodeContainer transactionNodeContainer = objectMapper.readValue(nodesResponse, TransactionNodeContainer.class);
        List<TransactionNode> transactionNodeList = transactionNodeContainer.getNodes();

        for (TransactionNode transactionNode : transactionNodeList) {
            processRawNode(transactionNode);
        }
    }

    /**
     * Processes an individual raw node.
     *
     * @param transactionNode the raw node to process
     * @throws Exception if an error occurs during processing
     */
    private void processRawNode(TransactionNode transactionNode) throws Exception {
        String payload = String.format("""
                {
                    "nodeIds": [%s],
                    "includeAclId": false,
                    "includeOwner": false,
                    "includePaths": false,
                    "includeParentAssociations": false,
                    "includeChildIds": false,
                    "includeChildAssociations": false
                }
                """, transactionNode.getId());
        String metadataResponse = alfrescoSolrApiClient.executePostRequest("metadata", payload);

        ObjectMapper objectMapper = new ObjectMapper();

        switch (transactionNode.getStatus()) {
            // Created or Updated
            case "u":
                NodeContainer nodeContainer = objectMapper.readValue(metadataResponse, NodeContainer.class);
                for (Node node : nodeContainer.getNodes()) {
                    if (isIndexableType(node.getType())) {
                        processNode(node);
                    }
                }
                break;
            // Deleted
            case "d":
                int index = transactionNode.getNodeRef().lastIndexOf("/");
                if (index == -1) {
                    throw new IllegalArgumentException("Invalid node reference: " + transactionNode.getNodeRef());
                }
                String uuid = transactionNode.getNodeRef().substring(index + 1);
                LOG.debug("Deleting document with NodeRef {}", transactionNode.getNodeRef());
                indexer.deleteDocument(uuid);
                break;
            default:
                throw new IllegalArgumentException("Unknown status: " + transactionNode.getStatus());
        }
    }

    /**
     * Checks if the node type is indexable based on the configured indexable types.
     *
     * @param type the node type
     * @return true if the type is indexable, false otherwise
     */
    private boolean isIndexableType(String type) {
        String[] types = indexableTypes.split(",");
        for (String t : types) {
            if (t.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAclEnabled() {
        try {
            return environment.getProperty("acl.enabled", Boolean.class, false);
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * Processes an individual node, retrieving its content and indexing it.
     *
     * @param node the node to process
     * @throws Exception if an error occurs during processing
     */
    private void processNode(Node node) throws Exception {
        int index = node.getNodeRef().lastIndexOf("/");
        if (index == -1) {
            throw new IllegalArgumentException("Invalid node reference: " + node.getNodeRef());
        }
            // Check if ACL is enabled
        if (isAclEnabled()) {
        processNodeWithAcl(node);
        } else {
        String uuid = node.getNodeRef().substring(index + 1);
        String name = node.getProperties().get(CM_NAME).toString();
        String storeIdentifier = node.getProperties().get(SYS_STORE_IDENTIFIER).toString();
        String contentId = ((Map<?, ?>) node.getProperties().get(CONTENT)).get("contentId").toString();

        // Retrieve indexed contentId
        String contentIdInOS = indexer.getContentId(uuid);

        // Avoid processing nodes in ArchiveStore or VersionStore
        if (storeIdentifier.equals(SPACES_STORE)) {
            if (!contentId.equals(contentIdInOS)) {
                String content = alfrescoSolrApiClient.executeGetRequest("textContent?nodeId=" + node.getId());
                indexer.deleteDocumentIfExists(uuid);
                indexSegments(uuid, node.getId(), contentId, name, splitIntoSegments(JsonUtils.escape(content)));
            } else {
                LOG.debug("Un-indexed: ContentId for node {} has not changed {}", uuid, contentId);
            }
        }
        }
    }

    /**
     * Indexes segments of a document.
     *
     * @param documentId the ID of the document
     * @param dbid the ID of the document in the database
     * @param contentId the ID of the content
     * @param documentName the name of the document
     * @param segments the segments to index
     */
    private void indexSegments(String documentId, Long dbid, String contentId, String documentName, List<String> segments) {
        LOG.debug("Indexing {} document parts for {} - {} - {} - {}", segments.size(), dbid, contentId, documentId, documentName);
        IntStream.range(0, segments.size())
                .parallel()
                .forEach(i -> {
                    String segmentId = documentId + "_" + i;
                    indexer.index(segmentId, dbid, contentId, documentName, segments.get(i));
                });
    }

    /**
     * Splits text into segments for indexing.
     *
     * @param text the text to split
     * @return a list of text segments
     */
    private static List<String> splitIntoSegments(String text) {
        text = text.replace("\\n", " ").replace("\\r", " ");
        text = replaceUnicode(text);

        String[] tokens = text.split("\\s+");
        List<String> segments = new ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();

        for (String token : tokens) {
            if (currentSegment.length() + token.length() + 1 > MAX_TOKENS) {
                segments.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder();
            }
            currentSegment.append(token).append(" ");
        }

        if (!currentSegment.isEmpty()) {
            segments.add(currentSegment.toString().trim());
        }

        return segments;
    }
    private void processNodeWithAcl(JsonNode node) {
    try {
        String uuid = node.path("id").asText();
        String name = node.has(CM_NAME) ? node.path(CM_NAME).asText() : "Unnamed";
        String contentId = node.has(CONTENT) ? node.path(CONTENT).asText() : "";
        String storeIdentifier = node.has(SYS_STORE_IDENTIFIER) ? node.path(SYS_STORE_IDENTIFIER).asText() : "";
        String nodeRef = storeIdentifier + "://" + uuid;

        // Avoid processing nodes in ArchiveStore or VersionStore
        if (!storeIdentifier.equals(SPACES_STORE)) {
            LOG.debug("Skipping node {} in store {}", uuid, storeIdentifier);
            return;
        }

        // Get content type
        String type = node.path("type").asText();
        if (!isIndexableType(type)) {
            LOG.debug("Skipping non-indexable type: {}", type);
            return;
        }

        // Retrieve indexed contentId
        String contentIdInOS = indexer.getContentId(uuid);

        // Check if content has changed
        if (!contentId.equals(contentIdInOS)) {
            // Fetch content
            String content = alfrescoSolrApiClient.executeGetRequest("textContent?nodeId=" + uuid);

            // Fetch ACL information
            AclStatus aclStatus;
            try {
                aclStatus = alfrescoContentApiClient.getNodeAcl(uuid);
            } catch (Exception e) {
                LOG.error("Error fetching ACL for node {}", uuid, e);
                // Create empty ACL status
                aclStatus = new AclStatus();
                aclStatus.setEntries(Collections.emptyList());
            }

            // Process ACL entries
            List<AclEntry> aclEntries = new ArrayList<>();
            List<String> readers = new ArrayList<>();

            // Add owner as a reader
            if (aclStatus.getOwner() != null) {
                readers.add(aclStatus.getOwner());
            }

            // Process ACL entries
            if (aclStatus.getEntries() != null) {
                for (AclStatus.AccessControlEntry entry : aclStatus.getEntries()) {
                    if (entry.isAllowPermissions()) {
                        String authority = entry.getAuthorityId();

                        for (String permission : entry.getPermissions()) {
                            aclEntries.add(AclEntry.builder()
                                .authority(authority)
                                .permission(permission)
                                .build());

                            // Add to readers if it has read permission
                            if (isReadPermission(permission)) {
                                readers.add(authority);
                            }
                        }
                    }
                }
            }

            // Always add EVERYONE group for now (can be refined later)
            if (!readers.contains("GROUP_EVERYONE")) {
                readers.add("GROUP_EVERYONE");
            }

            // Delete existing document if it exists
            indexer.deleteDocumentIfExists(uuid);

            // Index document segments with ACL information
            List<String> segments = splitIntoSegments(content);
            indexSegmentsWithAcl(uuid, Long.parseLong(uuid), contentId, name, segments, aclEntries, readers, nodeRef);

            LOG.debug("Indexed: {} - {} - {}", uuid, contentId, name);
        } else {
            LOG.debug("Un-indexed: ContentId for node {} has not changed {}", uuid, contentId);
        }
    } catch (Exception e) {
        LOG.error("Error processing node", e);
    }
}

private boolean isReadPermission(String permission) {
    return "Read".equals(permission) || "Consumer".equals(permission) || 
           "Contributor".equals(permission) || "Collaborator".equals(permission) || 
           "Coordinator".equals(permission);
}

private void indexSegmentsWithAcl(String documentId, Long dbid, String contentId, String documentName, 
                                 List<String> segments, List<AclEntry> acl, List<String> readers, String nodeRef) {
    LOG.debug("Indexing {} document parts for {} - {} - {} - {}", segments.size(), dbid, contentId, documentId, documentName);
    IntStream.range(0, segments.size())
            .parallel()
            .forEach(i -> {
                String segmentId = documentId + "_" + i;
                indexer.index(segmentId, dbid, contentId, documentName, segments.get(i), acl, readers, nodeRef);
            });
}
}
