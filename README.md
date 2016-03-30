# CloudPrime


## System architecture

**Health check**:
- Issued every 30 seconds
- Timeout after 5 seconds
- Declares the unit Unhealthy after 2 consecutive timeouts
- Declares the unit Healthy again after 3 consecutive responses

**Auto-scaling rules and policies**:
- Minimum number of machines: 1
- Maximum number of machines: none
- Increase group size when CPU utilization > 60% for 60 seconds
- Decrease group size when CPU utilization < 30% for 30 minutes
    
**Grace period**: 60 seconds
    
**Security group**:

1. Inbound Web server:
  - HTTP at port 8000 from "loadBalancer_IPaddress"
  - SSH  at port 22   from "myPC_IPaddress"

2. Inbound Load balancer:
  - HTTP at port 8000 from anywhere
  - SSH  at port 22   from "myPC_IPaddress"

## Collected metrics

1. Job Request value - Metric for mapping the factorizable number to the following metrics.
2. Number of bblock calls - Metric for determining how difficult the job was. More accurate than method calls, less accurate than instruction calls, but less prone to overflow.
3. Currant duration - Metric that calculates how long the request processing has been running.
4. Thread of current process - Metric for tracking the state of each job on the server.

## Example log with collected metrics

```bash
JOB 6197
Thread 31 finished in 9 milliseconds with 117235545721 bblocks.
JOB 7919
Thread 33 finished in 16 milliseconds with 117358109392 bblocks.
JOB 15331
Thread 35 finished in 24 milliseconds with 117703461950 bblocks.
JOB 29311
Thread 38 finished in 42 milliseconds with 117968663768 bblocks.
JOB 79273
Thread 41 finished in 82 milliseconds with 118251732321 bblocks.
JOB 170647
Thread 44 finished in 219 milliseconds with 118474183116 bblocks.
JOB 2374109
Thread 47 finished in 2411 milliseconds with 118849951748 bblocks.
JOB 2374109
Thread 50 finished in 2654 milliseconds with 119066007518 bblocks.
JOB 33839593
Thread 53 finished in 36740 milliseconds with 119673700298 bblocks.
JOB 438390839
Thread 56 finished in 460671 milliseconds with 127301692862 bblocks.
```

## Script to start the application

```bash
# Create binary files
cd $WEBSERVER_HOME
javac *.java

# Instrument IntFactorization.class
cd instrumented
java -XX:-UseSplitVerifier CloudPrimeInstrumentation .. ..

# Run webserver
cd ..
java -XX:-UseSplitVerifier WebServer

# Example query webserver
curl -X GET http://localhost:8000/7000000000
```