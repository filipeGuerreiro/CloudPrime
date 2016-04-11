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

public class LoadBalancer {
    
    private static List<String> machinesInCluster = Collections.synchronizedList(new ArrayList<String>());
    
    public LoadBalancer() {
        //machinesInCluster = new ArrayList<String>();
    }

    public static void main(String[] args) throws Exception {
        // open socket on port 8000 which expects requests as <IP>:8000/f.html?n=<requestNumber>
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new ReceiveRequestHandler());
        System.out.println("LB: Receiving requests at port 8000");
        
        // create a multi-threaded executor
        server.setExecutor(new ThreadPerTaskExecutor());
        
        server.start();
        
        // open socket on port 8001 which expects new webserver announcements as <IP>:8001/webserver/ip?<myIP>
        HttpServer ipServer = HttpServer.create(new InetSocketAddress(8001), 0);
        ipServer.createContext("/webserver", new WebserverIpHandler());
        System.out.println("LB: Receiving webserver IPs at port 8001");
        
        // create a multi-threaded executor
        ipServer.setExecutor(new ThreadPerTaskExecutor()); 
        
        ipServer.start();
    }
    
    // Executor which creates a thread for each request
    static class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }

    static class ReceiveRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            // read request data
            URI requestedUri = t.getRequestURI();
            String request = requestedUri.getQuery();
            String[] parts = request.split("n=");
            request = parts[1];
            
            // estimate the duration of the request based on parameter and mss data
            
            // choose which machine will handle the request
            String machineIP = chooseMachine(request);
            
            // send request to machine
            String response = "NOT FOUND";
            try {
                response = sendGet(machineIP, request);
            } catch(Exception e) { System.out.println("Failed during request to webserver: " + e.toString()); }
            
            // send response to requester
            t.sendResponseHeaders(200, response.length());
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    
    static class WebserverIpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            // read request data
            URI requestedUri = t.getRequestURI();
            String request = requestedUri.getPath().split("/webserver/")[1];
            
            System.out.println("New IP: "+request);
            // store IP name
            addWebserver(request);
            
            String response = "OK";
            // send response to requester
            t.sendResponseHeaders(200, response.length());          
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
            
        }
    }
    

    public static String chooseMachine(String request) {
        
        return "TODO";
    }
    
    private static String sendGet(String machineIP, String request) throws Exception {

		String url = machineIP+":8000/f.html?n="+request;
		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// optional default is GET
		con.setRequestMethod("GET");

		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + url);
		System.out.println("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		//print result
		System.out.println(response.toString());
        return response.toString();

	}
    
    private static void addWebserver(String webserverIP) {
        
        synchronized(machinesInCluster) {
            machinesInCluster.add(webserverIP);
        }
    }

}