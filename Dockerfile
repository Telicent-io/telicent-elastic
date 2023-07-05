FROM docker.elastic.co/elasticsearch/elasticsearch:8.8.1
COPY target/releases/SynonymsPlugin-8.8.1.0.zip /tmp
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:///tmp/SynonymsPlugin-8.8.1.0.zip
