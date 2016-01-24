package com.gdiama.ingest.es;

import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EventTimeDiff {


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final EventTimeDiff eventTimeDiff = new EventTimeDiff();
        Client client = eventTimeDiff.esClient();
        List<String> clipins = eventTimeDiff.clipins(client);

        for (String clipin : clipins) {

        }
    }

    public List<String> clipins(Client client) throws ExecutionException, InterruptedException {
        SearchResponse searchResponse = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                .setIndices("metrics")
                .setTypes("metric")
                .setQuery(QueryBuilders.matchAllQuery())
                .addAggregation(AggregationBuilders.terms("uniq_clipins").field("clipinId").size(10000))
                .setSize(0).execute().actionGet();


        ArrayList<String> uniqueClipins = new ArrayList<>();
        Terms terms = searchResponse.getAggregations().get("uniq_clipins");
        List<Terms.Bucket> buckets = terms.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            uniqueClipins.add(bucket.getKeyAsString());
        }

        return uniqueClipins;
    }


    private Client esClient() {
        Settings settings = Settings.settingsBuilder().put("cluster.name", "elasticsearch").build();
        return TransportClient.builder().settings(settings).build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getLoopbackAddress(), 9300));
    }
}
