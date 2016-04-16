import java.util.HashMap;
import java.util.Map;
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


public class AmazonDynamoDB {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

    static AmazonDynamoDBClient dynamoDB;
    
    private static final String TABLE_NAME = "metrics-table";
    private static final String ITEM_NAME  = "webserverIP";
    private static final String ATTRB_NAME  = "threads";

    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.ProfilesConfigFile
     * @see com.amazonaws.ClientConfiguration
     */
    private static void init() throws Exception {

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
        this.dynamoDB = new AmazonDynamoDBClient(credentials);
        Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        this.dynamoDB.setRegion(usWest2);
        
        try {
            // Create table if it does not exist yet
            if (Tables.doesTableExist( this.dynamoDB , TABLE_NAME )) {
                System.out.println("Table " + TABLE_NAME + " is already ACTIVE");
            } else {
                // Create a table with a primary hash key named 'ITEM_NAME', which holds a string
                CreateTableRequest createTableRequest = new CreateTableRequest().withTableName( TABLE_NAME )
                    .withKeySchema(new KeySchemaElement().withAttributeName( ITEM_NAME ).withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName( ITEM_NAME ).withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
                    TableDescription createdTableDescription = this.dynamoDB.createTable(createTableRequest).getTableDescription();
                System.out.println("Created Table: " + createdTableDescription);

                // Wait for it to become active
                System.out.println("Waiting for " + TABLE_NAME + " to become ACTIVE...");
                Tables.awaitTableToBecomeActive( this.dynamoDB , TABLE_NAME );
            }
        } catch (AmazonServiceException ase) {
            amazonServiceExceptionMessage(ase);
        } catch (AmazonClientException ace) {
            amazonClientExceptionMessage(ace);
        }
    }
    
    /*
     * Search for metrics in 'webserverIP'.
     */
    public static void searchDB(String webserverIP) throws AmazonServiceException, AmazonClientException {
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.EQ.toString())
            .withAttributeValueList(new AttributeValue().withS( webserverIP ));
        scanFilter.put( ITEM_NAME , condition);
        ScanRequest scanRequest = new ScanRequest( TABLE_NAME ).withScanFilter(scanFilter);
        ScanResult scanResult = this.dynamoDB.scan(scanRequest);
        System.out.println("Result: " + scanResult);
    }
    
    /*
     * Create new item into db when new webserver joins.
     */
    public static void putItem(String webserverIP) throws AmazonServiceException, AmazonClientException {
        Item item = new Item()
            .withPrimaryKey( ITEM_NAME , webserverIP )
            .withMap( ATTRB_NAME , new HashMap<String, String>() );
        PutItemRequest putItemRequest = new PutItemRequest(TABLE_NAME, item);
        PutItemResult putItemResult = this.dynamoDB.putItem(putItemRequest);
        System.out.println("Result: " + putItemResult);
    }
    
    /*
     * Delete item when webserver leaves.
     */
    public static void deleteItem(String webserverIP) {
        Table table = this.dynamoDB.getTable( TABLE_NAME );

        DeleteItemOutcome outcome = table.deleteItem( ITEM_NAME , webserverIP );
    }
    
    /*
     * Update thread load periodically.
     * Used by webserver.
     */
    public static void updateItem(String webserverIP, String threadID, String load) {
        Table table = this.dynamoDB.getTable( TABLE_NAME );
        
        Map<String, String> expressionAttributeNames = new HashMap<String, String>();
        expressionAttributeNames.put( "#A", ATTRB_NAME +"."+ threadID );
        
        Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
        expressionAttributeValues.put(":val1", load);
        
        UpdateItemOutcome outcome =  table.updateItem(
            ITEM_NAME,
            webserverIP,
            "set #A = :val2", // UpdateExpression
            expressionAttributeNames,
            expressionAttributeValues);
    }
    
    /*
     * Get the load value for a specific thread.
     * Used by webserver ? TODO
     */
    public static void getItem(String webserverIP, String threadID) {
        GetItemSpec spec = new GetItemSpec()
            .withPrimaryKey( ITEM_NAME , webserverIP )
            .withProjectionExpression( ATTRB_NAME + "." + threadID ) // access map element, e.g. threads.16 : <metric>
            .withConsistentRead( false );

        Table table = this.dynamoDB.getTable( TABLE_NAME );
        Item item = table.getItem(spec);
    }
    
    /*
     * Gets all the load values from each thread for that webserver. 
     * Used by loadbalancer to determine total load on that webserver.
     */
    public static Map<String,List<Item>> getMetrics(String... webserverIPs) {
        TableKeysAndAttributes forumTableKeysAndAttributes = new TableKeysAndAttributes( TABLE_NAME )
            .withProjectionExpression( ATTRB_NAME );

        forumTableKeysAndAttributes.addHashOnlyPrimaryKeys( ITEM_NAME , webserverIPs );
        BatchGetItemOutcome outcome = dynamoDB.batchGetItem(forumTableKeysAndAttributes);
        
        return outcome.getTableItems();
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

}