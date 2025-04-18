package org.alfresco.rest;

/**
 * Bean class representing a document in search results.
 */
public class DocumentBean {
    private String uuid;
    private String name;
    private String text;
    private String nodeRef;

    public DocumentBean() {
    }

    public DocumentBean(String uuid, String name, String text, String nodeRef) {
        this.uuid = uuid;
        this.name = name;
        this.text = text;
        this.nodeRef = nodeRef;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getNodeRef() {
        return nodeRef;
    }

    public void setNodeRef(String nodeRef) {
        this.nodeRef = nodeRef;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String uuid;
        private String name;
        private String text;
        private String nodeRef;

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder nodeRef(String nodeRef) {
            this.nodeRef = nodeRef;
            return this;
        }

        public DocumentBean build() {
            return new DocumentBean(uuid, name, text, nodeRef);
        }
    }
}