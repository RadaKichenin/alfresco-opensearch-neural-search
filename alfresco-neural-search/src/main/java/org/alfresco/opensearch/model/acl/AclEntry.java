package org.alfresco.opensearch.model.acl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing an ACL entry for indexing in OpenSearch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclEntry {
    private String authority;
    private String permission;
}