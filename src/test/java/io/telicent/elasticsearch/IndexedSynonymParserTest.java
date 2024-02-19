/**
 * Copyright 2023 Telicent
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.telicent.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/** Test class for IndexedSynonymParser * */
public class IndexedSynonymParserTest {

    @Rule public Timeout globalTimeout = Timeout.seconds(120);

    private ElasticsearchContainer container;

    private static final Logger LOG = LoggerFactory.getLogger(IndexedSynonymParserTest.class);

    private static final String INDEXNAME = ".synonyms";

    private static final String PASSWORD = "THISISAPASSWORD";
    private static final String USERNAME = "elastic";

    private void setup(boolean authenticate) throws ElasticsearchException, IOException {

        String version = System.getProperty("elasticsearch.version");
        if (version == null) version = "7.17.18";
        LOG.info("Starting docker instance of Elasticsearch {}...", version);

        container =
                new ElasticsearchContainer(
                        "docker.elastic.co/elasticsearch/elasticsearch:" + version);
        if (authenticate) {
            container.withPassword(PASSWORD);
        }
        container.start();

        LOG.info("Elasticsearch container started at {}", container.getHttpHostAddress());

        indexSynonyms(authenticate);
    }

    private void indexSynonyms(boolean authenticate) throws ElasticsearchException, IOException {
        RestClientBuilder builder =
                RestClient.builder(
                        new HttpHost(container.getHost(), container.getFirstMappedPort()));

        if (authenticate) {
            BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
            credsProv.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));
            builder.setHttpClientConfigCallback(
                    new HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(
                                HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder.setDefaultCredentialsProvider(credsProv);
                        }
                    });
        }

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport =
                new RestClientTransport(builder.build(), new JacksonJsonpMapper());

        // And create the API client
        ElasticsearchClient client = new ElasticsearchClient(transport);

        // Explicit mapping
        InputStream mapstream =
                getClass().getClassLoader().getResourceAsStream("synonyms-mappings.json");
        client.indices().create(t -> t.index(INDEXNAME).withJson(mapstream));

        InputStream input = getClass().getClassLoader().getResourceAsStream("synonyms.json");

        IndexRequest<JsonData> request =
                IndexRequest.of(i -> i.index(INDEXNAME).withJson(input).refresh(Refresh.True));

        client.index(request);

        client.shutdown();
    }

    private void testAndClose(boolean auth, String user, String password, int expected)
            throws Exception {
        setup(auth);
        final StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        IndexedSynonymParser parser =
                new IndexedSynonymParser(
                        container.getHost(),
                        container.getFirstMappedPort().intValue(),
                        user,
                        password,
                        INDEXNAME,
                        true,
                        true,
                        true,
                        standardAnalyzer);
        parser.parse();
        SynonymMap synonyms = parser.build();
        Assert.assertEquals(expected, synonyms.words.size());
        LOG.info("Closing ES container");
        container.close();
    }

    @Test
    /**
     * Checks that the number of entries is correct in the synonym map when loading from an index.
     * Passing credentials even when authentication is not required
     */
    public void loadSynonymsNoAuth() throws Exception {
        testAndClose(false, USERNAME, PASSWORD, 7);
    }

    @Test
    /**
     * Checks that the number of entries is correct in the synonym map when loading from an index.
     * Passing credentials with authentication required
     */
    public void loadSynonymsAuth() throws Exception {
        testAndClose(true, USERNAME, PASSWORD, 7);
    }

    @Test
    /** Missing credentials with authentication required */
    public void loadSynonymsMissingAuth() throws Exception {
        testAndClose(true, null, null, 0);
    }
}
