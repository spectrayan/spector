package com.spectrayan.spector.bench;

import com.spectrayan.spector.core.CosineSimilarity;
import com.spectrayan.spector.core.DotProduct;
import com.spectrayan.spector.core.EuclideanDistance;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for SIMD similarity kernels.
 *
 * <p>Run via:</p>
 * <pre>
 *   mvn -pl spector-bench compile exec:java \
 *     -Dexec.mainClass=org.openjdk.jmh.Main \
 *     -Dexec.args="SimdKernelBenchmark -f 1 -wi 3 -i 5"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
public class SimdKernelBenchmark {

    @Param({"32", "128", "384", "768"})
    int dimensions;

    float[] vectorA;
    float[] vectorB;

    @Setup
    public void setup() {
        Random rng = new Random(42);
        vectorA = new float[dimensions];
        vectorB = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vectorA[i] = rng.nextFloat() * 2f - 1f;
            vectorB[i] = rng.nextFloat() * 2f - 1f;
        }
    }

    @Benchmark
    public void dotProduct(Blackhole bh) {
        bh.consume(DotProduct.compute(vectorA, vectorB));
    }

    @Benchmark
    public void cosineSimilarity(Blackhole bh) {
        bh.consume(CosineSimilarity.compute(vectorA, vectorB));
    }

    @Benchmark
    public void euclideanDistanceSquared(Blackhole bh) {
        bh.consume(EuclideanDistance.computeSquared(vectorA, vectorB));
    }

    @Benchmark
    public void euclideanDistance(Blackhole bh) {
        bh.consume(EuclideanDistance.compute(vectorA, vectorB));
    }
}
