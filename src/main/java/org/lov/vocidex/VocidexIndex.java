package org.lov.vocidex;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Connection to Elasticsearch for LOV Vocidex indexing.
 * <p>
 * With {@link #perTypeIndices} (recommended for Elasticsearch 8/9), each category gets its own
 * index {@code <indexName>_<category>} (e.g. {@code lov_class}) with only that category's mapping.
 * Otherwise a single legacy index receives merged mappings.
 */
public class VocidexIndex implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(VocidexIndex.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Categories with a {@code <name>.json} mapping file under {@code mappings/}. */
    public static final String[] MAPPING_CATEGORIES = {
            "class", "property", "datatype", "instance", "vocabulary",
            "person", "organization", "individual"
    };

    private final String hostName;
    /** Index name, or prefix when {@link #perTypeIndices} is true (e.g. {@code lov}). */
    private final String indexName;
    private final String userName;
    private final String password;
    private final String mappingsBaseDir;
    private final boolean perTypeIndices;
    private ElasticsearchClient client = null;
    private RestClient restClient = null;

    public VocidexIndex(String clusterName, String hostName, String indexName) {
        this(clusterName, hostName, indexName, "elastic", "OntologyHub2026", null, false);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String password) {
        this(clusterName, hostName, indexName, "elastic", password, null, false);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String userName, String password) {
        this(clusterName, hostName, indexName, userName, password, null, false);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String userName, String password,
                        String mappingsBaseDir) {
        this(clusterName, hostName, indexName, userName, password, mappingsBaseDir, false);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String userName, String password,
                        String mappingsBaseDir, boolean perTypeIndices) {
        this.hostName = hostName;
        this.indexName = indexName;
        this.userName = userName != null ? userName : "elastic";
        this.password = password != null ? password : "";
        this.mappingsBaseDir = mappingsBaseDir != null ? mappingsBaseDir.trim() : null;
        this.perTypeIndices = perTypeIndices;
    }

    public boolean isPerTypeIndices() {
        return perTypeIndices;
    }

    /** Physical index for a document category or type string (e.g. {@code lov_class}). */
    public String physicalIndexName(String docType) {
        if (!perTypeIndices) {
            return indexName;
        }
        String t = docType == null ? "unknown" : docType.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9_-]", "");
        if (t.isEmpty()) {
            t = "unknown";
        }
        return indexName + "_" + t;
    }

    public void connect() throws IOException {
        if (client != null) {
            return;
        }

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(userName, password));

        RestClientBuilder builder = RestClient.builder(new HttpHost(hostName, 9200))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
    }

    public void close() {
        try {
            if (restClient != null) {
                restClient.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
        restClient = null;
        client = null;
    }

    public boolean exists() throws IOException {
        connect();
        if (!perTypeIndices) {
            return client.indices().exists(e -> e.index(indexName)).value();
        }
        List<String> names = new ArrayList<>();
        for (String cat : MAPPING_CATEGORIES) {
            names.add(physicalIndexName(cat));
        }
        return client.indices().exists(e -> e.index(names)).value();
    }

    public void delete() throws IOException {
        connect();
        if (!perTypeIndices) {
            client.indices().delete(d -> d.index(indexName));
            return;
        }
        for (String cat : MAPPING_CATEGORIES) {
            String idx = physicalIndexName(cat);
            if (client.indices().exists(e -> e.index(idx)).value()) {
                client.indices().delete(d -> d.index(idx));
            }
        }
    }

    private void createIndexWithRawSettingsJson(String settingsBody, String targetIndex) throws IOException {
        connect();
        String normalizedSettings = normalizeSettingsBodyForEs8(settingsBody);
        Request request = new Request("PUT", "/" + targetIndex.replace("/", "%2F"));
        request.setJsonEntity(normalizedSettings);
        restClient.performRequest(request);
    }

    /**
     * Adjusts index settings for Elasticsearch 8+ (e.g. Ontology-Hub UI may ship BM25 options removed in newer ES).
     */
    static String normalizeSettingsBodyForEs8(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isObject()) {
            return json;
        }
        stripUnsupportedBm25Settings(root);
        return MAPPER.writeValueAsString(root);
    }

    /** Recursively removes {@code discount_overlaps} (not supported on BM25 in ES 8+). */
    private static void stripUnsupportedBm25Settings(JsonNode n) {
        if (n == null || !n.isObject()) {
            return;
        }
        ObjectNode o = (ObjectNode) n;
        o.remove("discount_overlaps");
        List<String> keys = new ArrayList<>();
        o.fieldNames().forEachRemaining(keys::add);
        for (String k : keys) {
            stripUnsupportedBm25Settings(o.get(k));
        }
    }

    /**
     * Produces the JSON body for {@code PUT .../_mapping}: unwraps legacy wrappers and rewrites
     * Elasticsearch 1.x / 2.x constructs ({@code multi_field}, {@code string}) for ES 8+.
     */
    static String normalizeMappingBodyForEs8(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (!root.isObject()) {
            return json;
        }
        ObjectNode body = unwrapMappingBody((ObjectNode) root);
        if (body == null) {
            return json;
        }
        migrateLegacyFieldTypes(body);
        stripUnsupportedMappingParameters(body);
        return MAPPER.writeValueAsString(body);
    }

    /**
     * Removes mapping parameters rejected by Elasticsearch 8+ (e.g. field-level {@code boost},
     * obsolete {@code include_in_all}) from legacy / Ontology-Hub JSON.
     */
    private static void stripUnsupportedMappingParameters(ObjectNode body) {
        if (body.has("properties") && body.get("properties").isObject()) {
            stripFieldMappingsRecursive((ObjectNode) body.get("properties"));
        }
    }

    private static void stripFieldMappingsRecursive(ObjectNode propertiesBlock) {
        List<String> keys = new ArrayList<>();
        propertiesBlock.fieldNames().forEachRemaining(keys::add);
        for (String k : keys) {
            JsonNode v = propertiesBlock.get(k);
            if (v != null && v.isObject()) {
                stripOneFieldMapping((ObjectNode) v);
            }
        }
    }

    private static void stripOneFieldMapping(ObjectNode field) {
        field.remove("boost");
        field.remove("include_in_all");
        if (field.has("properties") && field.get("properties").isObject()) {
            stripFieldMappingsRecursive((ObjectNode) field.get("properties"));
        }
        if (field.has("fields") && field.get("fields").isObject()) {
            ObjectNode fields = (ObjectNode) field.get("fields");
            List<String> subKeys = new ArrayList<>();
            fields.fieldNames().forEachRemaining(subKeys::add);
            for (String name : subKeys) {
                JsonNode sub = fields.get(name);
                if (sub != null && sub.isObject()) {
                    stripOneFieldMapping((ObjectNode) sub);
                }
            }
        }
    }

    /** Returns the mapping root object (typically contains {@code properties}), or null. */
    private static ObjectNode unwrapMappingBody(ObjectNode root) {
        if (root.has("mappings") && root.get("mappings").isObject()) {
            JsonNode mappings = root.get("mappings");
            if (mappings.size() == 1) {
                Iterator<Map.Entry<String, JsonNode>> it = mappings.fields();
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode inner = e.getValue();
                if (inner != null && inner.isObject() && inner.has("properties")) {
                    return (ObjectNode) inner;
                }
            }
        }
        if (root.size() == 1) {
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode value = e.getValue();
            if (value != null && value.isObject() && value.has("properties") && !"properties".equals(key)) {
                return (ObjectNode) value;
            }
        }
        if (root.has("properties")) {
            return root;
        }
        return null;
    }

    private static void migrateLegacyFieldTypes(ObjectNode container) {
        JsonNode props = container.get("properties");
        if (props == null || !props.isObject()) {
            return;
        }
        ObjectNode propsObj = (ObjectNode) props;
        List<String> names = new ArrayList<>();
        propsObj.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            JsonNode field = propsObj.get(name);
            if (field == null || !field.isObject()) {
                continue;
            }
            ObjectNode fieldObj = (ObjectNode) field;
            if (isMisplacedAnalyzerVariantObject(fieldObj)) {
                propsObj.set(name, convertMisplacedPropertiesVariantsToTextFields(fieldObj));
                fieldObj = (ObjectNode) propsObj.get(name);
            } else if ("multi_field".equals(typeName(fieldObj))) {
                propsObj.set(name, convertMultiFieldMapping(name, fieldObj));
                fieldObj = (ObjectNode) propsObj.get(name);
            } else {
                convertLegacyStringField(fieldObj);
            }
            if (fieldObj.has("properties")) {
                migrateLegacyFieldTypes(fieldObj);
            }
            if (fieldObj.has("fields") && fieldObj.get("fields").isObject()) {
                migrateLegacyFieldsBlock((ObjectNode) fieldObj.get("fields"));
            }
        }
    }

    private static void migrateLegacyFieldsBlock(ObjectNode fieldsBlock) {
        List<String> keys = new ArrayList<>();
        fieldsBlock.fieldNames().forEachRemaining(keys::add);
        for (String k : keys) {
            JsonNode v = fieldsBlock.get(k);
            if (v == null || !v.isObject()) {
                continue;
            }
            ObjectNode fo = (ObjectNode) v;
            if (isMisplacedAnalyzerVariantObject(fo)) {
                fieldsBlock.set(k, convertMisplacedPropertiesVariantsToTextFields(fo));
                fo = (ObjectNode) fieldsBlock.get(k);
            } else if ("multi_field".equals(typeName(fo))) {
                fieldsBlock.set(k, convertMultiFieldMapping(k, fo));
                fo = (ObjectNode) fieldsBlock.get(k);
            } else {
                convertLegacyStringField(fo);
            }
            if (fo.has("properties")) {
                migrateLegacyFieldTypes(fo);
            }
            if (fo.has("fields") && fo.get("fields").isObject()) {
                migrateLegacyFieldsBlock((ObjectNode) fo.get("fields"));
            }
        }
    }

    private static String typeName(ObjectNode field) {
        JsonNode t = field.get("type");
        return t != null && t.isTextual() ? t.asText() : null;
    }

    /**
     * Detects Ontology-Hub / legacy exports where multifield variants were put under {@code properties}
     * (implicit {@code object} mapping) instead of {@code type: text} + {@code fields}, which breaks
     * flat string values from indexers.
     */
    private static boolean isMisplacedAnalyzerVariantObject(ObjectNode fieldObj) {
        if (!fieldObj.has("properties") || !fieldObj.get("properties").isObject()) {
            return false;
        }
        String explicit = typeName(fieldObj);
        if (explicit != null && !"object".equals(explicit)) {
            return false;
        }
        ObjectNode props = (ObjectNode) fieldObj.get("properties");
        if (props.size() == 0) {
            return false;
        }
        boolean variantKey = false;
        List<String> subKeys = new ArrayList<>();
        props.fieldNames().forEachRemaining(subKeys::add);
        for (String k : subKeys) {
            JsonNode sub = props.get(k);
            if (sub == null || !sub.isObject() || !sub.has("type")) {
                return false;
            }
            String st = sub.get("type").asText();
            if (!"text".equals(st) && !"keyword".equals(st)) {
                return false;
            }
            if ("ngram".equals(k) || "autocomplete".equals(k) || "raw".equals(k) || "sort".equals(k)) {
                variantKey = true;
            }
        }
        return variantKey;
    }

    private static ObjectNode convertMisplacedPropertiesVariantsToTextFields(ObjectNode fieldObj) {
        ObjectNode props = (ObjectNode) fieldObj.get("properties");
        ObjectNode out = MAPPER.createObjectNode();
        out.put("type", "text");
        ObjectNode fields = MAPPER.createObjectNode();
        props.fields().forEachRemaining(en -> {
            if (en.getValue().isObject()) {
                fields.set(en.getKey(), en.getValue().deepCopy());
            }
        });
        out.set("fields", fields);
        return out;
    }

    /**
     * Converts {@code multi_field} (removed in ES 5+) to {@code text} with {@code fields} for variants.
     */
    private static ObjectNode convertMultiFieldMapping(String fieldName, ObjectNode mf) {
        JsonNode fieldsNode = mf.get("fields");
        ObjectNode out;
        ObjectNode newFields = MAPPER.createObjectNode();
        if (fieldsNode == null || !fieldsNode.isObject()) {
            out = MAPPER.createObjectNode();
            out.put("type", "text");
            return out;
        }
        ObjectNode fieldsObj = (ObjectNode) fieldsNode;
        JsonNode primary = fieldsObj.get(fieldName);
        String primaryKey = fieldName;
        if (primary == null || !primary.isObject()) {
            primaryKey = null;
            Iterator<String> it = fieldsObj.fieldNames();
            while (it.hasNext()) {
                String k = it.next();
                JsonNode cand = fieldsObj.get(k);
                if (cand != null && cand.isObject()) {
                    primaryKey = k;
                    primary = cand;
                    break;
                }
            }
        }
        if (primary != null && primary.isObject()) {
            out = (ObjectNode) primary.deepCopy();
            convertLegacyStringField(out);
            if (!out.has("type")) {
                out.put("type", "text");
            }
        } else {
            out = MAPPER.createObjectNode();
            out.put("type", "text");
        }
        final String skipKey = primaryKey;
        fieldsObj.fields().forEachRemaining(en -> {
            if (skipKey != null && skipKey.equals(en.getKey())) {
                return;
            }
            if (en.getValue().isObject()) {
                ObjectNode sf = (ObjectNode) en.getValue().deepCopy();
                convertLegacyStringField(sf);
                newFields.set(en.getKey(), sf);
            }
        });
        if (newFields.size() > 0) {
            if (out.has("fields") && out.get("fields").isObject()) {
                ObjectNode existing = (ObjectNode) out.get("fields");
                newFields.fields().forEachRemaining(en -> existing.set(en.getKey(), en.getValue()));
            } else {
                out.set("fields", newFields);
            }
        }
        return out;
    }

    /** Maps legacy {@code string} type and {@code index: not_analyzed} to {@code text} / {@code keyword}. */
    private static void convertLegacyStringField(ObjectNode field) {
        JsonNode t = field.get("type");
        if (t == null || !t.isTextual()) {
            return;
        }
        if (!"string".equals(t.asText())) {
            return;
        }
        JsonNode index = field.get("index");
        boolean keyword = false;
        if (index != null) {
            if (index.isTextual()) {
                String iv = index.asText();
                keyword = "not_analyzed".equals(iv) || "no".equals(iv);
            } else if (index.isBoolean()) {
                keyword = !index.booleanValue();
            }
        }
        field.put("type", keyword ? "keyword" : "text");
        field.remove("index");
    }

    private void putMappingWithRawJson(String mappingBody, String targetIndex) throws IOException {
        connect();
        String normalized = normalizeMappingBodyForEs8(mappingBody);
        Request request = new Request("PUT", "/" + targetIndex.replace("/", "%2F") + "/_mapping");
        request.setJsonEntity(normalized);
        restClient.performRequest(request);
    }

    public boolean create() throws IOException {
        connect();

        if (mappingsBaseDir != null && !mappingsBaseDir.isEmpty()) {
            return createFromFilesystem(Paths.get(mappingsBaseDir));
        }
        return createFromClasspath();
    }

    private boolean createFromClasspath() throws IOException {
        String settings = JSONHelper.readFile("mappings/settings.json");
        if (perTypeIndices) {
            for (String cat : MAPPING_CATEGORIES) {
                String idx = physicalIndexName(cat);
                createIndexWithRawSettingsJson(settings, idx);
                String json = JSONHelper.readFile("mappings/" + cat + ".json");
                putMappingWithRawJson(json, idx);
                log.info("Created index {} with mapping {}", idx, cat);
            }
            return true;
        }
        createIndexWithRawSettingsJson(settings, indexName);
        log.info("Index created with classpath resource mappings/settings.json");
        for (String cat : MAPPING_CATEGORIES) {
            String json = JSONHelper.readFile("mappings/" + cat + ".json");
            if (!putMappingJson(json, indexName)) {
                return false;
            }
            log.info("Mapping applied for: {}", cat);
        }
        return true;
    }

    private boolean createFromFilesystem(Path base) throws IOException {
        if (!Files.isDirectory(base)) {
            throw new IOException("ELASTICSEARCH_MAPPINGS_PATH is not a directory: " + base.toAbsolutePath());
        }
        Path settingsFile = base.resolve("settings.json");
        if (!Files.isRegularFile(settingsFile)) {
            throw new IOException("Missing settings.json under mappings path: " + settingsFile.toAbsolutePath());
        }
        String settings = JSONHelper.readFileFromFilesystem(settingsFile.toString());
        if (perTypeIndices) {
            for (String cat : MAPPING_CATEGORIES) {
                Path mappingFile = base.resolve(cat + ".json");
                if (!Files.isRegularFile(mappingFile)) {
                    throw new IOException("Missing mapping file: " + mappingFile.toAbsolutePath());
                }
                String idx = physicalIndexName(cat);
                createIndexWithRawSettingsJson(settings, idx);
                String json = JSONHelper.readFileFromFilesystem(mappingFile.toString());
                putMappingWithRawJson(json, idx);
                log.info("Created index {} with mapping from {}", idx, mappingFile.getFileName());
            }
            return true;
        }
        createIndexWithRawSettingsJson(settings, indexName);
        log.info("Index created with settings from {}", settingsFile.toAbsolutePath());
        for (String cat : MAPPING_CATEGORIES) {
            Path mappingFile = base.resolve(cat + ".json");
            if (!Files.isRegularFile(mappingFile)) {
                throw new IOException("Missing mapping file: " + mappingFile.toAbsolutePath());
            }
            String json = JSONHelper.readFileFromFilesystem(mappingFile.toString());
            if (!putMappingJson(json, indexName)) {
                return false;
            }
            log.info("Mapping applied for: {}", cat);
        }
        return true;
    }

    private boolean putMappingJson(String json, String targetIndex) {
        try {
            connect();
            putMappingWithRawJson(json, targetIndex);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setMapping(String type, String jsonConfigFile) {
        try {
            connect();
            String json = JSONHelper.readFileFromFilesystem(jsonConfigFile);
            putMappingWithRawJson(json, physicalIndexName(type));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String addDocument(VocidexDocument document) throws IOException {
        connect();
        String idx = physicalIndexName(document.getType());
        String id = document.getId();
        if (id == null || id.isEmpty()) {
            throw new IOException("Document id (URI) is empty for type " + document.getType());
        }
        // Use RestClient with a single encoded path segment: URIs contain '/' which break
        // high-level client paths, yielding PUT /index/_doc/ and HTTP 405 on Elasticsearch 8+.
        String encIndex = idx.replace("/", "%2F");
        String encId;
        try {
            encId = URLEncoder.encode(id, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 not available for URL encoding", e);
        }
        Request request = new Request("PUT", "/" + encIndex + "/_doc/" + encId);
        request.setJsonEntity(document.getJSONContents());
        restClient.performRequest(request);
        return id;
    }
}
