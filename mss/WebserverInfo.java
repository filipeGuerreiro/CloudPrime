package cloudprime.mss;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
    
    /**
     * DynamoDB item-class representation.
     */
    @DynamoDBTable(tableName="metrics-table")
    public class WebserverInfo {
        
        private String webserverIP;
        private Map<String, String> metrics;

        @DynamoDBHashKey(attributeName="webserverIP")
        public String getWebserverIP() { return webserverIP; }
        public void setWebserverIP(String webserverIP) { this.webserverIP = webserverIP; }
        
        @DynamoDBAttribute(attributeName="threads")
        public Map<String, String> getMetrics() { return metrics; }
        public void setMetrics(Map<String, String> metrics) { this.metrics = metrics; }
        //public void setMetric(String key, String value) { this.metrics.put( key , value ); }
        
       
        @Override
        public String toString() {
            return "Webserver [IP=" + webserverIP + ", metrics=[ " + metrics.toString() + " ]";            
        }

    }