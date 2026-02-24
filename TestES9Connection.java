import java.io.IOException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;

public class TestES9Connection {
    public static void main(String[] args) {
        try {
            // Create credentials provider
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY, 
                new UsernamePasswordCredentials("elastic", "OntologyHub2026")
            );

            // Create REST client
            RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200))
                .setHttpClientConfigCallback(httpClientBuilder -> 
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();

            // Create transport and client
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchClient client = new ElasticsearchClient(transport);

            // Test index exists
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index("lov")));
            System.out.println("Index 'lov' exists: " + exists);

            if (!exists) {
                // Create index with settings
                CreateIndexRequest createRequest = CreateIndexRequest.of(c -> 
                    c.index("lov")
                     .withJson(new java.io.StringReader("{\"settings\":{\"index\":{\"max_ngram_diff\":50}}}"))
                );
                
                boolean acknowledged = client.indices().create(createRequest).acknowledged();
                System.out.println("Index creation acknowledged: " + acknowledged);
            }

            // Close client
            transport.close();
            restClient.close();
            
            System.out.println("Test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}