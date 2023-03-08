package io.telicent.elasticsearch;

import static org.elasticsearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.Map;
import java.util.TreeMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ReloadablePlugin;

public class SynonymsPlugin extends Plugin implements AnalysisPlugin, ReloadablePlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        final Map<String, AnalysisProvider<TokenFilterFactory>> filters = new TreeMap<>();
        filters.put(
                "index_synonym_graph",
                requiresAnalysisSettings(SynonymGraphTokenFilterFactory::new));
        return filters;
    }

    @Override
    public void reload(Settings settings) throws Exception {
        // TODO Auto-generated method stub
    }
}
