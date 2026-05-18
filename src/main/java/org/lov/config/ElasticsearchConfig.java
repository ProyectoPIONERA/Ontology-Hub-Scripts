package org.lov.config;

import java.util.Locale;
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
    /**
     * Optional directory with {@code settings.json} and per-type {@code class.json}, {@code property.json}, …
     * When set, create-index / index-lov use these files instead of packaged classpath mappings — use the same
     * directory as the Ontology-Hub UI ({@code elastic/mappings}) so analyzers and field definitions stay aligned.
     */
    public final String mappingsPath;
    /**
     * When true (recommended for Elasticsearch 8+), documents are stored in separate indices
     * {@code <indexName>_<type>} (e.g. {@code lov_class}, {@code lov_vocabulary}) using each
     * {@code <type>.json} mapping only on that index. When false, legacy single index with merged mappings.
     */
    public final boolean perTypeIndices;
    /**
     * When true, {@code index-lov} logs a warning and continues if a single document fails to index
     * (HTTP 4xx/5xx from Elasticsearch). Default false: first failure aborts the run.
     */
    public final boolean indexContinueOnError;
    /**
     * When {@link #indexContinueOnError} is true and this is true, exit with failure if any document was skipped.
     * Default false: partial success still exits successfully (lenient).
     */
    public final boolean indexFailOnSkipped;

    private ElasticsearchConfig(String clusterName, String hostName, String indexName, String user,
                                String password, String mappingsPath, boolean perTypeIndices,
                                boolean indexContinueOnError, boolean indexFailOnSkipped) {
        this.clusterName = clusterName;
        this.hostName = hostName;
        this.indexName = indexName;
        this.user = user;
        this.password = password;
        this.mappingsPath = mappingsPath;
        this.perTypeIndices = perTypeIndices;
        this.indexContinueOnError = indexContinueOnError;
        this.indexFailOnSkipped = indexFailOnSkipped;
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
        boolean perType = parseBooleanProp(p, true,
                "ELASTICSEARCH_PER_TYPE_INDICES", "ELASTICSEARCH_INDEX_PER_TYPE", "ELASTIC_PER_TYPE_INDICES");
        boolean continueOnError = parseBooleanProp(p, false,
                "ELASTICSEARCH_INDEX_CONTINUE_ON_ERROR", "ELASTIC_INDEX_CONTINUE_ON_ERROR");
        boolean failOnSkipped = parseBooleanProp(p, false,
                "ELASTICSEARCH_INDEX_FAIL_ON_SKIPPED", "ELASTIC_INDEX_FAIL_ON_SKIPPED");
        return new ElasticsearchConfig(cluster, host, index, user, password, mappings, perType,
                continueOnError, failOnSkipped);
    }

    private static boolean parseBooleanProp(Properties p, boolean defaultValue, String... keys) {
        String v = firstNonBlank(p, keys);
        if (v == null) {
            return defaultValue;
        }
        v = v.trim().toLowerCase(Locale.ROOT);
        if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("off")) {
            return false;
        }
        if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on")) {
            return true;
        }
        return defaultValue;
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
