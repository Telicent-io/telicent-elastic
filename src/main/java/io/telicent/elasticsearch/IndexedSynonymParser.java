package io.telicent.elasticsearch;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.discovery.zen.MasterFaultDetection.ThisIsNotTheMasterYouAreLookingForException;

import com.fasterxml.jackson.databind.node.ObjectNode;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/** Generates a synonym map from the content of an index **/

public class IndexedSynonymParser extends SolrSynonymParser {

	private final boolean lenient;

	private final String index;
	private final String field;

	private static final Logger logger = LogManager.getLogger(IndexedSynonymParser.class);

	public IndexedSynonymParser(String index, String field, boolean expand, boolean dedup, boolean lenient,
			Analyzer analyzer) {
		super(dedup, expand, analyzer);
		this.lenient = lenient;
		this.index = index;
		this.field = field;
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

	@Override
	public void parse(Reader in) throws IOException, ParseException {
		// create a one-off client
		// TODO make port configurable etc...
		final RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();

		// Create the transport with a Jackson mapper
		final ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

		// And create the API client
		final ElasticsearchClient client = new ElasticsearchClient(transport);

		// get all the documents from the index
		// assuming there are only a handful of documents
		SearchResponse<ObjectNode> response = client.search(s -> s.index(index), ObjectNode.class);

		List<Hit<ObjectNode>> hits = response.hits().hits();
		for (Hit<ObjectNode> hit : hits) {
			String fieldValue = null;
			JsonData data = hit.fields().get(this.field);
			if (data == null) {
				fieldValue = hit.source().get(field).asText();
			} else {
				fieldValue = data.toString();
			}
			if (fieldValue == null)
				continue;

			// populate the map
			super.parse(new StringReader(fieldValue));
		}

		// close the index
		client.shutdown();
	}

}
