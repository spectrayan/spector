// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Brain Geometry Factory
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

import * as THREE from 'three';
import { BRAIN_REGIONS } from './brain-regions';

// -----------------------------------------------------------------------
// Simple 3D Noise Implementation
// -----------------------------------------------------------------------
class SimpleNoise {
  private p: number[] = new Array(512);
  
  constructor() {
    const permutation = [
      151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
      190, 6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,88,237,149,56,87,174,20,
      125,136,171,168, 68,175,74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,
      105,92,41,55,46,245,40,244,102,143,54, 65,25,63,161, 1,216,80,73,209,76,132,187,208, 89,18,169,200,
      196,135,130,116,188,159,86,164,100,109,198,173,186, 3,64,52,217,226,250,124,123,5,202,38,147,118,126,
      255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152, 2,44,154,163, 70,
      221,153,101,155,167, 43,172,9,129,22,39,253, 19,98,108,110,79,113,224,232,178,185, 112,104,218,246,
      97,228,251,34,242,193,238,210,144,12,191,179,162,241, 81,51,145,235,249,14,239,107,49,192,214, 31,181,
      199,106,157,184, 84,204,176,115,121,50,45,127, 4,150,254,138,236,205,93,222,114,67,29,24,72,243,141,
      128,195,78,66,215,61,156,180
    ];
    for (let i = 0; i < 256; i++) {
      this.p[i] = permutation[i];
      this.p[i + 256] = permutation[i];
    }
  }
  
  private fade(t: number): number { return t * t * t * (t * (t * 6 - 15) + 10); }
  private lerp(t: number, a: number, b: number): number { return a + t * (b - a); }
  private grad(hash: number, x: number, y: number, z: number): number {
    const h = hash & 15;
    const u = h < 8 ? x : y;
    const v = h < 4 ? y : h === 12 || h === 14 ? x : z;
    return ((h & 1) === 0 ? u : -u) + ((h & 2) === 0 ? v : -v);
  }
  
  public noise3D(x: number, y: number, z: number): number {
    let X = Math.floor(x) & 255;
    let Y = Math.floor(y) & 255;
    let Z = Math.floor(z) & 255;
    x -= Math.floor(x);
    y -= Math.floor(y);
    z -= Math.floor(z);
    const u = this.fade(x);
    const v = this.fade(y);
    const w = this.fade(z);
    const A = this.p[X] + Y, AA = this.p[A] + Z, AB = this.p[A + 1] + Z;
    const B = this.p[X + 1] + Y, BA = this.p[B] + Z, BB = this.p[B + 1] + Z;
    return this.lerp(w, this.lerp(v, this.lerp(u, this.grad(this.p[AA], x, y, z),
                                     this.grad(this.p[BA], x - 1, y, z)),
                               this.lerp(u, this.grad(this.p[AB], x, y - 1, z),
                                     this.grad(this.p[BB], x - 1, y - 1, z))),
                       this.lerp(v, this.lerp(u, this.grad(this.p[AA + 1], x, y, z - 1),
                                     this.grad(this.p[BA + 1], x - 1, y, z - 1)),
                               this.lerp(u, this.grad(this.p[AB + 1], x, y - 1, z - 1),
                                     this.grad(this.p[BB + 1], x - 1, y - 1, z - 1))));
  }

  public fbm(x: number, y: number, z: number, octaves: number, frequency: number, amplitude: number): number {
    let total = 0;
    let max = 0;
    for (let i = 0; i < octaves; i++) {
      total += this.noise3D(x * frequency, y * frequency, z * frequency) * amplitude;
      max += amplitude;
      amplitude *= 0.5;
      frequency *= 2.0;
    }
    return total / max;
  }
}

const noise = new SimpleNoise();

// -----------------------------------------------------------------------
// Brain Geometry Generation
// -----------------------------------------------------------------------

export function createBrainGeometry(): THREE.Group {
  const brainGroup = new THREE.Group();

  const baseMaterial = new THREE.MeshPhysicalMaterial({
    transmission: 0.95,
    roughness: 0.1,
    thickness: 0.5,
    color: 0x8866cc,
    emissive: 0x220044,
    emissiveIntensity: 0.08,
    transparent: true,
    opacity: 0.08,
    side: THREE.DoubleSide,
    vertexColors: true,
    depthWrite: false,
  });

  const leftHemisphere = generateHemisphereGeometry(-1, baseMaterial);
  const rightHemisphere = generateHemisphereGeometry(1, baseMaterial);

  // Create wireframe overlay for each hemisphere — renders behind the solid
  const wireframeMat = new THREE.MeshBasicMaterial({
    color: 0x8866cc,
    wireframe: true,
    transparent: true,
    opacity: 0.06,
    depthWrite: false,
  });
  const leftWire = new THREE.Mesh(leftHemisphere.geometry.clone(), wireframeMat);
  leftWire.position.copy(leftHemisphere.position);
  leftWire.renderOrder = -2;
  brainGroup.add(leftWire);
  const rightWire = new THREE.Mesh(rightHemisphere.geometry.clone(), wireframeMat);
  rightWire.position.copy(rightHemisphere.position);
  rightWire.renderOrder = -2;
  brainGroup.add(rightWire);

  leftHemisphere.renderOrder = -1;
  rightHemisphere.renderOrder = -1;

  brainGroup.add(leftHemisphere);
  brainGroup.add(rightHemisphere);

  // Cerebellum
  const cerebellumGeo = new THREE.SphereGeometry(3.5, 20, 16);
  displaceCerebellumVertices(cerebellumGeo);
  const cerebellumMat = baseMaterial.clone();
  cerebellumMat.color.setHex(0xab47bc);
  cerebellumMat.vertexColors = false;
  cerebellumMat.opacity = 0.06;
  
  const cerebellum = new THREE.Mesh(cerebellumGeo, cerebellumMat);
  cerebellum.position.set(0, -5, -6);
  cerebellum.renderOrder = -1;
  brainGroup.add(cerebellum);

  // Brain stem
  const stemGeo = new THREE.CylinderGeometry(1.2, 0.6, 4, 12);
  const stemMat = new THREE.MeshStandardMaterial({
    color: 0x2a1a4a,
    roughness: 0.9,
    metalness: 0.1,
    transparent: true,
    opacity: 0.15,
    depthWrite: false,
  });
  const stem = new THREE.Mesh(stemGeo, stemMat);
  stem.position.set(0, -8, -5);
  stem.rotation.x = 0.2;
  stem.renderOrder = -1;
  brainGroup.add(stem);

  return brainGroup;
}

