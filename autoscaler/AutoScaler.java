import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Date;
import java.lang.Math;
import java.util.Timer;

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
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class AutoScaler {
	
	public static AmazonEC2 ec2;
    public static AmazonCloudWatchClient cloudWatch;
	
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
		
		// Using AWS Ireland. Pick the zone where you have AMI, key and secgroup
		ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");
		cloudWatch.setEndpoint("monitoring.eu-west-1.amazonaws.com");
		
		Set<Instance> instances = poolWebServers();
		while (instances.size() < Constants.MIN_N_INSTANCES){
			instances.add(launchNewInstance());
		}
    }
	
	protected static Instance launchNewInstance(){
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		runInstancesRequest.withImageId(Constants.WEBSERVER_AMI_ID)
						   .withInstanceType("t2.micro")
						   .withMinCount(1)
						   .withMaxCount(1)
						   .withKeyName(Constants.KEY_PAIR_NAME)
						   .withSecurityGroups(Constants.SECGROUP_NAME)
						   .withMonitoring(true);
						   
		RunInstancesResult runInstancesResult;
		synchronized(ec2){
			runInstancesResult = ec2.runInstances(runInstancesRequest);
		}
		Instance newInstance = runInstancesResult.getReservation().getInstances().get(0);
		System.out.println("Starting a new WebServer with id = " + newInstance.getInstanceId());
		return newInstance;
	}
	
	protected static void terminateInstance(String instanceId){
		System.out.println("Terminating WebServer with id = " + instanceId);
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
		synchronized(ec2){
			ec2.terminateInstances(termInstanceReq);
		}
	}
	
	protected static HashSet<Instance> poolWebServers(){
		//Pooling the information about all WebServer instances
		DescribeInstancesResult describeInstancesResult;
		int countWebServers = 0;
		synchronized(ec2){
			describeInstancesResult = ec2.describeInstances();
		}
		List<Reservation> reservations = describeInstancesResult.getReservations();
		HashSet<Instance> webServers = new HashSet<Instance>();
		System.out.println("total Reservations = " + reservations.size());
		for (Reservation reservation : reservations) {
			for (Instance instance : reservation.getInstances()) {
				if(instance.getImageId().equals(Constants.WEBSERVER_AMI_ID)){
					String state = instance.getState().getName();
					if(state.equals("running")){
						webServers.add(instance);
					} else if(!state.equals("terminated")) {countWebServers++;}
				}
			}
		}
		System.out.println("Running WebServers = " + webServers.size());
		System.out.println("Total WebServers = " + countWebServers );
		return webServers;
	}


    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("Welcome to Cloud Prime Auto Scaler LOG!");
        System.out.println("===========================================");

        try {
			init();
			
			//Create Scaling Policies
			IncreaseGroupPolicy increasePolicy = new IncreaseGroupPolicy(Constants.INCR_POLICY_ID);
			DecreaseGroupPolicy decreasePolicy = new DecreaseGroupPolicy(Constants.DECR_POLICY_ID);
			
			//Schedule Scaling Policies
			Timer t1 = new Timer();
			ScalePolicy.policyTimers.put(Constants.INCR_POLICY_ID, t1);
			t1.schedule(increasePolicy, new Date(new Date().getTime() + Constants.INCR_POOLING_PERIOD));
			
			Timer t2 = new Timer();
			ScalePolicy.policyTimers.put(Constants.DECR_POLICY_ID, t2);
			t2.schedule(decreasePolicy, new Date(new Date().getTime() + Constants.DECR_POOLING_PERIOD));
			
			
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}