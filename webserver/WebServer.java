import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public final class WebServer {
    
    public static void main(String[] args) throws Exception {
        // open socket on port 8000 which expects requests as <IP>:8000/<requestNumber>
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        
        // create a multi-threaded executor
        Executor myExecutor = new ThreadPerTaskExecutor();
        server.setExecutor(myExecutor); 
        
        server.start();
    }
    
    // Executor which creates a thread for each request
    static class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }
    
    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            // read request data
            URI requestedUri = t.getRequestURI();
            String request = requestedUri.getPath();
            String[] parts = request.split("/");
            request = parts[1];
            
            // factorize number
            BigInteger requestedNumber = new BigInteger(request);
            ArrayList<BigInteger> result = new IntFactorization().calcPrimeFactors(requestedNumber);           
            
            // craft response
            String response = "";
            for (BigInteger s : result) {
                response += s.toString() + "\n";
            }
            t.sendResponseHeaders(200, response.length());
            
            // send response and close stream
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
