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
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;

/** Generates a synonym map from the content of an index * */
public class IndexedSynonymParser extends SolrSynonymParser {

    private final boolean lenient;

    private final String index;
    private final String host;
    private final int port;

    private final String username;
    private final String password;

    private static final Logger logger = LogManager.getLogger(IndexedSynonymParser.class);

    public IndexedSynonymParser(
            String host,
            int port,
            String username,
            String password,
            String index,
            boolean expand,
            boolean dedup,
            boolean lenient,
            Analyzer analyzer) {
        super(dedup, expand, analyzer);
        this.lenient = lenient;
        this.index = index;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void add(CharsRef input, CharsRef output, boolean includeOrig) {
        // This condition follows up on the overridden analyze method. In case lenient
        // was set to true and there was an
        // exception during super.analyze we return a zero-length CharsRef for that word
        // which caused an exception. When
        // the synonym mappings for the words are added using the add method we skip the
        // ones that were left empty by
        // analyze i.e., in the case when lenient is set we only add those combinations
        // which are non-zero-length. The
        // else would happen only in the case when the input or output is empty and
        // lenient is set, in which case we
        // quietly ignore it. For more details on the control-flow see
        // SolrSynonymParser::addInternal.
        if (lenient == false || (input.length > 0 && output.length > 0)) {
            super.add(input, output, includeOrig);
        }
    }

    @Override
    public CharsRef analyze(String text, CharsRefBuilder reuse) throws IOException {
        try {
            return super.analyze(text, reuse);
        } catch (IllegalArgumentException ex) {
            if (lenient) {
                logger.info("Synonym rule for [" + text + "] was ignored");
                return new CharsRef("");
            } else {
                throw ex;
            }
        }
    }

    public void parse() throws IOException, ParseException {
        // create a one-off client
        final RestClientBuilder builder = RestClient.builder(new HttpHost(this.host, this.port));

        // needs a least a password
        if (password != null && !password.isBlank()) {
            BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
            credsProv.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(this.username, this.password));

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
        final ElasticsearchTransport transport =
                new RestClientTransport(builder.build(), new JacksonJsonpMapper());

        // And create the API client
        final ElasticsearchClient client = new ElasticsearchClient(transport);

        final boolean indexExists = client.indices().exists(e -> e.index(index)).value();
        if (!indexExists) {
            // just leave a message to indicate that the index does not exist
            // but don't crash everything just for that
            logger.error("Could not find index for synonyms {}", index);
            client.shutdown();
            return;
        }

        // get all the documents from the index
        // assuming there are only a handful of documents
        try {
            int synonymsLoaded = 0;

            SearchResponse<ObjectNode> response =
                    client.search(s -> s.index(index), ObjectNode.class);

            List<Hit<ObjectNode>> hits = response.hits().hits();
            for (Hit<ObjectNode> hit : hits) {
                // get the data from the source field
                Iterator<Entry<String, JsonNode>> fieldsIter = hit.source().fields();
                while (fieldsIter.hasNext()) {
                    Entry<String, JsonNode> node = fieldsIter.next();
                    if (node.getValue().isArray()) {
                        Iterator<JsonNode> iter = ((ArrayNode) node.getValue()).iterator();
                        while (iter.hasNext()) {
                            super.parse(new StringReader(iter.next().asText()));
                            synonymsLoaded++;
                        }
                    } else {
                        super.parse(new StringReader(node.getValue().asText()));
                        synonymsLoaded++;
                    }
                }
            }

            logger.info("{} synonyms loaded from index {}", synonymsLoaded, index);

        } catch (ElasticsearchException e) {
            logger.error("Exception caught when loading the synonyms from {}", index);
        } finally {
            client.shutdown();
        }
    }
}
