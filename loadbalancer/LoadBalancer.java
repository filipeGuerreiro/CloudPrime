import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.ArrayList;
import java.util.List;

public class LoadBalancer {
    
    private List<Integer> machinesInCluster;
    
    public LoadBalancer() {
        machinesInCluster = new ArrayList<Integer>();
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            // read request data
            URI requestedUri = t.getRequestURI();
            String request = requestedUri.getPath(); //or getQuery();
            String[] parts = request.split("/");
            request = parts[1];
            
            // choose which machine will handle the request
            Integer machine = chooseMachine(request);
            
            // send request to machine
            
            // wait for response
            String response = null; // todo
            response = request;
            
            // send response to requester
            t.sendResponseHeaders(200, response.length());
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    

    public static Integer chooseMachine(String request) {
        
        return new Integer(0);
  }

}