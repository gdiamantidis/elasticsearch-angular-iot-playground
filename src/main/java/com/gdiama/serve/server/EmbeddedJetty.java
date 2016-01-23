package com.gdiama.serve.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.Integer.valueOf;

public class EmbeddedJetty {

    public static final int DEFAULT_PORT = 8080;
    private final AnnotationConfigWebApplicationContext applicationContext;
    private Server server;

    public EmbeddedJetty(int port, Class... classes) throws IOException {

        applicationContext = new AnnotationConfigWebApplicationContext();


        if (classes.length > 0) {
            applicationContext.register(classes);
        }

        final ServletHolder servletHolder = new ServletHolder(new DispatcherServlet(applicationContext));
        final ServletContextHandler context = new ServletContextHandler();


        context.setErrorHandler(null);
        context.setContextPath("/");
        context.addServlet(servletHolder, "/");

        server = new Server(port);
        server.setHandler(context);
    }

    public static void main(String[] args) throws Exception {
        new EmbeddedJetty(port(args, DEFAULT_PORT)).start();
    }

    public static int port(String[] args, int defaultPort) {
        int port = defaultPort;
        Optional<String> portArg = Stream.of(args).filter(arg -> arg.startsWith("-Djetty.port")).map(p -> p.replace("-Djetty.port=", "")).findFirst();
        if (portArg.isPresent()) {
            port = valueOf(portArg.get());
            System.setProperty("jetty.port", portArg.get());
        }
        return port;
    }

    public void start() throws Exception {
        try {
            server.start();
        } catch (Throwable e) {
            this.stop();
        }
    }

    public void join() throws Exception {
        server.join();
    }

    public void stop() throws Exception {
        this.server.stop();
    }

    public boolean isStopped() {
        return server.isStopped();
    }

    public boolean isStarted() {
        return server.isStarted();
    }
}