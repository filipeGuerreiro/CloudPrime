import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;

import java.lang.System;


public class WebServerInfo {
    
    int           nRequests;
    List<Request> requestList;
    
    public WebServerInfo() {
        this.nRequests = 0;
        this.requestList = new ArrayList<Request>();
    }
    
    public void addRequest(long id, BigInteger parameter, long estimatedTime) {
        this.requestList.add(new Request(id, parameter, estimatedTime));
    }
    
    public void endRequest(long id) {
        Request r = getRequest(id);
        // TODO
    }
    
    private Request getRequest(long id) {
        for(Request r : this.requestList) {
            if(r.id == id) {
                return r;
            }
        }
        return null;
    }
    
    static class Request {
        
        long       id;
        BigInteger parameter;
        long       startTime;
        long       estimatedTime;
        
        Request(long id, BigInteger parameter, long estimatedTime) {
            this.id        = id;
            this.parameter = parameter;
            this.startTime = System.currentTimeMillis();
            this.estimatedTime = estimatedTime;
        }
    }
}