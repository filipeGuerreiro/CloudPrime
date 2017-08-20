package mss;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;

/**
 * DynamoDB item-class representation.
 */
@DynamoDBTable(tableName = "metrics-table")
public class WebserverInfo {

    private String webserverIP;
    // private Map<String, Long> metrics;
    private Long metrics;

    @DynamoDBHashKey(attributeName = "webserverIP")
    public String getWebserverIP() {
        return webserverIP;
    }

    public void setWebserverIP(String webserverIP) {
        this.webserverIP = webserverIP;
    }

    @DynamoDBAttribute(attributeName = "load")
    public Long getMetrics() {
        return metrics;
    }

    public void setMetrics(Long newMetrics) {
        this.metrics = newMetrics;
    }

    @Override
    public String toString() {
        return "Webserver [IP=" + webserverIP + ", metrics=[ " + metrics.toString() + " ]";
    }

}