package com.gdiama.serve.resources.histo;

import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.TypeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalHistogram;
import org.joda.time.DateTime;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// /usr/local/elasticsearch/bin/elasticsearch --path.data=/Users/gdiama/Desktop/elasticsearch-data/data
@RestController
public class HistogramResource {


    private final TransportClient client;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HistogramResource(TransportClient client) {
        this.client = client;
    }

    //http://localhost:9090/histo/received/1M
    @RequestMapping(value = "/histo", method = RequestMethod.GET)
    public Map<String, List<List<Number>>> histo(@RequestParam String types, @RequestParam String interval, HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        LocalDateTime start = LocalDateTime.now();
        Map<String, List<List<Number>>> histo = new HashMap<>();

        try {
            SearchRequestBuilder requestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                    .setIndices("all_data")
                    .setSize(0);

            String[] field = types.split(",");
            for (String f : field) {
                requestBuilder.addAggregation(AggregationBuilders.filter(f).filter(new TypeQueryBuilder(f)).subAggregation(histoSubAggregation(interval)));
            }

            SearchResponse searchResponse = requestBuilder.execute().get();

            System.out.println("Query Took: " + ChronoUnit.SECONDS.between(start, LocalDateTime.now()));
            start = LocalDateTime.now();

            for (String f : field) {
                histo.put(f, subAgg((InternalHistogram) ((InternalFilter) searchResponse.getAggregations().getAsMap().get(f)).getAggregations().getAsMap().get("messagesPer1W")));
            }

            System.out.println(">>> Transformation Took: " + ChronoUnit.MILLIS.between(start, LocalDateTime.now()));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return histo;
    }

    public List<List<Number>> subAgg(InternalHistogram internalAggregations) {
        ArrayList<List<Number>> lists = new ArrayList<>();
        List<Histogram.Bucket> buckets = internalAggregations.getBuckets();
        for (Histogram.Bucket bucket : buckets) {
            ArrayList<Number> pair = new ArrayList<>();
            pair.add(((DateTime) bucket.getKey()).getMillis());
            pair.add(bucket.getDocCount());
            lists.add(pair);
        }

        return lists;
    }

    private DateHistogramBuilder histoSubAggregation(String interval) {
        DateHistogramBuilder messagesPerMonth = AggregationBuilders.dateHistogram("messagesPer" + interval).interval(new DateHistogramInterval(interval));
        return messagesPerMonth.field("received");
    }
}
