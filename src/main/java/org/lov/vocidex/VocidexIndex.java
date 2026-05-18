package org.lov.vocidex;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A connection to a specific named index on an ElasticSearch cluster
 *
 * @author Richard Cyganiak
 */
public class VocidexIndex implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(VocidexIndex.class);

    private final String hostName;
    private final String indexName;
    private final String userName;
    private final String password;
    /**
     * Optional directory with {@code settings.json} and mapping JSON files. When null or empty,
     * {@link #create()} loads from classpath {@code mappings/} (packaged resources).
     */
    private final String mappingsBaseDir;
    private ElasticsearchClient client = null;
    private RestClient restClient = null;

    public VocidexIndex(String clusterName, String hostName, String indexName) {
        this(clusterName, hostName, indexName, "elastic", "OntologyHub2026", null);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String password) {
        this(clusterName, hostName, indexName, "elastic", password, null);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String userName, String password) {
        this(clusterName, hostName, indexName, userName, password, null);
    }

    public VocidexIndex(String clusterName, String hostName, String indexName, String userName, String password,
                        String mappingsBaseDir) {
        this.hostName = hostName;
        this.indexName = indexName;
        this.userName = userName != null ? userName : "elastic";
        this.password = password != null ? password : "";
        this.mappingsBaseDir = mappingsBaseDir != null ? mappingsBaseDir.trim() : null;
    }

    /**
     * Connects to the cluster if not yet connected. Is called implicitly by
     * all operations that require a connection.
     */
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
        return client.indices().exists(exists -> exists.index(indexName)).value();
    }

    public void delete() throws IOException {
        connect();
        client.indices().delete(delete -> delete.index(indexName));
    }

    public boolean create() throws IOException {
        connect();

        final String[] mappingNames = {
                "class", "property", "datatype", "instance", "vocabulary",
                "person", "organization", "individual"
        };

        if (mappingsBaseDir != null && !mappingsBaseDir.isEmpty()) {
            Path base = Paths.get(mappingsBaseDir);
            if (!Files.isDirectory(base)) {
                throw new IOException("ELASTICSEARCH_MAPPINGS_PATH is not a directory: " + base.toAbsolutePath());
            }
            Path settingsFile = base.resolve("settings.json");
            if (!Files.isRegularFile(settingsFile)) {
                throw new IOException("Missing settings.json under mappings path: " + settingsFile.toAbsolutePath());
            }
            String settings = JSONHelper.readFileFromFilesystem(settingsFile.toString());
            client.indices().create(create ->
                    create.index(indexName).withJson(new java.io.StringReader(settings)));
            log.info("Index created with settings from {}", settingsFile.toAbsolutePath());

            for (String mapping : mappingNames) {
                Path mappingFile = base.resolve(mapping + ".json");
                if (!Files.isRegularFile(mappingFile)) {
                    throw new IOException("Missing mapping file: " + mappingFile.toAbsolutePath());
                }
                String json = JSONHelper.readFileFromFilesystem(mappingFile.toString());
                if (!putMappingJson(json)) {
                    return false;
                }
                log.info("Mapping applied for: {}", mapping);
            }
            return true;
        }

        // Default: packaged resources under classpath mappings/
        String settings = JSONHelper.readFile("mappings/settings.json");
        client.indices().create(create ->
                create.index(indexName).withJson(new java.io.StringReader(settings)));
        log.info("Index created with classpath resource mappings/settings.json");

        for (String mapping : mappingNames) {
            String resourcePath = "mappings/" + mapping + ".json";
            String json = JSONHelper.readFile(resourcePath);
            if (!putMappingJson(json)) {
                return false;
            }
            log.info("Mapping applied for: {} ({})", mapping, resourcePath);
        }
        return true;
    }

    private boolean putMappingJson(String json) {
        try {
            connect();
            client.indices().putMapping(put ->
                    put.index(indexName).withJson(new java.io.StringReader(json)));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Applies a mapping from a filesystem path (e.g. when using ELASTICSEARCH_MAPPINGS_PATH).
     */
    public boolean setMapping(String type, String jsonConfigFile) {
        try {
            connect();
            String json = JSONHelper.readFileFromFilesystem(jsonConfigFile);
            client.indices().putMapping(put ->
                    put.index(indexName).withJson(new java.io.StringReader(json)));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Adds a document (that is, a JSON structure) to the index.
     *
     * @return The document's id
     */
    public String addDocument(VocidexDocument document) throws IOException {
        connect();
        return client.index(idx ->
                idx.index(indexName)
                        .id(document.getId())
                        .withJson(new java.io.StringReader(document.getJSONContents()))
        ).id();
    }
}
