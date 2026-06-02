package com.spectrayan.spector.node.event;

import java.time.Instant;
import java.util.List;

/**
 * Cortex cluster topology event — periodic snapshot of the full cluster state.
 * Used by the cluster topology panel.
 *
 * <p>In standalone mode, a single-node topology is emitted so the
 * dashboard always has something to display.</p>
 *
 * @param nodeId           node that generated this topology snapshot
 * @param timestamp        when the snapshot was taken
 * @param nodes            state of each node in the cluster
 * @param replicationLinks pairs of nodeIds with active replication links
 */
public record SpectorCortexClusterTopologyEvent(
        String nodeId, Instant timestamp,
        List<SpectorCortexClusterNodeInfo> nodes,
        List<String[]> replicationLinks
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.cluster.topology"; }
}
