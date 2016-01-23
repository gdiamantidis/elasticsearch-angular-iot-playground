package com.gdiama.serve.resources.graph;

import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.time.LocalDateTime.parse;
import static java.util.Arrays.asList;


//./bin/elasticsearch --path.data=/Users/gdiama/Desktop/elasticsearch-data/data
@RestController
public class GraphDataResource {

    private final TransportClient client;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public GraphDataResource(TransportClient client) {
        this.client = client;
    }

    //http://localhost:9090/graph/::20d:6f00:ab3:7755?size=8000&days=1
    @RequestMapping(value = "/graph/{clipinId}", method = RequestMethod.GET)
    public Map<String, List<List<Number>>> data(@PathVariable String clipinId, @RequestParam int days, @RequestParam int size, HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        Map<String, List<List<Number>>> map = new HashMap<>();
        LocalDateTime start = LocalDateTime.now();
        try {
            String lastKnownDate = dateOfLastDataReceived(clipinId);
            String from = fromDate(lastKnownDate, days);
            SearchResponse searchResponse = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                    .setIndices("metrics")
                    .setTypes("metric")
                    .setQuery(QueryBuilders.boolQuery()
                                    .must(QueryBuilders.termQuery("clipinId", clipinId))
                                    .must(QueryBuilders.termsQuery("name", "PrimT", "HwFlow", "HwTOutlet", "HwTSet", "PrimTSet", "ActPow"))
                                    .must(QueryBuilders.rangeQuery("received").from(from).to(lastKnownDate))
                    )
                    .addSort("received", SortOrder.DESC)
                    .setSize(size)
                    .addFields("received", "value", "name")
                    .execute().get();

            System.out.println("Query Took: " + ChronoUnit.SECONDS.between(start, LocalDateTime.now()));
            start = LocalDateTime.now();

            SearchHit[] hits = searchResponse.getHits().getHits();
            for (SearchHit hit : hits) {
                Map<String, SearchHitField> fields = hit.getFields();
                Long timestamp = parse((String) fields.get("received").getValue(), dateFormat).toInstant(ZoneOffset.UTC).toEpochMilli();
                Number metricValue = Double.parseDouble((String) fields.get("value").getValue());

                String metricName = fields.get("name").getValue();
                List<List<Number>> values = map.get(metricName);
                if (values == null) {
                    values = new ArrayList<>();
                }
                values.add(asList(timestamp, metricValue));
                map.put(metricName, values);
            }

            System.out.println(">>> Transformation Took: " + ChronoUnit.MILLIS.between(start, LocalDateTime.now()));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    private String fromDate(String lastKnownDate, int days) {
        LocalDateTime dateTime = parse(lastKnownDate, dateFormat);
        return dateFormat.format(dateTime.minusDays(days));
    }

    private String dateOfLastDataReceived(String clipinId) throws ExecutionException, InterruptedException {
        SearchResponse searchResponse = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                .setIndices("metrics")
                .setTypes("metric")
                .setQuery(QueryBuilders.boolQuery()
                                .must(QueryBuilders.termQuery("clipinId", clipinId))
                                .must(QueryBuilders.termsQuery("name", "PrimT", "HwFlow", "HwTOutlet", "HwTSet", "PrimTSet", "ActPow"))
                )
                .addSort("received", SortOrder.DESC)
                .setSize(1)
                .addFields("received")
                .execute().get();
        SearchHit[] hits = searchResponse.getHits().getHits();

        return hits[0].field("received").getValue(); // roundup
    }
}
