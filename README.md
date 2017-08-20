# CloudPrime

CloudPrime is a load balancer and auto scaler implementation.

This project sets up several webservers in different AWS EC2 instances running an integer prime factorization process.

Since only the binary is provided, code instrumentation is used to add metrics to the program, which send metrics information to AWS S3.

The auto scaler checks these metrics periodically to determine to scale up or down the number of instances available.

## System architecture

**Health check**:
- HTTP:8000/f.html?n=1000 => small request to check that the unit is still operational
- Issued every 30 seconds
- Timeout after 15 seconds
- Declares the unit Unhealthy after 3 consecutive timeouts
- Declares the unit Healthy again after 3 consecutive responses

**Auto-scaling rules and policies**:
- Minimum number of machines: 1
- Maximum number of machines: none
- Increase group size when CPU utilization > 60% for 60 seconds
- Decrease group size when CPU utilization < 30% for 2 consecutive 15 minute periods
    
**Grace period**: 120 seconds

**Cooldown period**: 300 seconds
    
**Security group**:

1. Inbound Web server:
  - HTTP at port 8000 from "loadBalancer_IPaddress"
  - SSH  at port 22   from "myPC_IPaddress"

2. Inbound Load balancer:
  - HTTP at port 8000 from anywhere
  - SSH  at port 22   from "myPC_IPaddress"

## Collected metrics

1. Job Request value - Metric for mapping the factorizable number to the following metrics.
2. Number of load calls - Metric for measuring the difficulty of the job.
3. Current duration - Metric that calculates how long the request processing has been running. Can be combined with load count to determine CPU use.
4. Thread of current process - Metric for tracking the state of each job on the server.

## Example log with collected metrics

```bash
JOB 6197
Thread 13 || 26 milliseconds || 37183 loads
JOB 7919
Thread 18 || 21 milliseconds || 84698 loads
JOB 15331
Thread 23 || 9 milliseconds || 176685 loads
JOB 29311
Thread 28 || 20 milliseconds || 352552 loads
JOB 79273
Thread 33 || 21 milliseconds || 828191 loads
JOB 170647
Thread 38 || 35 milliseconds || 1852074 loads
JOB 2374109
Thread 43 || 365 milliseconds || 16096729 loads
JOB 33839593
Thread 48 || 4774 milliseconds || 219134288 loads
```

## Script to start the application

```bash
# Set directory variables -- for example:
BIT_HOME="/home/ec2-user/webserver/BIT"
WEBSERVER_HOME="/home/ec2-user/webserver"

# Set Java running options
export _JAVA_OPTIONS=”-XX:-UseSplitVerifier” 
CP=$WEBSERVER_HOME:$BIT_HOME:$BIT_HOME/samples

# Compile BIT tool CloudPrimeInstrumentation (if it's not done already)
cd $BIT_HOME
javac samples/*.java 
#or javac -cp $CPsamples/*.java (if it can't find BIT lib)

# Create class files
cd $WEBSERVER_HOME
javac *.java

# Instrument IntFactorization.class
java -cp $CP CloudPrimeInstrumentation . .

# Run webserver
java -cp $CP WebServer

# If running local
curl -X GET http://localhost:8000/f.html?n=7000000000
# If in AWS
curl -X GET http://<loadBalancerIP-or-DNS>/f.html?n=7000000000
```