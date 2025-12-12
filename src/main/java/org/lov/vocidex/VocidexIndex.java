package org.lov.vocidex;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;


/**
 * A connection to a specific named index on an ElasticSearch cluster
 * 
 * @author Richard Cyganiak
 */
public class VocidexIndex implements Closeable {
	private final String clusterName;
	private final String hostName;
	private final String indexName;
	private TransportClient client = null;
	
	public VocidexIndex(String clusterName, String hostName, String indexName) {
		this.clusterName = clusterName;
		this.hostName = hostName;
		this.indexName = indexName;
	}

	/**
	 * Connects to the cluster if not yet connected. Is called implicitly by
	 * all operations that require a connection. 
	 * @throws UnknownHostException 
	 */
	public void connect() throws UnknownHostException {
		if (client != null) return;
		Settings settings = Settings.builder()
		        .put("cluster.name", clusterName).build();

		client = TransportClient.builder().settings(settings).build().addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(hostName), 9300));
	}
	
	public void close() {
		if (client == null) return;
		client.close();
		client = null;
	}
	
	public boolean exists() throws UnknownHostException {
		connect();
		return client.admin().indices().exists(Requests.indicesExistsRequest(indexName)).actionGet().isExists();
	}
	
	public void delete() throws UnknownHostException {
		connect();
		client.admin().indices().prepareDelete(indexName).execute();		
	}
	
	public boolean create() throws UnknownHostException {
		connect();
		//create index with specific settings
		if (!client.admin().indices().create(Requests.createIndexRequest(indexName).settings(JSONHelper.readFile("mappings/settings.json"))).actionGet().isAcknowledged()) {
			return false;
		}
		System.out.println("Ha entrado");
		if (!setMapping("class", "mappings/class.json")) return false;
		System.out.println("Ha entrado2");
		if (!setMapping("property", "mappings/property.json")) return false;
		System.out.println("Ha entrado3");
		if (!setMapping("datatype", "mappings/datatype.json")) return false;
		System.out.println("Ha entrado4");
		if (!setMapping("instance", "mappings/instance.json")) return false;
		System.out.println("Ha entrado5");
		if (!setMapping("vocabulary", "mappings/vocabulary.json")) return false;
		System.out.println("Ha entrado6");
		if (!setMapping("person", "mappings/person.json")) return false;
		System.out.println("Ha entrado7");
		if (!setMapping("organization", "mappings/organization.json")) return false;
		System.out.println("Ha entrado8");
        if (!setMapping("individual", "mappings/individual.json")) return false;
        System.out.println("Ha entrado9");
		
		return true;
	}
	
	public boolean setMapping(String type, String jsonConfigFile) {
		String json = JSONHelper.readFile(jsonConfigFile);
		if (!client.admin().indices().preparePutMapping().setIndices(indexName).setType(type).setSource(json).execute().actionGet().isAcknowledged()) {
			return false;
		}
		return true;
	}
	
	/**
	 * Adds a document (that is, a JSON structure) to the index.
	 * @return The document's id
	 */
	public String addDocument(VocidexDocument document) {
        return client
				.prepareIndex(indexName, document.getType(), document.getId())
				.setSource(document.getJSONContents())
				.execute().actionGet().getId();
	}
}
