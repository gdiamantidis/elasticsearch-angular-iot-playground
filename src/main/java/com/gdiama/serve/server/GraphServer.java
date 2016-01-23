package com.gdiama.serve.server;


import com.gdiama.serve.context.AppContext;

import java.io.IOException;

public class GraphServer extends EmbeddedJetty {
    public GraphServer() throws IOException {
        super(9090, AppContext.class);
    }

    public static void main(String[] args) throws Exception {
        new GraphServer().start();
    }
}
