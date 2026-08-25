# Spector Inspect CLI

The `spector-inspect` module provides a command-line utility for offline and runtime inspection, debugging, and verification of Panama off-heap storage bundles, cognitive indexes, and memory partitions.

## Features

- **Header Validation**: Inspects 64-byte synaptic headers for byte layout, checksum, and corruption.
- **Partition Dumping**: Dumps metadata, record counts, active memory tiers, and Bloom filter statistics.
- **Vector Dump**: Inspects raw dense embedding vectors and cluster centroid assignments.

## Usage

```bash
java -jar spector-inspect.jar --path /path/to/.spector/partitions/
```
