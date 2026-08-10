// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Neuron Mesh Factory
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

import * as THREE from 'three';

export const EDGE_TYPE_COLORS: Record<string, number> = {
  HEBBIAN: 0x00ffcc,
  TEMPORAL: 0x00bcd4,
  ENTITY: 0xffc107,
};

export const TIER_COLORS: Record<string, number> = {
  WORKING: 0xffb74d,
  EPISODIC: 0x66bb6a,
  SEMANTIC: 0x42a5f5,
  PROCEDURAL: 0xab47bc,
};

/**
 * Creates a neuron cell body (soma).
 */
export function createNeuronSoma(color: number, size: number, importance: number): { mesh: THREE.Mesh; glowSprite: THREE.Sprite } {
  const geometry = new THREE.SphereGeometry(size * 0.8, 16, 12);
  const material = new THREE.MeshPhysicalMaterial({
    color: color,
    emissive: color,
    emissiveIntensity: 0.4 + importance * 0.6,
    transparent: true,
    opacity: 0.85 + importance * 0.15,
    roughness: 0.3,
    metalness: 0.1
  });
  
  const mesh = new THREE.Mesh(geometry, material);

  // Soft radial glow sprite using CanvasTexture
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const context = canvas.getContext('2d');
  
  if (context) {
    const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
    const c = new THREE.Color(color);
    const r = Math.floor(c.r * 255);
    const g = Math.floor(c.g * 255);
    const b = Math.floor(c.b * 255);
    
    gradient.addColorStop(0, `rgba(${r}, ${g}, ${b}, 1)`);
    gradient.addColorStop(0.2, `rgba(${r}, ${g}, ${b}, 0.8)`);
    gradient.addColorStop(0.5, `rgba(${r}, ${g}, ${b}, 0.2)`);
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
    
    context.fillStyle = gradient;
    context.fillRect(0, 0, 64, 64);
  }

  const texture = new THREE.CanvasTexture(canvas);
  const spriteMaterial = new THREE.SpriteMaterial({
    map: texture,
    color: 0xffffff,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  
  const glowSprite = new THREE.Sprite(spriteMaterial);
  const glowScale = size * 0.5;
  glowSprite.scale.set(glowScale, glowScale, 1);
  
  return { mesh, glowSprite };
}

// Pseudo-random generator for stable curve generation
function hashVector(v1: THREE.Vector3, v2: THREE.Vector3): number {
  const str = `${v1.x.toFixed(2)}_${v1.y.toFixed(2)}_${v1.z.toFixed(2)}_${v2.x.toFixed(2)}_${v2.y.toFixed(2)}_${v2.z.toFixed(2)}`;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash |= 0;
  }
  return hash;
}

function random(seed: number): number {
  const x = Math.sin(seed) * 10000;
  return (x - Math.floor(x)) * 2 - 1; // Range: -1 to 1
}

/**
 * Creates a curved dendrite/axon connection between two neurons.
 */
export function createDendriteCurve(fromPos: THREE.Vector3, toPos: THREE.Vector3, edgeType: string, weight: number): { tube: THREE.Mesh; curve: THREE.CatmullRomCurve3 } {
  const distance = fromPos.distanceTo(toPos);
  const curvature = Math.min(distance * 0.3, 8);
  
  const dir = new THREE.Vector3().subVectors(toPos, fromPos).normalize();
  
  // Create perpendicular vectors for offsetting control points
  const up = new THREE.Vector3(0, 1, 0);
  if (Math.abs(dir.dot(up)) > 0.99) {
    up.set(1, 0, 0);
  }
  const perp1 = new THREE.Vector3().crossVectors(dir, up).normalize();
  const perp2 = new THREE.Vector3().crossVectors(dir, perp1).normalize();
  
  const seed = hashVector(fromPos, toPos);
  
  // Control point 1 at ~25%
  const cp1 = new THREE.Vector3().lerpVectors(fromPos, toPos, 0.25);
  const offset1 = new THREE.Vector3()
    .addScaledVector(perp1, random(seed) * curvature)
    .addScaledVector(perp2, random(seed + 1) * curvature);
  cp1.add(offset1);
  
  // Control point 2 at ~75%
  const cp2 = new THREE.Vector3().lerpVectors(fromPos, toPos, 0.75);
  const offset2 = new THREE.Vector3()
    .addScaledVector(perp1, random(seed + 2) * curvature)
    .addScaledVector(perp2, random(seed + 3) * curvature);
  cp2.add(offset2);
  
  const curve = new THREE.CatmullRomCurve3([fromPos, cp1, cp2, toPos]);
  
  let tubeRadius = 0.06;
  if (edgeType === 'HEBBIAN') tubeRadius = 0.08 + weight * 0.15;
  else if (edgeType === 'TEMPORAL') tubeRadius = 0.05;
  else if (edgeType === 'ENTITY') tubeRadius = 0.10;
  
  const geometry = new THREE.TubeGeometry(curve, 20, tubeRadius, 6, false);
  
  const color = EDGE_TYPE_COLORS[edgeType] || 0xffffff;
  const emissiveIntensity = edgeType === 'HEBBIAN' ? 0.2 + weight * 0.5 : 0.3;
  
  const material = new THREE.MeshPhysicalMaterial({
    color: color,
    emissive: color,
    emissiveIntensity: emissiveIntensity,
    transparent: true,
    opacity: 0.5 + weight * 0.4,
    roughness: 0.4
  });
  
  const tube = new THREE.Mesh(geometry, material);
  
  return { tube, curve };
}

/**
 * Creates a tiny glowing bulb at the endpoint of a dendrite.
 */
export function createSynapticTerminal(position: THREE.Vector3, color: number): THREE.Mesh {
  const geometry = new THREE.SphereGeometry(0.15, 8, 6);
  const material = new THREE.MeshPhysicalMaterial({
    color: color,
    emissive: color,
    emissiveIntensity: 0.8,
    transparent: true,
    opacity: 0.9,
    roughness: 0.3
  });
  
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.copy(position);
  return mesh;
}

/**
 * Creates a 2D label sprite for a neuron.
 */
export function createNeuronLabel(id: string, tier: string, importance: number, color: number): THREE.Sprite {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 64;
  const context = canvas.getContext('2d');
  
  if (context) {
    // Pill shape background
    context.fillStyle = 'rgba(0, 0, 0, 0.6)';
    context.beginPath();
    context.roundRect(10, 10, 236, 44, 22);
    context.fill();
    
    // Pill border
    context.strokeStyle = `rgba(${(color >> 16) & 255}, ${(color >> 8) & 255}, ${color & 255}, 0.8)`;
    context.lineWidth = 2;
    context.stroke();
    
    // Label text
    context.fillStyle = '#ffffff';
    context.font = 'bold 20px sans-serif';
    context.textAlign = 'left';
    context.textBaseline = 'middle';
    context.fillText(`◉ ${id}`, 25, 32);
    
    // Tier text
    context.fillStyle = `rgba(${(color >> 16) & 255}, ${(color >> 8) & 255}, ${color & 255}, 1)`;
    context.font = '16px sans-serif';
    context.textAlign = 'right';
    context.fillText(tier, 230, 32);
  }
  
  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  
  const spriteMaterial = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthTest: false
  });
  
  const sprite = new THREE.Sprite(spriteMaterial);
  sprite.scale.set(1.8, 0.45, 1);
  
  return sprite;
}