function generateHemisphereGeometry(side: -1 | 1, material: THREE.MeshPhysicalMaterial): THREE.Mesh {
  const geometry = new THREE.IcosahedronGeometry(10, 4);
  const positions = geometry.attributes['position'];
  const colors = [];
  const baseColor = new THREE.Color(0x8866cc);
  
  const clipThreshold = side === 1 ? 0.2 : -0.2;
  const newPositions = [];

  const tempVertex = new THREE.Vector3();
  const baseRadius = 10;
  const wrinkleDepth = 1.2;

  // Process vertices
  for (let i = 0; i < positions.count; i++) {
    tempVertex.fromBufferAttribute(positions, i);
    
    // Scale to ellipsoid
    tempVertex.x *= 1.0;
    tempVertex.y *= 0.85;
    tempVertex.z *= 1.1;

    // Displacement
    const n = noise.fbm(tempVertex.x, tempVertex.y, tempVertex.z, 3, 0.2, 1.0);
    tempVertex.normalize().multiplyScalar(baseRadius + n * wrinkleDepth);
    
    // Apply ellipsoid scaling again for the final shape after noise
    tempVertex.x *= 1.0;
    tempVertex.y *= 0.85;
    tempVertex.z *= 1.1;
    
    // Clip the middle to create the gap between hemispheres
    if ((side === 1 && tempVertex.x > clipThreshold) || (side === -1 && tempVertex.x < clipThreshold)) {
      tempVertex.x = clipThreshold + (Math.random() * 0.1 - 0.05);
    }
    
    newPositions.push(tempVertex.x, tempVertex.y, tempVertex.z);

    // Color mixing based on brain regions
    let nearestDist = Infinity;
    let nearestColor = baseColor;

    if (BRAIN_REGIONS && BRAIN_REGIONS.length > 0) {
      for (const region of BRAIN_REGIONS) {
        const center = region.center;
        if (!center) continue;
        
        const regionPos = new THREE.Vector3(center[0], center[1], center[2]);
        const dist = tempVertex.distanceTo(regionPos);
        
        if (dist < nearestDist) {
          nearestDist = dist;
          nearestColor = new THREE.Color(region.color || 0x8866cc);
        }
      }
    }

    // Blend vertex color with base color based on distance
    const blendFactor = Math.max(0, 1.0 - (nearestDist / 8.0));
    const vertexColor = baseColor.clone().lerp(nearestColor, blendFactor * 0.7);
    colors.push(vertexColor.r, vertexColor.g, vertexColor.b);
  }

  geometry.setAttribute('position', new THREE.Float32BufferAttribute(newPositions, 3));
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  geometry.computeVertexNormals();

  return new THREE.Mesh(geometry, material);
}

function displaceCerebellumVertices(geometry: THREE.BufferGeometry): void {
  const positions = geometry.attributes['position'];
  const tempVertex = new THREE.Vector3();
  const newPositions = [];
  
  for (let i = 0; i < positions.count; i++) {
    tempVertex.fromBufferAttribute(positions, i);
    
    const baseLength = tempVertex.length();
    const n = noise.fbm(tempVertex.x, tempVertex.y, tempVertex.z, 2, 0.6, 1.0);
    tempVertex.normalize().multiplyScalar(baseLength + n * 0.8);
    
    newPositions.push(tempVertex.x, tempVertex.y, tempVertex.z);
  }
  
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(newPositions, 3));
  geometry.computeVertexNormals();
}

// -----------------------------------------------------------------------
// Brain Mist Generation
// -----------------------------------------------------------------------

export function createBrainMist(): THREE.Points {
  const particleCount = 300;
  const positions = new Float32Array(particleCount * 3);
  const colors = new Float32Array(particleCount * 3);
  
  const color1 = new THREE.Color(0x6644aa);
  const color2 = new THREE.Color(0x88ccff);

  for (let i = 0; i < particleCount; i++) {
    // Distribute within an ellipsoid
    let x, y, z;
    do {
      x = (Math.random() - 0.5) * 20;
      y = (Math.random() - 0.5) * 17;
      z = (Math.random() - 0.5) * 22;
    } while ((x*x)/100 + (y*y)/72.25 + (z*z)/121 > 1); // Point in ellipsoid check
    
    positions[i * 3] = x;
    positions[i * 3 + 1] = y;
    positions[i * 3 + 2] = z;
    
    const mixRatio = Math.random();
    const pColor = color1.clone().lerp(color2, mixRatio);
    
    colors[i * 3] = pColor.r;
    colors[i * 3 + 1] = pColor.g;
    colors[i * 3 + 2] = pColor.b;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

  const material = new THREE.PointsMaterial({
    size: 0.8,
    vertexColors: true,
    transparent: true,
    opacity: 0.2,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });

  return new THREE.Points(geometry, material);
}
