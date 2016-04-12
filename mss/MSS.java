import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Executor;

public class MSS {
    
    private static AmazonDynamoDB amazonDB;
    
    public MSS() {
        amazonDB = new AmazonDynamoDB();
    }

    public static void main(String[] args) throws Exception {
        // requests as <this_ip>:8010/<server_ip>/metric?<metric>
        HttpServer server = HttpServer.create(new InetSocketAddress(8010), 0);
        server.createContext("/", new ReceiveMetricsHandler());
        System.out.println("LB: Receiving metrics at port 8010");
        
        // create a multi-threaded executor
        server.setExecutor(new ThreadPerTaskExecutor());
        
        server.start();
    }
    
    // Executor which creates a thread for each request
    static class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }

    static class ReceiveMetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            // read request data
            URI requestedUri = t.getRequestURI();
            String webserver = requestedUri.getPath().split("/")[1];
            String request = requestedUri.getQuery();
            
            // TODO
            System.out.println("webserver:" +webserver+" metric:"+request);

            // send response to requester
            t.sendResponseHeaders(200, response.length());
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

}