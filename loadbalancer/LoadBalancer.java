package loadbalancer;

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

    private static final int SERVER_PORT = 8000;

    private static Map<String, Long> _webservers = Collections.synchronizedMap(new HashMap<String, Long>());

    // client used for checking how many webservers are running and what their IP is
    private static MSS _mss;
    // when a request comes, update the load by this amount while we don't query the
    // MSS
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
        }, 0, UPDATE_INFO_PERIOD);

        // open socket on port <SERVER_PORT> which expects requests as
        // <IP>:<SERVER_PORT>/f.html?n=<requestNumber>
        HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
        server.createContext("/", new ReceiveRequestHandler());
        System.out.println("LB: Receiving requests at port " + String.valueOf(SERVER_PORT));

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

            System.out.println(printWebservers());
            // send request to machine
            String response = "";
            int attempts = 0;
            do {
                attempts++;
                // choose which machine will handle the request
                String machineIP = chooseMachine();
                System.out.println("Picked machine: " + machineIP + " for req:" + request);
                try {
                    // mark the increase of load for this machine
                    Long load = _webservers.get(machineIP);
                    if (load == null) {
                        load = 0L;
                    }
                    load += RQST_INCREMENT;
                    _webservers.put(machineIP, _webservers.get(machineIP) + RQST_INCREMENT);

                    response = sendRequest(machineIP, request);

                    // checks if the value was not changed in the meantime by the mss update
                    _webservers.replace(machineIP, load, load - RQST_INCREMENT);

                } catch (Exception e) {
                    System.out.println("Failed to send request to " + machineIP);
                    _webservers.remove(machineIP);
                    if (machineIP != null && machineIP != "") {
                        _mss.removeWebserver(machineIP);
                    }
                    if (attempts > 5) {
                        response = "AVAILABLE MACHINE NOT FOUND.\nPLEASE TRY AGAIN LATER.";
                        break;
                    }
                    // System.out.println("Failed to send request to "+machineIP+": " +
                    // e.toString());
                }
            } while (response.equals(""));

            // send response back to requester
            t.sendResponseHeaders(200, response.length());

            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    // choose machine that has the lowest load
    private static String chooseMachine() {
        // updateInstanceInformation();
        String chosenOne = "";
        long lowestLoad = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : _webservers.entrySet()) {
            if (entry.getValue() == 0L) {
                return entry.getKey();
            }
            if (entry.getValue() < lowestLoad) {
                lowestLoad = entry.getValue();
                chosenOne = entry.getKey();
            }
        }

        return chosenOne;
    }

    // Receives information from MSS and updates the memory map of each webserver
    // with their respective load
    // Also removes servers that are no longer in the MSS
    private static void updateInstanceInformation() {
        List<String> activeServers = new ArrayList<String>();

        List<WebserverInfo> result = _mss.getAllMetrics();
        for (WebserverInfo ws : result) {
            _webservers.put(ws.getWebserverIP(), ws.getMetrics());
            // System.out.println("update: "+ws.getWebserverIP() +" "+ ws.getMetrics());
            activeServers.add(ws.getWebserverIP());
        }

        Set<String> keySet = _webservers.keySet();
        keySet.retainAll(activeServers);
    }

    // Sends HTTP request to webserver with number to be factored
    private static String sendRequest(String machineIP, String request) throws Exception {

        String url = machineIP + ":8000/f.html?n=" + request;

        URL obj = new URL("http", machineIP, 8000, "/f.html?n=" + request);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
            // System.out.println(inputLine);
        }
        in.close();

        return response.toString();

    }

    public static String printWebservers() {
        String res = "[\n";
        for (Map.Entry<String, Long> entry : _webservers.entrySet()) {
            res += entry.getKey() + " " + entry.getValue() + ",\n";
        }
        res += "]";
        return res;
    }
}
