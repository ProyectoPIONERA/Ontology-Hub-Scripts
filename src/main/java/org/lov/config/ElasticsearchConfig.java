package org.lov.config;

import java.util.Properties;

/**
 * Reads Elasticsearch connection settings from {@link Properties}, supporting
 * both {@code ELASTICSEARCH_*} and {@code ELASTIC_SEARCH_*} key styles.
 */
public final class ElasticsearchConfig {

    public final String clusterName;
    public final String hostName;
    public final String indexName;
    public final String user;
    public final String password;
    /** Absolute path to directory with settings.json and type mapping JSON files (used by create-index). */
    public final String mappingsPath;

    private ElasticsearchConfig(String clusterName, String hostName, String indexName, String user,
                                String password, String mappingsPath) {
        this.clusterName = clusterName;
        this.hostName = hostName;
        this.indexName = indexName;
        this.user = user;
        this.password = password;
        this.mappingsPath = mappingsPath;
    }

    public static ElasticsearchConfig fromProperties(Properties p) {
        String host = firstNonBlank(p, "ELASTICSEARCH_HOST", "ELASTIC_SEARCH_HOST");
        String cluster = firstNonBlank(p, "ELASTICSEARCH_CLUSTER", "ELASTIC_SEARCH_CLUSTER");
        String index = firstNonBlank(p, "ELASTICSEARCH_INDEX_NAME", "ELASTIC_SEARCH_INDEX_NAME");
        String user = firstNonBlank(p, "ELASTIC_SEARCH_USER", "ELASTICSEARCH_USER");
        if (user == null) {
            user = "elastic";
        }
        String password = firstNonBlank(p, "ELASTIC_SEARCH_PASSWORD", "ELASTICSEARCH_PASSWORD");
        if (password == null) {
            password = "OntologyHub2026";
        }
        String mappings = firstNonBlank(p, "ELASTICSEARCH_MAPPINGS_PATH", "ELASTIC_MAPPINGS_PATH");
        return new ElasticsearchConfig(cluster, host, index, user, password, mappings);
    }

    private static String firstNonBlank(Properties p, String... keys) {
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String v = p.getProperty(key);
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return null;
    }
}
