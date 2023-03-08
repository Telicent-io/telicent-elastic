package io.telicent.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import org.apache.http.HttpHost;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class IndexedSynonymParserTest {

    @Rule public Timeout globalTimeout = Timeout.seconds(120);

    private ElasticsearchContainer container;

    private static final Logger LOG = LoggerFactory.getLogger(IndexedSynonymParserTest.class);

    private static final String INDEXNAME = "synonyms";

    @Before
    public void setup() throws ElasticsearchException, IOException {

        String version = System.getProperty("elasticsearch-version");
        if (version == null) version = "7.17.5";
        LOG.info("Starting docker instance of Elasticsearch {}...", version);

        container =
                new ElasticsearchContainer(
                        "docker.elastic.co/elasticsearch/elasticsearch:" + version);
        container.start();
        LOG.info("Elasticsearch container started at {}", container.getHttpHostAddress());

        indexSynonyms();
    }

    private void indexSynonyms() throws ElasticsearchException, IOException {

        RestClient restClient =
                RestClient.builder(
                                new HttpHost(container.getHost(), container.getFirstMappedPort()))
                        .build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());

        // And create the API client
        ElasticsearchClient client = new ElasticsearchClient(transport);

        InputStream input = getClass().getClassLoader().getResourceAsStream("synonyms.json");

        IndexRequest<JsonData> request = IndexRequest.of(i -> i.index(INDEXNAME).withJson(input));

        client.index(request);

        client.shutdown();
    }

    @After
    public void close() {
        LOG.info("Closing ES container");
        container.close();
    }

    @Test
    public void loadSynonyms() throws IOException, ParseException {
        String fieldName = "synonyms";
        final StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        IndexedSynonymParser parser =
                new IndexedSynonymParser(
                        container.getHost(),
                        container.getFirstMappedPort().intValue(),
                        INDEXNAME,
                        fieldName,
                        true,
                        true,
                        true,
                        standardAnalyzer);
        parser.parse();
        SynonymMap synonyms = parser.build();
    }
}