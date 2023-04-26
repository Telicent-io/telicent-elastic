FROM docker.elastic.co/elasticsearch/elasticsearch:8.6.2
COPY target/releases/SynonymsPlugin-8.6.2.1.zip /tmp
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:///tmp/SynonymsPlugin-8.6.2.1.zip
