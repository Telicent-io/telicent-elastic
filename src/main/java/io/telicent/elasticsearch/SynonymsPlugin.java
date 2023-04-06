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

import static org.elasticsearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.Map;
import java.util.TreeMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ReloadablePlugin;

/** Main class for Telicent Synonym Plugin * */
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
        // nothing special required it seems
    }
}
