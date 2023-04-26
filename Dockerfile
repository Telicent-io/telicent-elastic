FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.5
COPY target/releases/SynonymsPlugin-7.17.5.1.zip /tmp
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:///tmp/SynonymsPlugin-7.17.5.1.zip
