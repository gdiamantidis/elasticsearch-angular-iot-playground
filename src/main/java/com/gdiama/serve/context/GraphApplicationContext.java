package com.gdiama.serve.context;

import com.gdiama.serve.resources.graph.GraphDataResource;
import com.gdiama.serve.resources.histo.HistogramResource;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
public class GraphApplicationContext {

    @Bean
    public GraphDataResource graphDataResource(TransportClient client) {
        return new GraphDataResource(client);
    }

    @Bean
    public HistogramResource histogramResource(TransportClient client) {
        return new HistogramResource(client);
    }

    @Bean
    public TransportClient esClient() {
        Settings settings = Settings.settingsBuilder().put("cluster.name", "elasticsearch").build();
        return TransportClient.builder().settings(settings).build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getLoopbackAddress(), 9300));
    }
}
