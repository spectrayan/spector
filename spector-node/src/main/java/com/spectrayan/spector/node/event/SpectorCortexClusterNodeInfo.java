package com.spectrayan.spector.node.event;

/**
 * Cluster node info record — state of a single node in the cluster.
 * Used as a component of {@link SpectorCortexClusterTopologyEvent}.
 *
 * @param nodeId           unique identifier for the node
 * @param status           "active", "draining", or "down"
 * @param shardCount       number of shards assigned to this node
 * @param memoryUsedBytes  memory in use on this node
 * @param queryRate        queries per second on this node
 */
public record SpectorCortexClusterNodeInfo(
        String nodeId,
        String status,
        int shardCount,
        long memoryUsedBytes,
        double queryRate
) {}
