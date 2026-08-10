// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Graph View Strategy Interface
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

import * as THREE from 'three';
import { GraphNode, GraphEdge } from '../../../core/services/memory-table.service';

export interface ExplorerNode {
  id: string;
  tier: string;
  text: string;
  importance: number;
  valence: number;
  timestampMs: number;
  position: THREE.Vector3;
  velocity: THREE.Vector3;
  mesh: THREE.Sprite;
  glowMesh: THREE.Sprite;
  labelSprite: THREE.Sprite;
  selected: boolean;
  baseSize: number;
  visible: boolean;
  targetOpacity: number;
}

export interface ExplorerEdge {
  from: string;
  to: string;
  type: string;
  weight: number;
  relation: string | null;
  line: THREE.Line;
  labelSprite?: THREE.Sprite;
  weightSprite?: THREE.Sprite;
}

export interface FiringParticle {
  mesh: THREE.Sprite;
  trailMesh: THREE.Sprite;
  edgeIndex: number;
  progress: number;
  speed: number;
  alive: boolean;
  color: number;
}

export interface GraphViewStrategy {
  readonly name: 'constellation' | 'cortex';
  
  initScene(container: HTMLElement, camera: THREE.PerspectiveCamera): THREE.Scene;
  
  addNodes(apiNodes: GraphNode[], existingNodes: ExplorerNode[], warpIn: boolean): ExplorerNode[];
  
  addNodesAroundParent(apiNodes: GraphNode[], existingNodes: ExplorerNode[], parent: ExplorerNode): ExplorerNode[];
  
  addEdges(apiEdges: GraphEdge[], nodes: ExplorerNode[], existingEdges: ExplorerEdge[]): ExplorerEdge[];
  
  animateNodes(nodes: ExplorerNode[], delta: number, time: number, showLabels: boolean, cameraPos: THREE.Vector3, recallMode: boolean, recallMatchedIds: Set<string>): void;
  
  animateEdges(edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, showHebbian: boolean, showTemporal: boolean, showEntity: boolean, showLabels: boolean, orbitRadius: number, cameraPos: THREE.Vector3): void;
  
  animateParticles(particles: FiringParticle[], edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, scene: THREE.Scene): FiringParticle[];
  
  spawnParticle(edges: ExplorerEdge[], particles: FiringParticle[], scene: THREE.Scene, particleTexture: THREE.CanvasTexture, trailTexture: THREE.CanvasTexture): FiringParticle | null;
  
  dispose(scene: THREE.Scene, nodes: ExplorerNode[], edges: ExplorerEdge[], particles: FiringParticle[]): void;
}
