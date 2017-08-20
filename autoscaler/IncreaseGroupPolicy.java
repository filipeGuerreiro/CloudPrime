import java.util.Timer;
import java.util.List;
import java.util.Set;
import java.util.Date;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class IncreaseGroupPolicy extends ScalePolicy {

	public IncreaseGroupPolicy(Integer id) {
		this.policyId = id;
	}

	private boolean checkIncreaseGroupSize(List<Datapoint> datapoints) {
		// when CPU utilization > 60% for 60 seconds
		System.out.println("-- executing checkIncreaseGroupSizePolicy:");
		double cpuUtilization = 0.0;
		for (Datapoint dp : datapoints) {
			cpuUtilization = Math.floor(dp.getAverage() * 100) / 100;
			System.out.println(" CPU utilization = " + cpuUtilization + " with time = " + dp.getTimestamp().toString());
			if (cpuUtilization <= Constants.MAX_CPU_UTILIZATION) {
				return false;
			}
		}
		return true;
	}

	protected void policyTask() {
		System.out.println("===========================================");
		Set<Instance> webServers = AutoScaler.poolWebServers();

		Dimension instanceDimension = new Dimension();
		instanceDimension.setName("InstanceId");
		GetMetricStatisticsRequest request;
		GetMetricStatisticsResult getMetricStatisticsResult;
		List<Datapoint> datapoints;

		long offsetInMilliseconds = 0;
		int countOverloadingInstances = 0;

		for (Instance webServer : webServers) {
			System.out.println("---------------------------------");
			String name = webServer.getInstanceId();
			String state = webServer.getState().getName();
			System.out.println("WebServer Name : " + name + ".");
			System.out.println("WebServer State : " + state + ".");
			if (webServers.size() < Constants.MAX_N_INSTANCES) {
				// Get metrics to check the increase policy
				instanceDimension.setValue(name);
				offsetInMilliseconds = Constants.INCR_DATAPOINTS_OFFSET;
				request = new GetMetricStatisticsRequest()
						.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds)).withNamespace("AWS/EC2")
						.withPeriod(Constants.INCR_POLICY_PERIOD).withMetricName("CPUUtilization")
						.withStatistics("Average").withUnit("Percent").withDimensions(instanceDimension)
						.withEndTime(new Date());
				synchronized (AutoScaler.cloudWatch) {
					getMetricStatisticsResult = AutoScaler.cloudWatch.getMetricStatistics(request);
				}
				datapoints = getMetricStatisticsResult.getDatapoints();
				int dpsSize = datapoints.size();
				System.out.println("datapoints retrived = " + dpsSize);
				if (dpsSize > 1) {
					if (dpsSize > 2) {
						datapoints = getTwoRecentDatapoins(datapoints);
						System.out.println("datapoints filtered = " + datapoints.size());
					}
					if (checkIncreaseGroupSize(datapoints)) {
						countOverloadingInstances++;
					}
				}
			} else {
				System.out.println("Maximum number of running WebServers reached = " + Constants.MAX_N_INSTANCES);
			}
		}

		final Integer policyId = getPolicyId();
		if (countOverloadingInstances == webServers.size()) {
			// increase group size
			AutoScaler.launchNewInstance();
			cooldownAllPolicyTasks();
			return;
		}

		// Pause pooling for 1 minute
		System.out.println("Waiting: " + Constants.INCR_COOLDOWN_PERIOD + " miliseconds for the next pooling.");
		getPolicyTimer(policyId).schedule(new IncreaseGroupPolicy(policyId),
				new Date(new Date().getTime() + Constants.INCR_COOLDOWN_PERIOD));
	}
}
