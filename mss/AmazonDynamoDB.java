package mss;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.math.BigInteger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.Tables;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.TableKeysAndAttributes;
import com.amazonaws.services.dynamodbv2.document.BatchGetItemOutcome;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;

import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;


public class AmazonDynamoDB {

    static DynamoDBMapper _dbMapper;
    static AmazonDynamoDBClient _dbClient;
    static DynamoDB _dynamoDB;
    
    private static final String TABLE_NAME  = "metrics-table";
    private static final String PRIMARY_KEY = "webserverIP";
    private static final String ATTRB_NAME  = "load";

    
    public static void init() throws Exception {

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
        _dbClient = new AmazonDynamoDBClient(credentials);
        Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
        _dbClient.setRegion(euWest1);
        
        try {
            // Create table if it does not exist yet
            if (Tables.doesTableExist( _dbClient , TABLE_NAME )) {
                System.out.println("Table " + TABLE_NAME + " is already ACTIVE");
            } else {
                // Create a table with a primary hash key named 'PRIMARY_KEY', which holds a string
                CreateTableRequest createTableRequest = new CreateTableRequest().withTableName( TABLE_NAME )
                    .withKeySchema(new KeySchemaElement().withAttributeName( PRIMARY_KEY ).withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName( PRIMARY_KEY ).withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
                    TableDescription createdTableDescription = _dbClient.createTable(createTableRequest).getTableDescription();
                System.out.println("Created Table: " + createdTableDescription);

                // Wait for it to become active
                System.out.println("Waiting for " + TABLE_NAME + " to become ACTIVE...");
                Tables.awaitTableToBecomeActive( _dbClient , TABLE_NAME );
            }
            _dynamoDB = new DynamoDB( _dbClient );
            _dbMapper = new DynamoDBMapper( _dbClient );
        } catch (AmazonServiceException ase) {
            amazonServiceExceptionMessage(ase);
        } catch (AmazonClientException ace) {
            amazonClientExceptionMessage(ace);
        }
    }
    

    
    /*
     * Create new item into db when new webserver joins.
     */
    public static void putItem(String webserverIP) throws AmazonServiceException, AmazonClientException {
        Table table = _dynamoDB.getTable( TABLE_NAME );
        
        Item item = new Item()
            .withPrimaryKey( PRIMARY_KEY , webserverIP )
            //.withMap( ATTRB_NAME , new HashMap<String, String>() )
            .withNumber( ATTRB_NAME , 0 ); 
        PutItemOutcome outcome = table.putItem( item );
        
        outcome.getPutItemResult();
    }
    
    /*
     * Delete item when webserver leaves.
     */
    public static void deleteItem(String webserverIP) {
        Table table = _dynamoDB.getTable( TABLE_NAME );

        // DeleteItemOutcome outcome = table.deleteItem( PRIMARY_KEY , webserverIP );
        
        try {

            DeleteItemSpec deleteItemSpec = new DeleteItemSpec().withPrimaryKey( PRIMARY_KEY, webserverIP );

            DeleteItemOutcome outcome = table.deleteItem(deleteItemSpec);

        } catch (Exception e) {
            System.err.println("Error deleting item in " + tableName);
            System.err.println(e.getMessage());
        }
    }
    

    
    public static void updateMetrics(String webserverIP, long load) {
        Table table = _dynamoDB.getTable( TABLE_NAME );
        
        Map<String, String> expressionAttributeNames = new HashMap<String, String>();
        expressionAttributeNames.put( "#A", ATTRB_NAME ); 
        
        Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
        expressionAttributeValues.put(":val1", load);
        
        UpdateItemOutcome outcome =  table.updateItem(
            PRIMARY_KEY,
            webserverIP,
            "set #A = #A + :val1", // UpdateExpression
            expressionAttributeNames,
            expressionAttributeValues);
    }
    
    
    /*
     * Gets all the load values from each thread for that webserver. 
     * Used by loadbalancer to determine total load on that webserver.
     */    
    public static List<WebserverInfo> getAllMetrics() throws Exception {
        
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        
        List<WebserverInfo> scanResult = _dbMapper.scan(WebserverInfo.class, scanExpression);
        /*
        for (WebserverInfo ws : scanResult) {
            System.out.println(ws);
        }
        */
        return scanResult;
    }
    
    
    /*
     * Print Amazon error message functions.
     */
    private static void amazonServiceExceptionMessage(AmazonServiceException ase) {
        System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("AWS Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }
    
    private static void amazonClientExceptionMessage(AmazonClientException ace) {
        System.out.println("Caught an AmazonClientException, which means the client encountered "
                + "a serious internal problem while trying to communicate with AWS, "
                + "such as not being able to access the network.");
        System.out.println("Error Message: " + ace.getMessage());
    }
    
    
    /*
    
    // Search for metrics in 'webserverIP'.
    
    public static void searchDB(String webserverIP) throws AmazonServiceException, AmazonClientException {
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.EQ.toString())
            .withAttributeValueList(new AttributeValue().withS( webserverIP ));
        scanFilter.put( PRIMARY_KEY , condition);
        ScanRequest scanRequest = new ScanRequest( TABLE_NAME ).withScanFilter(scanFilter);
        ScanResult scanResult = _dbClient.scan(scanRequest);
        System.out.println("Result: " + scanResult);
    }
    
    // Update thread load periodically.
    // Used by webserver.
    public static void updateThread(String webserverIP, String threadID, String load) {
        Table table = _dynamoDB.getTable( TABLE_NAME );
        
        Map<String, String> expressionAttributeNames = new HashMap<String, String>();
        expressionAttributeNames.put( "#A", threadID ); 
        
        Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
        expressionAttributeValues.put(":val1", load);
        
        UpdateItemOutcome outcome =  table.updateItem(
            PRIMARY_KEY,
            webserverIP,
            "set " + ATTRB_NAME + ".#A = :val1", // UpdateExpression: update map element, e.g. set threads.16 := <metric>
            expressionAttributeNames,
            expressionAttributeValues);
    }
    
    // BUG HERE -- probably in UpdateExpression
    public static void removeThread(String webserverIP, String threadID) {
        Table table = _dynamoDB.getTable( TABLE_NAME );
        
        Map<String, String> expressionAttributeNames = new HashMap<String, String>();
        expressionAttributeNames.put( "#A", threadID );
        System.out.println("removeThread " + webserverIP + " " + threadID);
        UpdateItemOutcome outcome =  table.updateItem(
            PRIMARY_KEY,
            webserverIP,
            "delete " + ATTRB_NAME + " :#A", // UpdateExpression: delete map element, e.g. delete threads 16
            expressionAttributeNames);
        System.out.println(outcome.toString());
    }
    */
}