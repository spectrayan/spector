/**
 * CUDA kernel for batch cosine similarity computation.
 * Each thread computes cosine(query, database[tid]) for one database vector.
 *
 * Compile: nvcc -ptx -arch=sm_80 batch_cosine.cu -o batch_cosine.ptx
 */
extern "C" __global__ void batch_cosine(
    const float* __restrict__ query,
    const float* __restrict__ database,
    float* __restrict__ results,
    int numVectors,
    int dimensions
) {
    int tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid >= numVectors) return;

    const float* dbVec = database + (long long)tid * dimensions;

    float dot = 0.0f;
    float normQ = 0.0f;
    float normD = 0.0f;

    for (int d = 0; d < dimensions; d++) {
        float q = query[d];
        float v = dbVec[d];
        dot += q * v;
        normQ += q * q;
        normD += v * v;
    }

    float denom = sqrtf(normQ * normD);
    results[tid] = (denom > 0.0f) ? (dot / denom) : 0.0f;
}
