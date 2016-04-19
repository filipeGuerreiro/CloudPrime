package mss;

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Inet4Address;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.Enumeration;


public class MSS {
    
    private static AmazonDynamoDB _amazonDB = null;
    
    private static String _publicIP = null;
    
    public MSS() {
        _amazonDB = new AmazonDynamoDB();
        try {
            _amazonDB.init();
        } catch(Exception e) { System.out.println("Failed to initialize dynamoDB: " + e.toString()); }
    }
    
    public void initMetricStorage() {
        _publicIP = getPublicIpAddress();
        _amazonDB.putItem( _publicIP );
        attachShutdownHook();
    }
    
    public List<WebserverInfo> getAllMetrics() {
        List<WebserverInfo> results = null;
        try {
            results = _amazonDB.getAllMetrics();
        } catch(Exception e) { System.out.println("Failed to get all metrics: " + e.toString()); }
        return results;
    }
    
    public void updateMetrics(long threadID, long metric) {
        _amazonDB.updateThread( _publicIP , String.valueOf( threadID ) , String.valueOf( metric ) );
    }
    
    public void removeMetrics(long threadID) {
        _amazonDB.removeThread( _publicIP , String.valueOf( threadID ) );
    }
    
    private String getPublicIpAddress() {
        String res = null;
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) e.nextElement();
                if(ni.isLoopback())
                    continue;
                if(ni.isPointToPoint())
                    continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress address = (InetAddress) addresses.nextElement();
                    if(address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        if(!ip.equals(localhost))
                            res = ip;
                            //System.out.println((res = ip));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }
    
    // When the program receives a SIGTERM signal (due to autoscaler perhaps), remove its info from dynamoDB
    private void attachShutdownHook() {
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("SHUTDOWN HOOK RAN!");
                if( _publicIP != null ) { 
                    _amazonDB.deleteItem( _publicIP );
                }
            }
        });
    }

    /*
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
            
            String response = "TODO";

            // send response to requester
            t.sendResponseHeaders(200, response.length());
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    */
}