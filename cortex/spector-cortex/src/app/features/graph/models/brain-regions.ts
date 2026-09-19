// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Brain Regions Model
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

export interface BrainRegion {
  readonly name: string;
  readonly tier: string;  // MemoryTier string
  readonly center: readonly [number, number, number]; // region centroid in brain-local coords
  readonly radius: number;  // bounding radius for node jitter placement
  readonly color: number;   // hex color
  readonly noiseFrequency: number; // surface wrinkle density for this region
}

export const BRAIN_REGIONS: readonly BrainRegion[] = [
  { name: 'Prefrontal Cortex',   tier: 'WORKING',    center: [0, 2, 10],     radius: 5,   color: 0xffb74d, noiseFrequency: 0.3 },
  { name: 'Hippocampus',         tier: 'EPISODIC',   center: [5, -1, 0],     radius: 4,   color: 0x66bb6a, noiseFrequency: 0.5 },
  { name: 'Hippocampus (L)',     tier: 'EPISODIC',   center: [-5, -1, 0],    radius: 4,   color: 0x66bb6a, noiseFrequency: 0.5 },
  { name: 'Temporal Cortex',     tier: 'SEMANTIC',   center: [8, 1, -3],     radius: 6,   color: 0x42a5f5, noiseFrequency: 0.25 },
  { name: 'Temporal Cortex (L)', tier: 'SEMANTIC',    center: [-8, 1, -3],    radius: 6,   color: 0x42a5f5, noiseFrequency: 0.25 },
  { name: 'Parietal Cortex',     tier: 'SEMANTIC',    center: [0, 5, -2],     radius: 5,   color: 0x29b6f6, noiseFrequency: 0.25 },
  { name: 'Cerebellum',          tier: 'PROCEDURAL',  center: [0, -5, -8],    radius: 5,   color: 0xab47bc, noiseFrequency: 0.6 },
];

export function getRegionsForTier(tier: string): BrainRegion[] {
  return BRAIN_REGIONS.filter(r => r.tier === tier);
}

export function getRegionForTier(tier: string): BrainRegion {
  const regions = getRegionsForTier(tier);
  if (regions.length > 0) {
    return regions[0];
  }
  
  // Fallback: cycle through ALL regions to distribute unknown tiers across the whole brain
  return BRAIN_REGIONS[Math.floor(Math.random() * BRAIN_REGIONS.length)];
}

export function jitterPositionInRegion(region: BrainRegion, index: number, total: number): [number, number, number] {
  // Distribute nodes within the bounding sphere using a 3D golden spiral
  const phi = Math.acos(1 - 2 * (index + 0.5) / total);
  const theta = Math.PI * (1 + Math.sqrt(5)) * index;
  
  // Use cubic root to ensure uniform volume density
  const r = region.radius * Math.cbrt((index + 0.5) / total);

  const x = region.center[0] + r * Math.sin(phi) * Math.cos(theta);
  const y = region.center[1] + r * Math.sin(phi) * Math.sin(theta);
  const z = region.center[2] + r * Math.cos(phi);

  return [x, y, z];
}
