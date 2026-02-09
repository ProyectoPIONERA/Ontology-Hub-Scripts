package org.lov.vocidex;

import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;

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


/**
 * A connection to a specific named index on an ElasticSearch cluster
 * 
 * @author Richard Cyganiak
 */
public class VocidexIndex implements Closeable {
	private final String hostName;
	private final String indexName;
	private final String password;
	private ElasticsearchClient client = null;
	private RestClient restClient = null;
	
	public VocidexIndex(String clusterName, String hostName, String indexName) {
		this.hostName = hostName;
		this.indexName = indexName;
		this.password = "OntologyHub2026"; // Default password from configuration
	}
	
	public VocidexIndex(String clusterName, String hostName, String indexName, String password) {
		this.hostName = hostName;
		this.indexName = indexName;
		this.password = password;
	}

	/**
	 * Connects to the cluster if not yet connected. Is called implicitly by
	 * all operations that require a connection. 
	 * @throws IOException 
	 */
	public void connect() throws IOException {
		if (client != null) return;
		
		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, 
			new UsernamePasswordCredentials("elastic", password));

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
		
		// Read settings from the new mappings location
		String settings = JSONHelper.readFile("/Users/alexel200/Downloads/Pionera/Ontology-Hub/app/elastic/mappings/settings.json");
		
		// Create index with specific settings
		client.indices().create(create -> 
			create.index(indexName)
				.withJson(new java.io.StringReader(settings))
		);
		
		System.out.println("Index created with settings");
		
		// Apply mappings using new paths
		String[] mappings = {
			"class", "property", "datatype", "instance", "vocabulary", 
			"person", "organization", "individual"
		};
		
		for (String mapping : mappings) {
			String mappingPath = "/Users/alexel200/Downloads/Pionera/Ontology-Hub/app/elastic/mappings/" + mapping + ".json";
			if (!setMapping(mapping, mappingPath)) {
				return false;
			}
			System.out.println("Mapping applied for: " + mapping);
		}
		
		return true;
	}
	
	public boolean setMapping(String type, String jsonConfigFile) {
		try {
			connect();
			String json = JSONHelper.readFile(jsonConfigFile);
			client.indices().putMapping(put -> 
				put.index(indexName)
					.withJson(new java.io.StringReader(json))
			);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Adds a document (that is, a JSON structure) to the index.
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
