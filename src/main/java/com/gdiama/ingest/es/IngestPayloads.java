package com.gdiama.ingest.es;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.time.LocalDateTime.parse;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class IngestPayloads {

    public static final DateTimeFormatter IN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    public static final DateTimeFormatter OUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String PAYLOAD_DIR = "/Users/gdiama/Desktop/connectedboilerdata-intfuture-2/uncompressed/";
//    public static final String PAYLOAD_DIR = "/Users/gdiama/Desktop/connectedboilerdata-intfuture-2/uncompressed/i-aeeffb03-warnings.2016-01-03_09";
//    public static final String PAYLOAD_DIR = "/Users/gdiama/Desktop/connectedboilerdata-intfuture-2/uncompressed/i-082282a4-warnings.2015-12-14_17";
    private static Gson gson = new Gson();

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings settings = Settings.settingsBuilder().put("cluster.name", "elasticsearch").build();
        TransportClient client = TransportClient.builder().settings(settings).build().addTransportAddress(new InetSocketTransportAddress(InetAddress.getLoopbackAddress(), 9300));


        LocalDateTime start = LocalDateTime.now();
        System.out.println(">>>> " + start);
        BulkProcessor bulkProcessor = getBulkProcessor(client);

        Files.list(Paths.get(PAYLOAD_DIR)).parallel().forEach(p -> {
            String type = type(p);
            switch (type) {
                case "status":
                    ingestStatus(p, bulkProcessor);
                    break;
                case "warnings":
                    ingestWarnings(p, bulkProcessor);
                    break;
                case "alerts":
                    ingestAlerts(p, bulkProcessor);
                    break;
            }
        });

        bulkProcessor.flush();
        bulkProcessor.awaitClose(10000, TimeUnit.SECONDS);

        LocalDateTime end = LocalDateTime.now();
        System.out.println(">>>> " + end);
        System.out.println(">>>> " + start.until(end, ChronoUnit.MINUTES));

        bulkProcessor.close();
    }

    private static BulkProcessor getBulkProcessor(TransportClient client) {
        return BulkProcessor.builder(client, new BulkProcessor.Listener() {
            public void beforeBulk(long l, BulkRequest bulkRequest) {
            }

            public void afterBulk(long l, BulkRequest bulkRequest, BulkResponse bulkResponse) {
                if(!stream(bulkResponse.getItems()).filter(BulkItemResponse::isFailed).collect(toList()).isEmpty()) {
                    System.out.println("Failures!!");
                }

            }

            public void afterBulk(long l, BulkRequest bulkRequest, Throwable throwable) {
                System.out.println(">>> Exception ");
                throwable.printStackTrace();
            }
        }).setBulkActions(1000).setConcurrentRequests(2).build();
    }

    private static void ingestStatus(Path p, BulkProcessor bulkProcessor) {
        ingest(p, line -> addStatusIndexRequest(bulkProcessor, line, date(p)));
    }

    private static void ingestAlerts(Path p, BulkProcessor bulkProcessor) {
        ingest(p, line -> addAlertsIndexRequest(bulkProcessor, line, date(p)));
    }

    private static void ingestWarnings(Path p, BulkProcessor bulkProcessor) {
        ingest(p, line -> addWarningsIndexRequest(bulkProcessor, line, date(p)));
    }

    private static void ingest(Path p, Consumer<String> ingestor) {
        BufferedReader bufferedReader = null;
        try {
            FileReader fileReader = new FileReader(p.toString());
            bufferedReader = new BufferedReader(fileReader);
            bufferedReader.lines().forEach(ingestor);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void addWarningsIndexRequest(BulkProcessor processor, String line, String date) {
        if (line == null || line.equals("")) {
            return;
        }

        JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
        String clipinId = jsonObject.get("clipinId").getAsString();

        IndexRequest indexRequest = new IndexRequest("warnings-" + date, "warning");
        HashMap<String, Object> source = new HashMap<>();
        source.put("clipinId", clipinId);
        addIfExists("type", jsonObject, source);
        addIfExists("description", jsonObject, source);
        source.put("received", convertTimestamp(jsonObject.get("time").getAsString()));
        indexRequest.source(source);
        processor.add(indexRequest);
        processor.flush();

    }


    private static void addAlertsIndexRequest(BulkProcessor processor, String line, String date) {
        if (line == null || line.equals("")) {
            return;
        }

        JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
        String clipinId = jsonObject.get("clipinId").getAsString();

        indexFault(processor, date, jsonObject, clipinId);

        JsonElement data = jsonObject.get("data");
        if (data != null) {
            JsonObject dataObject = data.getAsJsonObject();
            if (dataObject != null) {
                indexMetric(processor, date, jsonObject, clipinId, dataObject.get("metrics"));
            }

            if (dataObject != null) {
                indexStatus(processor, date, jsonObject, clipinId, dataObject.get("statuses"));
            }
        }
    }

    private static void indexFault(BulkProcessor processor, String date, JsonObject jsonObject, String clipinId) {
        IndexRequest indexRequest = new IndexRequest("fault-" + date, "fault");
        HashMap<String, Object> source = new HashMap<>();
        source.put("clipinId", clipinId);
        addIfExists("classification", jsonObject, source);
        addIfExists("CurFaultCode", jsonObject, source);
        addIfExists("description", jsonObject, source);
        addIfExists("remedy", jsonObject, source);
        source.put("received", convertTimestamp(jsonObject.get("time").getAsString()));
        indexRequest.source(source);
        processor.add(indexRequest);
    }

    private static void addIfExists(String varName, JsonObject jsonObject, HashMap<String, Object> source) {
        JsonElement jsonElement = jsonObject.get(varName);
        if (jsonElement != null) {
            String value = jsonElement.getAsString();
            source.put(varName, value);
        }
    }

    private static void addStatusIndexRequest(BulkProcessor processor, String line, String date) {
        if (line == null || line.equals("")) {
            return;
        }

        JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
        String clipinId = jsonObject.get("clipinId").getAsString();

        // todo
        JsonElement metricOrStatus = jsonObject.get("metrics");
        indexMetric(processor, date, jsonObject, clipinId, metricOrStatus);
        indexStatus(processor, date, jsonObject, clipinId, metricOrStatus);
    }

    private static void indexStatus(BulkProcessor processor, String date, JsonObject jsonObject, String clipinId, JsonElement metricOrStatus) {
        String type;
        if (metricOrStatus == null) {
            metricOrStatus = jsonObject.get("statuses");
            type = "status";
            createIndexRequestForMetricOrStatus(processor, indexName(type, date), type, jsonObject, clipinId, metricOrStatus);
        }
    }

    private static void indexMetric(BulkProcessor processor, String date, JsonObject jsonObject, String clipinId, JsonElement metricOrStatus) {
        String type;
        if (metricOrStatus != null) {
            type = "metric";
            createIndexRequestForMetricOrStatus(processor, indexName(type, date), type, jsonObject, clipinId, metricOrStatus);
        }
    }

    private static void createIndexRequestForMetricOrStatus(BulkProcessor processor, String indexName, String type, JsonObject jsonObject, String clipinId, JsonElement metrics) {
        metrics.getAsJsonArray().forEach(m -> {
            IndexRequest indexRequest = new IndexRequest(indexName, type);
            HashMap<String, Object> source = new HashMap<>();
            source.put("clipinId", clipinId);
            source.put("name", ((JsonObject) m).get("name").getAsString());
            source.put("received", convertTimestamp(jsonObject.get("time").getAsString()));
            source.put("value", ((JsonObject) m).get("value").getAsNumber());
            indexRequest.source(source);
            processor.add(indexRequest);
        });
    }

    private static String indexName(String type, String date) {
        return type + "-" + date;
    }

    private static String date(Path p) {
        return p.toString().split("/")[p.toString().split("/").length - 1].split("\\.")[1].split("_")[0];
    }

    private static String type(Path p) {
        return p.toString().split("/")[p.toString().split("/").length - 1].split("\\.")[0].split("-")[p.toString().split("/")[p.toString().split("/").length - 1].split("\\.")[0].split("-").length - 1];
    }

    private static String convertTimestamp(String incoming) {
//        in:  2015-11-02T23:59:45,874Z
//        out: 2015-11-02 23:59:45
        LocalDateTime dateTime = parse(incoming, IN_FORMAT);
        return OUT_FORMAT.format(dateTime);
    }

    private void createAlias() {

    }
}
