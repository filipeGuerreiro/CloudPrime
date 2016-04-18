package cloudprime.loadbalancer;

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

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.Timer;
import java.util.TimerTask;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;

import com.amazonaws.AmazonClientException;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import mss.*;

public class LoadBalancer {
        
    private static Map<String, Long> _webservers = Collections.synchronizedMap(new HashMap<String, Long>());
    
    // client used for checking how many webservers are running and what their IP is
    private static MSS _mss;
    // when a request comes, update the load by this amount while we don't query the MSS
    private static final long RQST_INCREMENT = 1000L; 
    
    private static final int UPDATE_INFO_PERIOD = 15 * 1000; // 15 seconds   
    private static Timer _updateTimer = new Timer();


    public static void main(String[] args) throws Exception {
        
        // initEC2Client();
        _mss = new MSS();
        
        // set timer to get webserver ips and loads periodically
        _updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateInstanceInformation();
            }
        }, UPDATE_INFO_PERIOD);
        
        // open socket on port 8000 which expects requests as <IP>:8000/f.html?n=<requestNumber>
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new ReceiveRequestHandler());
        System.out.println("LB: Receiving requests at port 8000");
        
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
            String machineIP = chooseMachine();
            
            // send request to machine
            String response = "NOT FOUND";
            try {
                // mark the increase of load for this machine
                long load = _webservers.get( machineIP ) + RQST_INCREMENT;
                _webservers.put( machineIP, _webservers.get( machineIP ) + RQST_INCREMENT );

                response = sendRequest( machineIP , request );
                
                // checks if the value was not changed in the meantime by the mss update
                if(_webservers.get( machineIP ) == load) { 
                    _webservers.put( machineIP, load - RQST_INCREMENT );
                }
            } catch(Exception e) { System.out.println("Failed to send request to webserver: " + e.toString()); }
            
            // send response back to requester
            t.sendResponseHeaders(200, response.length());
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    // check which machine has the lowest load
    private static String chooseMachine() {
        // updateInstanceInformation();
        String chosenOne = "";
        long lowestLoad = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : _webservers.entrySet()) {
            if(entry.getValue() == 0L ) { 
                return entry.getKey();                
            }
            if(entry.getValue() < lowestLoad) { 
                lowestLoad = entry.getValue();
                chosenOne = entry.getKey();
            }
        }
        
        return chosenOne;
    }
    
    private static String sendRequest(String machineIP, String request) throws Exception {

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
    
    

    private static void initEC2Client() throws Exception {

        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        _ec2Client = new AmazonEC2Client(credentials);
    }
    
    private static void updateInstanceInformation() {
        List<String> activeServers = new ArrayList<String>(); 
        
        // TODO
        
        Set<String> keySet = _webservers.keySet();
        keySet.retainAll( activeServers );
    }
    
    /*
    private static void updateInstanceInformation() {
        
        DescribeInstancesRequest request = new DescribeInstancesRequest();

        DescribeInstancesResult result = _ec2Client.describeInstances( request );
        List<Reservation> reservations = result.getReservations();

        List<String> activeServers = new ArrayList<String>(); 
        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                activeServers.add( instance.getPublicIpAddress() );
            }
        }
        Set<String> keySet = _webservers.keySet();
        keySet.retainAll( activeServers );
    }
    */
}