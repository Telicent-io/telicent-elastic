# Telicent ElasticSearch Plugin

## Overview

This plugin provides an alternative implementation of the [SynonymGraphTokenFilter](https://www.elastic.co/guide/en/elasticsearch/reference/7.17/analysis-synonym-graph-tokenfilter.html) for Elasticsearch.

Instead of storing the synonyms in a file, this implementation loads it from an Elasticsearch index, which makes it easier to update especially when Elasticsearch runs in a sandboxed environment.

The main branch of this repository is for Elasticsearch 7.x, a separate branch is for 8.x.

A Docker compose file in _src/test/resources_ helps create instances of Elasticsearch and Kibana with the plugin pre-installed. It relies on an environment variable _BUILD_DIRECTORY_ e.g. 

```
export BUILD_DIRECTORY=./target
```

This project is licensed under ASF licence v2, see [LICENSE](LICENSE). All contributions are welcome and should be under ASF licence v2, see [CONTRIBUTING](CONTRIBUTING.md) on how to proceed. 

### Issues/Questions

Please file an [issue](https://github.com/Telicent-io/telicent-elastic/issues "issue").

## Installation

Compile the code with `mvn clean package`, you should find the plugin in _target/releases_.

You can then install it in Elasticsearch with

```
$ES_HOME/bin/elasticsearch-plugin install file://target/releases/SynonymsPlugin-$ESVERSION.zip
```

When installing the plugin, you will see a message similar to this one:

![Elastic installation message](https://user-images.githubusercontent.com/2104864/226297257-390d224a-dd1b-463a-a553-b77414315625.png)

This is because the plugin code needs to query Elasticsearch and requires special permissions to do so. 

## Getting Started

First, you need to declare the analyzers when creating your index (assuming Elasticsearch is running locally on the default port):

```
curl -XPUT "http://localhost:9200/my_index" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "default": {
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "asciifolding"
          ]
        },
        "default_search": {
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "asciifolding",
            "graph_synonyms"
          ]
        }
      },
      "filter": {
        "graph_synonyms": {
          "type": "index_synonym_graph",
          "index": ".synonyms",
          "expand": true,
          "lenient": false
        }
      }
    }
  }
}'

```

The index synonym graph is used only during search and can't be applied during indexing.
The parameters _lenient_ and _expand_ are similar to those of synonym-graph-tokenfilter, their default values are indicated above.
The parameter _index_ specifies where the plugin will load the synonym mappings from. The default value is _.synonyms_.

The next step is to declare the index used to store the synonyms and populate it.

```
curl -XPUT "http://localhost:9200/.synonyms"


curl -XPUT -H "Content-Type: application/json" "http://localhost:9200/.synonyms/_doc/synonyms" -d '
{
  "synonyms": [
    "i-pod, i pod => ipod",
    "sea biscuit, sea biscit => seabiscuit",
    "ipod, i-pod, i pod",
    "universe , cosmos",
    "lol, laughing out loud"
  ]
}'

```

The plugin supports only the [SOLR format](https://www.elastic.co/guide/en/elasticsearch/reference/7.17/analysis-synonym-graph-tokenfilter.html#_solr_synonyms_2).

The synonyms can be stored in any number of documents in the index, a query loads them all. The field names do not matter either. The values of the fields are either simple strings or arrays of strings. Each string corresponds to a line in the SOLR synonym format.

## Testing

Now that the synonym index has been populated, you can check that it is being applied. First, since the index has been created *after* declaring it in the index, it must be reloaded with 

`curl -XPOST  "http://localhost:9200/search/_reload_search_analyzers"`

you can then use the analyze endpoint to get a description of how a field will be analysed at search time, for instance

```
curl -XPOST "http://elastic:9200/search/_analyze" -H 'Content-Type: application/json' -d'
{ 
  "analyzer": "default_search", 
  "text": "Is this universe déja vu?"
}'
```

should return

```json
{
  "tokens" : [
    {
      "token" : "is",
      "start_offset" : 0,
      "end_offset" : 2,
      "type" : "<ALPHANUM>",
      "position" : 0
    },
    {
      "token" : "this",
      "start_offset" : 3,
      "end_offset" : 7,
      "type" : "<ALPHANUM>",
      "position" : 1
    },
    {
      "token" : "cosmos",
      "start_offset" : 8,
      "end_offset" : 16,
      "type" : "SYNONYM",
      "position" : 2
    },
    {
      "token" : "universe",
      "start_offset" : 8,
      "end_offset" : 16,
      "type" : "<ALPHANUM>",
      "position" : 2
    },
    {
      "token" : "deja",
      "start_offset" : 17,
      "end_offset" : 21,
      "type" : "<ALPHANUM>",
      "position" : 3
    },
    {
      "token" : "vu",
      "start_offset" : 22,
      "end_offset" : 24,
      "type" : "<ALPHANUM>",
      "position" : 4
    }
  ]
}
```

as you can see, _universe_ has been expanded into _cosmos_ with the same offset.


### Note to developers

Please format the code with 

```
mvn git-code-format:format-code -Dgcf.globPattern=**/*
```

prior to submitting a PR.

