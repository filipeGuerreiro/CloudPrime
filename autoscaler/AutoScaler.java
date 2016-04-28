import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class AutoScaler {

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

    private static AmazonEC2 ec2;
    private static AmazonCloudWatchClient cloudWatch;
	private static Set<Instance> instances;
	
	private static final int INCR_POLICY_PERIOD = 1;
	private static final int INCR_DATAPOINTS_OFFSET = INCR_POLICY_PERIOD * 2; //Max number of the last datapoints retrieved
	private static final int DECR_POLICY_PERIOD = 7;
	private static final int DECR_DATAPOINTS_OFFSET = DECR_POLICY_PERIOD * 2;
	private static final String WEBSERVER_AMI_ID = "ami-7808840b";
	private static final String KEY_PAIR_NAME = "CNV-proj-AWS";
	private static final String SECGROUP_NAME = "CNV-ssh+http";
	private static final double MAX_CPU_UTILIZATION = 60.0;
	private static final double MIN_CPU_UTILIZATION = 30.0;
	private static final int POOLING_MINUTES_BREAK = 1;
	

/*	private AutoScaler(){
		init();
	}*/
	
	
    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
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
        ec2 = new AmazonEC2Client(credentials);
        cloudWatch = new AmazonCloudWatchClient(credentials);
		instances = new HashSet<Instance>();
		
		// Using AWS Ireland. Pick the zone where you have AMI, key and secgroup
		ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
		cloudWatch.setEndpoint("monitoring.eu-west-1.amazonaws.com"); 
    }
	
	private static Instance launchNewInstance(){
		System.out.println("Starting a new instance.");
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		//check if the following options match with the desired AMI and Security Group at AWS
		runInstancesRequest.withImageId(WEBSERVER_AMI_ID)
						   .withInstanceType("t2.micro")
						   .withMinCount(1)
						   .withMaxCount(1)
						   .withKeyName(KEY_PAIR_NAME)
						   .withSecurityGroups(SECGROUP_NAME)
						   .withMonitoring(true);

		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		Instance newInstance = runInstancesResult.getReservation().getInstances().get(0);
		return newInstance;
	}
	
	private static void terminateInstance(String instanceId){
		System.out.println("Terminating instance with id = " + instanceId);
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
	}
	
	private static void checkIncreaseGroupSizePolicy(List<Datapoint> datapoints) {
		//when CPU utilization > 60% for 60 seconds
		System.out.println("datapoints size = " + datapoints.size());
		for (Datapoint dp : datapoints) {
			System.out.println(" CPU utilization = " + dp.getAverage() + " with time = " + dp.getTimestamp().toString());
			if(dp.getAverage() <= MAX_CPU_UTILIZATION){
				return;
			}
		}
		//decrease group size
		launchNewInstance();
		//TODO: TIMER
	}
	
	private static void checkDecreaseGroupSizePolicy(List<Datapoint> datapoints, String instanceId) {
		//when CPU utilization < 30% for 2 consecutive 7 minutes period
		System.out.println("datapoints size = " + datapoints.size());
		for (Datapoint dp : datapoints) {
			System.out.println(" CPU utilization = " + dp.getAverage() + " with time = " + dp.getTimestamp().toString());
			if(dp.getAverage() >= MIN_CPU_UTILIZATION){
				return;
			}
		}
		//decrease group size
		terminateInstance(instanceId);
		//TODO: TIMER
	}

	public static Set<Instance> getInstanceSet(){
		return instances;
	}


    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");

        try {

			init();
			//Uncomment below if you need to start an instance
			//launchNewInstance();
			
			//Pooling the information about all WebServer instances
			DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
			List<Reservation> reservations = describeInstancesResult.getReservations();
			System.out.println("total reservations = " + reservations.size());
			InstanceInfo newInstanceInfo;
			for (Reservation reservation : reservations) {
				for (Instance instance : reservation.getInstances()) {
					if(!instance.getState().getName().equals("terminated") && instance.getImageId().equals(WEBSERVER_AMI_ID)){
						instances.add(instance);
					}
				}
			}
			System.out.println("total instances = " + instances.size());
			
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");

			//auto scaling monitoring loop
			for(;;){
				for (Instance instance : instances) {
					String imageId = instance.getImageId();
					String name = instance.getInstanceId();
					String state = instance.getState().getName();
					System.out.println("Instance Name : " + name +".");
					System.out.println("Instance State : " + state +".");
					if (state.equals("running")) { 
						instanceDimension.setValue(name);
						//Get metrics to check the increase policy
						long offsetInMilliseconds = 1000 * 60 * INCR_DATAPOINTS_OFFSET;
						GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
							.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
							.withNamespace("AWS/EC2")
							.withPeriod(60 * INCR_POLICY_PERIOD)
							.withMetricName("CPUUtilization")
							.withStatistics("Average")
							.withUnit("Percent")
							.withDimensions(instanceDimension)
							.withEndTime(new Date());
						GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
						List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
						if(datapoints.size() >= 2){
							checkIncreaseGroupSizePolicy(datapoints);
						}
						//Get metrics to check the decrease policy
						offsetInMilliseconds = 1000 * 60 * DECR_DATAPOINTS_OFFSET;
						request = new GetMetricStatisticsRequest()
							.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
							.withNamespace("AWS/EC2")
							.withPeriod(60 * DECR_POLICY_PERIOD)
							.withMetricName("CPUUtilization")
							.withStatistics("Average")
							.withUnit("Percent")
							.withDimensions(instanceDimension)
							.withEndTime(new Date());
						getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
						datapoints = getMetricStatisticsResult.getDatapoints();
						if(datapoints.size() >= 2){
							checkDecreaseGroupSizePolicy(datapoints, name);
						}
					}
					else {
						System.out.println("Instance id = " + name + " is not running!");
					}
				}
				//Stop pooling for 1 minute
				Thread.sleep(1000 * 60 * POOLING_MINUTES_BREAK);
				System.out.println("===========================================");
				//Pooling the information about all WebServer instances
				describeInstancesResult = ec2.describeInstances();
				reservations = describeInstancesResult.getReservations();
				System.out.println("total reservations = " + reservations.size());
				for (Reservation reservation : reservations) {
					for (Instance instance : reservation.getInstances()) {
						if(!instance.getState().getName().equals("terminated") && instance.getImageId().equals(WEBSERVER_AMI_ID)){
							instances.add(instance);
						}
					}
				}	
				System.out.println("total instances = " + instances.size());
			}
			
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}