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
⋅⋅- HTTP at port 8000 from "loadBalancer_IPaddress"
⋅⋅- SSH  at port 22   from "myPC_IPaddress"

2. Inbound Load balancer:
⋅⋅- HTTP at port 8000 from anywhere
⋅⋅- SSH  at port 22   from "myPC_IPaddress"

## Collected metrics

1. Number of routine calls - TODO: justification
2. Number of executed basic blocks
3. Number of executed instructions

## Example log with collected metrics

```bash
example log
```

## Script to start the application