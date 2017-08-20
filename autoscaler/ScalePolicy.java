import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.TimerTask;
import java.util.Timer;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public abstract class ScalePolicy extends TimerTask {

	protected static final ConcurrentMap<Integer, Timer> policyTimers = new ConcurrentHashMap<Integer, Timer>();

	protected Integer policyId;

	abstract void policyTask();

	@Override
	public void run() {
		policyTask();
	}

	protected void cooldownAllPolicyTasks() {
		System.out.println("Coolling down for : " + Constants.GRACE_PERIOD + " miliseconds.");
		for (Map.Entry e : ScalePolicy.policyTimers.entrySet()) {
			Integer policyId = (Integer) e.getKey();
			Timer oldTimer = (Timer) e.getValue();
			oldTimer.cancel();
			Timer newTimer = new Timer();
			policyTimers.replace(policyId, oldTimer, newTimer);
			if (policyId == Constants.INCR_POLICY_ID) {
				// System.out.println("INCR POLICY RESCHEDULED");
				newTimer.schedule(new IncreaseGroupPolicy(policyId),
						new Date(new Date().getTime() + Constants.GRACE_PERIOD));
			} else if (policyId == Constants.DECR_POLICY_ID) {
				// System.out.println("DECRE POLICY RESCHEDULED");
				newTimer.schedule(new DecreaseGroupPolicy(policyId),
						new Date(new Date().getTime() + Constants.GRACE_PERIOD));
			}
		}
	}

	protected List<Datapoint> getTwoRecentDatapoins(List<Datapoint> datapoints) {

		System.out.println(" Datapoint with time = " + datapoints.get(0).getTimestamp().toString());
		Datapoint firstRecent = datapoints.get(0);
		Datapoint secondRecent = null;

		for (int i = 1; i < datapoints.size(); i++) {
			Datapoint dp = datapoints.get(i);
			System.out.println(" Datapoint with time = " + dp.getTimestamp().toString());
			if (dp.getTimestamp().after(firstRecent.getTimestamp())) {
				secondRecent = firstRecent;
				firstRecent = dp;
			} else if (secondRecent == null || (dp.getTimestamp().after(secondRecent.getTimestamp())
					&& dp.getTimestamp().before(firstRecent.getTimestamp()))) {
				secondRecent = dp;
			}
		}
		System.out.println(" Datapoint firstRecent with time = " + firstRecent.getTimestamp().toString());
		System.out.println(" Datapoint secondRecent with time = " + secondRecent.getTimestamp().toString());
		return new ArrayList<Datapoint>(Arrays.asList(firstRecent, secondRecent));
	}

	protected Timer getPolicyTimer(Integer policyId) {
		return policyTimers.get(policyId);
	}

	public Integer getPolicyId() {
		return this.policyId;
	}

	public void addNewPolicyTimer(Integer policyId) {
		policyTimers.put(policyId, new Timer());
	}
}