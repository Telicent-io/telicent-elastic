package io.telicent.elasticsearch;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.function.Function;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.Analysis;
import org.elasticsearch.index.analysis.AnalysisMode;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;

public class SynonymGraphTokenFilterFactory extends AbstractTokenFilterFactory {

	private final String format;
	private final boolean expand;
	private final boolean lenient;
	protected final Settings settings;
	protected final Environment environment;
	protected final AnalysisMode analysisMode = AnalysisMode.SEARCH_TIME;

	SynonymGraphTokenFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		this.settings = settings;
		this.expand = settings.getAsBoolean("expand", true);
		this.lenient = settings.getAsBoolean("lenient", false);
		this.format = settings.get("format", "");
		this.environment = env;
	}

	@Override
	public AnalysisMode getAnalysisMode() {
		return this.analysisMode;
	}

	@Override
	public TokenStream create(TokenStream tokenStream) {
		throw new IllegalStateException(
				"Call createPerAnalyzerSynonymFactory to specialize this factory for an analysis chain first");
	}

	@Override
	public TokenFilterFactory getChainAwareTokenFilterFactory(TokenizerFactory tokenizer,
			List<CharFilterFactory> charFilters, List<TokenFilterFactory> previousTokenFilters,
			Function<String, TokenFilterFactory> allFilters) {
		final Analyzer analyzer = buildSynonymAnalyzer(tokenizer, charFilters, previousTokenFilters, allFilters);
		final SynonymMap synonyms = buildSynonyms(analyzer, getRulesFromSettings(environment));
		final String name = name();
		return new TokenFilterFactory() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public TokenStream create(TokenStream tokenStream) {
				return synonyms.fst == null ? tokenStream : new SynonymGraphFilter(tokenStream, synonyms, false);
			}

			@Override
			public AnalysisMode getAnalysisMode() {
				return analysisMode;
			}
		};
	}

	SynonymMap buildSynonyms(Analyzer analyzer, Reader rules) {
		try {
			SynonymMap.Builder parser;
			if ("wordnet".equalsIgnoreCase(format)) {
				parser = new ESWordnetSynonymParser(true, expand, lenient, analyzer);
				((ESWordnetSynonymParser) parser).parse(rules);
			} else {
				parser = new ESSolrSynonymParser(true, expand, lenient, analyzer);
				((ESSolrSynonymParser) parser).parse(rules);
			}
			return parser.build();
		} catch (Exception e) {
			throw new IllegalArgumentException("failed to build synonyms", e);
		}
	}

	Analyzer buildSynonymAnalyzer(TokenizerFactory tokenizer, List<CharFilterFactory> charFilters,
			List<TokenFilterFactory> tokenFilters, Function<String, TokenFilterFactory> allFilters) {
		return new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
				tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));
	}

	Reader getRulesFromSettings(Environment env) {
		Reader rulesReader;
		if (settings.getAsList("synonyms", null) != null) {
			List<String> rulesList = Analysis.getWordList(env, settings, "synonyms");
			StringBuilder sb = new StringBuilder();
			for (String line : rulesList) {
				sb.append(line).append(System.lineSeparator());
			}
			rulesReader = new StringReader(sb.toString());
		} else if (settings.get("synonyms_path") != null) {
			rulesReader = Analysis.getReaderFromFile(env, settings, "synonyms_path");
		} else {
			throw new IllegalArgumentException(
					"synonym requires either `synonyms` or `synonyms_path` to be configured");
		}
		return rulesReader;
	}

}