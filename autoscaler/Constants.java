public class Constants {

	// Default Policy
	public static final long GRACE_PERIOD = 1000 * 60 * 3; // in miliseconds
	public static final long DELAY = 40; // in seconds

	// Increase Policy
	public static final int INCR_POLICY_ID = 1;
	public static final int INCR_POLICY_PERIOD = 60 * 1; // in seconds
	public static final long INCR_DATAPOINTS_OFFSET = 1000 * ((INCR_POLICY_PERIOD * 2) + DELAY); // Max number of the
																									// last datapoints
																									// retrieved
	public static final long INCR_COOLDOWN_PERIOD = 1000 * INCR_POLICY_PERIOD; // in miliseconds

	// Decrease Policy
	public static final int DECR_POLICY_ID = 2;
	public static final int DECR_POLICY_PERIOD = 60 * 4; // in seconds
	public static final long DECR_DATAPOINTS_OFFSET = 1000 * ((DECR_POLICY_PERIOD * 2) + DELAY); // Max number of the
																									// last datapoints
																									// retrieved
	public static final long DECR_COOLDOWN_PERIOD = 1000 * DECR_POLICY_PERIOD; // in miliseconds

	// AutoScaler
	public static final String WEBSERVER_AMI_ID = "ami-4a30be39";
	public static final String KEY_PAIR_NAME = "CNV-proj-AWS";
	public static final String SECGROUP_NAME = "CNV-ssh+http";
	public static final int MAX_N_INSTANCES = 10;
	public static final int MIN_N_INSTANCES = 1;
	public static final double MAX_CPU_UTILIZATION = 70.0; // in percentage
	public static final double MIN_CPU_UTILIZATION = 30.0; // in percentage

}