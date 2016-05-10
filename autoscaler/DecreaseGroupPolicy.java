import java.util.Timer;
import java.util.List;
import java.util.Set;
import java.util.Date;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class DecreaseGroupPolicy extends ScalePolicy{
	
	public DecreaseGroupPolicy(Integer id){
		this.policyId = id;
	}
	
	private boolean checkDecreaseGroupSize(List<Datapoint> datapoints) {
		//when CPU utilization < 30% for 2 consecutive 7 minutes period
		System.out.println("-- executing checkDecreaseGroupSize:");
		double cpuUtilization = 0.0;
		for (Datapoint dp : datapoints) {
			cpuUtilization = Math.floor(dp.getAverage() * 100) / 100;
			System.out.println(" CPU utilization = " + cpuUtilization + " with time = " + dp.getTimestamp().toString());
			if(cpuUtilization >= Constants.MIN_CPU_UTILIZATION){
				return false;
			}
		}
		return true;
	}
	
	protected void policyTask(){
		System.out.println("===========================================");
		Set<Instance> webServers = AutoScaler.poolWebServers();
		
		Dimension instanceDimension = new Dimension();
		instanceDimension.setName("InstanceId");
		GetMetricStatisticsRequest request;
		GetMetricStatisticsResult getMetricStatisticsResult;
		List<Datapoint> datapoints;
		final Integer policyId = getPolicyId();
		long offsetInMilliseconds = 0;
		
		for (Instance webServer : webServers) {
			System.out.println("---------------------------------");
			String name = webServer.getInstanceId();
			String state = webServer.getState().getName();
			System.out.println("WebServer Name : " + name +".");
			System.out.println("WebServer State : " + state +".");
			if (webServers.size() > Constants.MIN_N_INSTANCES) { 
				//Get metrics to check the decrease policy
				instanceDimension.setValue(name);
				offsetInMilliseconds = Constants.DECR_DATAPOINTS_OFFSET;
				request = new GetMetricStatisticsRequest()
					.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
					.withNamespace("AWS/EC2")
					.withPeriod(Constants.DECR_POLICY_PERIOD)
					.withMetricName("CPUUtilization")
					.withStatistics("Average")
					.withUnit("Percent")
					.withDimensions(instanceDimension)
					.withEndTime(new Date());
				synchronized(AutoScaler.cloudWatch){
					getMetricStatisticsResult = AutoScaler.cloudWatch.getMetricStatistics(request);
				}
				datapoints = getMetricStatisticsResult.getDatapoints();
				int dpsSize = datapoints.size();
				System.out.println("datapoints retrived = " + dpsSize);
				if(dpsSize > 1){
					if(dpsSize > 2) {
						datapoints = getTwoRecentDatapoins(datapoints);
						System.out.println("datapoints filtered = " + datapoints.size());
					}
					if(checkDecreaseGroupSize(datapoints)){
						//decrease group size
						AutoScaler.terminateInstance(name);
						cooldownAllPolicyTasks();
						return;
					}
				}
			}
			else {
				System.out.println("Minimun number of running WebServers reached = " + Constants.MIN_N_INSTANCES);
			}
		}
		//Pause pooling for 1 minute
		System.out.println("Waiting: " + Constants.DECR_POOLING_PERIOD +" miliseconds for the next pooling.");
		getPolicyTimer(policyId).schedule(new DecreaseGroupPolicy(policyId), new Date(new Date().getTime() + Constants.DECR_POOLING_PERIOD));
	}
}
	