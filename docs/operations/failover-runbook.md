# Spector DR Failover Runbook

This runbook outlines the procedure for performing a disaster recovery (DR) failover in a Spector cluster using a "kill-9" approach to simulate or recover from catastrophic node or datacenter failure.

## Prerequisites

1. **JdbcControlStore configured**: The cluster must be using the JDBC-based control store to track epoch sequences and fence tokens persistently across cluster boundaries.
2. **DR Exports enabled**: The DR export CronJob (`drExport.enabled=true` in Helm) must be running, pushing WAL checkpoints to the designated object store (e.g., S3, MinIO).
3. **Admin Tooling**: Access to `spectorctl` or `curl` on an operations box with connectivity to the standby cell/cluster API.

## Procedure: Manual Failover

1. **Verify Outage**  
   Ensure the primary cell is completely unresponsive or unrecoverable. 
   
2. **Promote the Standby Cell**  
   Using `spectorctl`, promote the standby cell to active status for the impacted namespace. This pulls the latest export from the object store and mints a new fence token via the control store, securely fencing off the old primary.
   ```bash
   spectorctl dr promote --namespace=my-namespace --from=s3://spector-backups/dr/
   ```
   If using REST:
   ```bash
   curl -X POST "http://standby-api:8080/api/v1/admin/dr/promote?namespace=my-namespace&from=s3://spector-backups/dr/"
   ```

3. **Verify Restoration and Data Loss (RPO)**  
   Check the restored High-Water Mark (HWM) on the promoted cell to determine exact data loss.
   ```bash
   spectorctl dr verify --namespace=my-namespace
   ```
   
4. **Update Routing**  
   Update any load balancers or gateway routers to point traffic for the namespace to the newly promoted cell. The standby cell is now the active primary.

## Expected RPO/RTO

- **RPO (Recovery Point Objective)**: Determined by your DR export schedule (default is every 6 hours). Expect up to 6 hours of data loss under normal export conditions, though manual checkpoints prior to a controlled failover can reduce this to ~0.
- **RTO (Recovery Time Objective)**: Dictated by object store download speed and WAL replay time. Generally expected to be under 15-30 minutes for multi-gigabyte memory snapshots.

## Verification: Running a DR Drill

To validate your DR setup without a real outage, you can use the automated DR drill script. The script simulates an NVMe failure, forces a promotion from the object store, measures RTO and RPO, and runs a functional `remember`/`recall` loop.

```bash
./deploy/dr/dr-drill.sh \
    --namespace test-ns \
    --cell-id cell-standby-1 \
    --object-store-url s3://spector-backups/dr/ \
    --data-dir /data/spector
```

For local CI/Docker testing, use the companion wrapper:
```bash
./deploy/dr/dr-drill-ci.sh
```
