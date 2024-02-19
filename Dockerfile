ARG ELASTICSEARCH_VERSION
FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}
ARG PLUGIN_VERSION
COPY target/releases/SynonymsPlugin-${PLUGIN_VERSION}.zip /tmp
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:///tmp/SynonymsPlugin-${PLUGIN_VERSION}.zip
