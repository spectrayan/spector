// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Brain View Strategy
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

import * as THREE from 'three';
import { GraphNode, GraphEdge } from '../../../core/services/memory-table.service';
import { GraphViewStrategy, ExplorerNode, ExplorerEdge, FiringParticle } from './view-strategy.interface';
import { createBrainGeometry, createBrainMist } from '../models/brain-geometry.factory';
import { getRegionsForTier, jitterPositionInRegion } from '../models/brain-regions';
import { createNeuronSoma, createDendriteCurve, createSynapticTerminal, createNeuronLabel, TIER_COLORS, EDGE_TYPE_COLORS } from '../models/neuron-mesh.factory';

export class BrainViewStrategy implements GraphViewStrategy {
  readonly name = 'cortex';

  private scene: THREE.Scene | null = null;
  private brainGroup: THREE.Group | null = null;
  private brainMist: THREE.Points | null = null;
  private dendriteCurves: Map<number, THREE.CatmullRomCurve3> = new Map();
  private synapticTerminals: THREE.Mesh[] = [];
  
  private regionToggle: Record<string, boolean> = {};

  initScene(container: HTMLElement, camera: THREE.PerspectiveCamera): THREE.Scene {
    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x0a0512, 0.008);

    this.brainGroup = createBrainGeometry();
    scene.add(this.brainGroup);

    this.brainMist = createBrainMist();
    scene.add(this.brainMist);

    const ambientLight = new THREE.AmbientLight(0xffffff, 0.8);
    scene.add(ambientLight);

    const directionalLight = new THREE.DirectionalLight(0xffaaee, 1.2);
    directionalLight.position.set(10, 20, 15);
    scene.add(directionalLight);

    // Add a second light from below for hemisphere illumination
    const bottomLight = new THREE.DirectionalLight(0x8866cc, 0.4);
    bottomLight.position.set(-5, -15, -10);
    scene.add(bottomLight);

    this.scene = scene;
    return scene;
  }

  addNodes(apiNodes: GraphNode[], existingNodes: ExplorerNode[], warpIn: boolean): ExplorerNode[] {
    const newNodes: ExplorerNode[] = [];
    if (!this.scene) return newNodes;

    const nodesByTier: Record<string, GraphNode[]> = {};
    for (const node of apiNodes) {
      if (!nodesByTier[node.tier]) {
        nodesByTier[node.tier] = [];
      }
      nodesByTier[node.tier].push(node);
    }

    for (const [tier, nodes] of Object.entries(nodesByTier)) {
      const regions = getRegionsForTier(tier);
      const color = TIER_COLORS[tier] || 0xffffff;
      
      nodes.forEach((node, index) => {
        // Alternate between bilateral regions (left/right) using index
        let regionIndex = 0;
        if (regions.length > 1) {
          regionIndex = index % regions.length;
        }
        
        const region = regions[regionIndex] || regions[0];
        if (!region) return;

        // Distribute nodes within the chosen region
        const nodesInThisRegion = Math.ceil(nodes.length / regions.length);
        const indexInRegion = Math.floor(index / regions.length);
        const pos = jitterPositionInRegion(region, indexInRegion, nodesInThisRegion);
        const position = new THREE.Vector3(pos[0], pos[1], pos[2]);

        const baseSize = 0.2 + (node.importance || 0) * 0.15;
        const { mesh, glowSprite } = createNeuronSoma(color, baseSize, node.importance || 0);

        mesh.position.copy(position);
        glowSprite.position.copy(position);

        if (warpIn) {
          mesh.scale.set(0.01, 0.01, 0.01);
          glowSprite.scale.set(0.01, 0.01, 0.01);
          mesh.userData['warpIn'] = true;
        }

        const labelSprite = createNeuronLabel(node.id, node.tier, node.importance || 0, color);
        labelSprite.position.copy(position).add(new THREE.Vector3(0, baseSize * 1.2, 0));
        labelSprite.visible = false;

        this.scene!.add(mesh);
        this.scene!.add(glowSprite);
        this.scene!.add(labelSprite);

        mesh.userData['id'] = node.id;
        mesh.userData['type'] = 'node';
        mesh.renderOrder = 1;
        glowSprite.renderOrder = 0;
        labelSprite.renderOrder = 2;

        newNodes.push({
          id: node.id,
          tier: node.tier,
          text: node.textPreview || node.id,
          importance: node.importance || 0,
          valence: node.valence || 0,
          timestampMs: node.timestampMs || Date.now(),
          position: position,
          velocity: new THREE.Vector3(),
          mesh: mesh as any,
          glowMesh: glowSprite,
          labelSprite: labelSprite,
          selected: false,
          baseSize: baseSize,
          visible: true,
          targetOpacity: 1.0
        });
      });
    }

    return newNodes;
  }

  addNodesAroundParent(apiNodes: GraphNode[], existingNodes: ExplorerNode[], parent: ExplorerNode): ExplorerNode[] {
    const newNodes = this.addNodes(apiNodes, existingNodes, true);
    
    newNodes.forEach((node, i) => {
      const angle = (i / newNodes.length) * Math.PI * 2;
      const radius = 3 + (node.importance * 2);
      
      const targetPos = new THREE.Vector3(
        parent.position.x + Math.cos(angle) * radius,
        parent.position.y + (Math.random() - 0.5) * 2,
        parent.position.z + Math.sin(angle) * radius
      );
      
      node.position.copy(targetPos);
      node.mesh.position.copy(targetPos);
      node.glowMesh.position.copy(targetPos);
      node.labelSprite.position.copy(targetPos).add(new THREE.Vector3(0, node.baseSize * 2, 0));
    });
    
    return newNodes;
  }

  addEdges(apiEdges: GraphEdge[], nodes: ExplorerNode[], existingEdges: ExplorerEdge[]): ExplorerEdge[] {
    const newEdges: ExplorerEdge[] = [];
    if (!this.scene) return newEdges;
    
    for (const edge of apiEdges) {
      const fromNode = nodes.find(n => n.id === edge.fromId);
      const toNode = nodes.find(n => n.id === edge.toId);
      
      if (fromNode && toNode) {
        const { tube, curve } = createDendriteCurve(fromNode.position, toNode.position, edge.type, edge.weight || 1);
        
        const color = EDGE_TYPE_COLORS[edge.type] || 0xffffff;
        const fromTerminal = createSynapticTerminal(fromNode.position, color);
        const toTerminal = createSynapticTerminal(toNode.position, color);
        
        this.synapticTerminals.push(fromTerminal, toTerminal);
        this.scene.add(fromTerminal);
        this.scene.add(toTerminal);
        
        tube.userData = { type: 'edge', from: edge.fromId, to: edge.toId, edgeType: edge.type };
        this.scene.add(tube);
        
        let labelSprite: THREE.Sprite | undefined;
        let weightSprite: THREE.Sprite | undefined;
        
        if (edge.relation) {
          labelSprite = this.createEdgeLabel(edge.relation, color);
          labelSprite.visible = false;
          this.scene.add(labelSprite);
        }
        
        if (edge.weight) {
          weightSprite = this.createWeightLabel(edge.weight, color);
          weightSprite.visible = false;
          this.scene.add(weightSprite);
        }
        
        const explorerEdge: ExplorerEdge = {
          from: edge.fromId,
          to: edge.toId,
          type: edge.type,
          weight: edge.weight || 1,
          relation: edge.relation || null,
          line: tube as any,
          labelSprite: labelSprite,
          weightSprite: weightSprite
        };
        
        (explorerEdge.line as any).__curve = curve;
        
        const globalIndex = (existingEdges?.length || 0) + newEdges.length;
        this.dendriteCurves.set(globalIndex, curve);
        
        newEdges.push(explorerEdge);
      }
    }
    
    return newEdges;
  }

  private createEdgeLabel(text: string, color: number): THREE.Sprite {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 32;
    const context = canvas.getContext('2d');
    if (context) {
      context.fillStyle = 'rgba(0,0,0,0.5)';
      context.beginPath();
      context.roundRect(0, 0, 128, 32, 16);
      context.fill();

      context.strokeStyle = `rgba(${(color >> 16) & 255}, ${(color >> 8) & 255}, ${color & 255}, 0.8)`;
      context.lineWidth = 1;
      context.stroke();

      context.fillStyle = '#ffffff';
      context.font = '12px sans-serif';
      context.textAlign = 'center';
      context.textBaseline = 'middle';
      context.fillText(text.substring(0, 15), 64, 16);
    }
    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    const sprite = new THREE.Sprite(spriteMaterial);
    sprite.scale.set(4, 1, 1);
    return sprite;
  }

  private createWeightLabel(weight: number, color: number): THREE.Sprite {
    const canvas = document.createElement('canvas');
    canvas.width = 32;
    canvas.height = 32;
    const context = canvas.getContext('2d');
    if (context) {
      context.fillStyle = `rgba(${(color >> 16) & 255}, ${(color >> 8) & 255}, ${color & 255}, 0.8)`;
      context.beginPath();
      context.arc(16, 16, 16, 0, Math.PI * 2);
      context.fill();

      context.fillStyle = '#000000';
      context.font = 'bold 12px sans-serif';
      context.textAlign = 'center';
      context.textBaseline = 'middle';
      context.fillText(weight.toFixed(1), 16, 16);
    }
    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    const sprite = new THREE.Sprite(spriteMaterial);
    sprite.scale.set(1.5, 1.5, 1);
    return sprite;
  }

  animateNodes(nodes: ExplorerNode[], delta: number, time: number, showLabels: boolean, cameraPos: THREE.Vector3, recallMode: boolean, recallMatchedIds: Set<string>): void {
    const driftSpeed = 0.05;

    for (const node of nodes) {
      node.position.x += Math.sin(time * 0.5 + node.position.y) * driftSpeed * delta;
      node.position.y += Math.cos(time * 0.4 + node.position.z) * driftSpeed * delta;
      node.position.z += Math.sin(time * 0.6 + node.position.x) * driftSpeed * delta;
      
      node.mesh.position.copy(node.position);
      node.glowMesh.position.copy(node.position);

      const distToCamera = node.position.distanceTo(cameraPos);
      
      if (node.labelSprite) {
      node.labelSprite.position.copy(node.position).add(new THREE.Vector3(0, node.baseSize * 1.2, 0));
        node.labelSprite.visible = showLabels && distToCamera < 30;
      }

      node.mesh.visible = node.visible;
      node.glowMesh.visible = node.visible;

      if (!node.visible) continue;

      const material = (node.mesh as any).material as THREE.Material;
      if (material && 'opacity' in material) {
        (material as any).opacity = THREE.MathUtils.lerp((material as any).opacity, node.targetOpacity, delta * 2);
      }

      let pulseScale = 1.0;
      let glowPulseScale = 1.0;
      
      if (recallMode && recallMatchedIds.has(node.id)) {
        pulseScale = 1.2 + Math.sin(time * 5) * 0.2;
        glowPulseScale = 1.5 + Math.sin(time * 5) * 0.5;
      } else {
        pulseScale = 1.0 + Math.sin(time * 2 + node.position.x) * 0.05;
        glowPulseScale = 1.0 + Math.sin(time * 2 + node.position.y) * 0.1;
      }
      
      if (node.selected) {
        pulseScale *= 1.3;
        glowPulseScale *= 1.5;
      }

      const scale = node.baseSize * pulseScale;
      
      if (node.mesh.userData['warpIn']) {
        const curScale = node.mesh.scale.x;
        const target = scale;
        const newScale = THREE.MathUtils.lerp(curScale, target, delta * 5);
        node.mesh.scale.set(newScale, newScale, newScale);
        
        const glowTarget = node.baseSize * 0.15 * glowPulseScale;
        const newGlowScale = THREE.MathUtils.lerp(node.glowMesh.scale.x, glowTarget, delta * 5);
        node.glowMesh.scale.set(newGlowScale, newGlowScale, 1);
        
        if (Math.abs(newScale - target) < 0.01) {
          node.mesh.userData['warpIn'] = false;
        }
      } else {
        node.mesh.scale.set(scale, scale, scale);
        const glowScale = node.baseSize * 0.15 * glowPulseScale;
        node.glowMesh.scale.set(glowScale, glowScale, 1);
      }
    }
  }

  animateEdges(edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, showHebbian: boolean, showTemporal: boolean, showEntity: boolean, showLabels: boolean, orbitRadius: number, cameraPos: THREE.Vector3): void {
    for (let i = 0; i < edges.length; i++) {
      const edge = edges[i];
      
      let isVisible = false;
      if (edge.type === 'HEBBIAN' && showHebbian) isVisible = true;
      if (edge.type === 'TEMPORAL' && showTemporal) isVisible = true;
      if (edge.type === 'ENTITY' && showEntity) isVisible = true;
      
      const fromNode = nodes.find(n => n.id === edge.from);
      const toNode = nodes.find(n => n.id === edge.to);
      
      if (fromNode && toNode && (!fromNode.visible || !toNode.visible)) {
        isVisible = false;
      }
      
      edge.line.visible = isVisible;
      
      if (edge.labelSprite) edge.labelSprite.visible = isVisible && showLabels;
      if (edge.weightSprite) edge.weightSprite.visible = isVisible && showLabels;
      
      if (!isVisible) continue;
      
      const curve = (edge.line as any).__curve as THREE.CatmullRomCurve3;
      if (curve) {
        if (edge.labelSprite || edge.weightSprite) {
          const midPoint = curve.getPoint(0.5);
          if (edge.labelSprite) edge.labelSprite.position.copy(midPoint).add(new THREE.Vector3(0, 0.5, 0));
          if (edge.weightSprite) edge.weightSprite.position.copy(midPoint).add(new THREE.Vector3(0, -0.5, 0));
        }
      }
    }
  }

  animateParticles(particles: FiringParticle[], edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, scene: THREE.Scene): FiringParticle[] {
    const activeParticles: FiringParticle[] = [];

    for (const particle of particles) {
      if (!particle.alive) continue;

      particle.progress += particle.speed * delta;
      
      if (particle.progress >= 1.0) {
        particle.alive = false;
        scene.remove(particle.mesh);
        scene.remove(particle.trailMesh);
        
        const edge = edges[particle.edgeIndex];
        if (edge) {
          const toNode = nodes.find(n => n.id === edge.to);
          if (toNode) {
            toNode.glowMesh.scale.multiplyScalar(1.2);
          }
        }
        continue;
      }

      const edge = edges[particle.edgeIndex];
      if (edge) {
        const curve = (edge.line as any).__curve as THREE.CatmullRomCurve3;
        if (curve) {
          const point = curve.getPoint(particle.progress);
          particle.mesh.position.copy(point);
          
          const trailProgress = Math.max(0, particle.progress - 0.05);
          const trailPoint = curve.getPoint(trailProgress);
          particle.trailMesh.position.copy(trailPoint);
          
          const alpha = Math.sin(particle.progress * Math.PI);
          (particle.mesh.material as THREE.SpriteMaterial).opacity = alpha;
          (particle.trailMesh.material as THREE.SpriteMaterial).opacity = alpha * 0.5;
          
          activeParticles.push(particle);
        } else {
          particle.alive = false;
          scene.remove(particle.mesh);
          scene.remove(particle.trailMesh);
        }
      } else {
        particle.alive = false;
        scene.remove(particle.mesh);
        scene.remove(particle.trailMesh);
      }
    }

    return activeParticles;
  }

  spawnParticle(edges: ExplorerEdge[], particles: FiringParticle[], scene: THREE.Scene, particleTexture: THREE.CanvasTexture, trailTexture: THREE.CanvasTexture): FiringParticle | null {
    const visibleEdges = edges.filter(e => e.line.visible);
    if (visibleEdges.length === 0) return null;

    const randomIndex = Math.floor(Math.random() * visibleEdges.length);
    const edge = visibleEdges[randomIndex];
    const edgeIndex = edges.indexOf(edge);
    
    let color = 0xffffff;
    if (edge.type === 'HEBBIAN') color = EDGE_TYPE_COLORS['HEBBIAN'];
    else if (edge.type === 'TEMPORAL') color = EDGE_TYPE_COLORS['TEMPORAL'];
    else if (edge.type === 'ENTITY') color = EDGE_TYPE_COLORS['ENTITY'];

    const particleMat = new THREE.SpriteMaterial({
      map: particleTexture,
      color: color,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    
    const trailMat = new THREE.SpriteMaterial({
      map: trailTexture,
      color: color,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });

    const mesh = new THREE.Sprite(particleMat);
    const trailMesh = new THREE.Sprite(trailMat);
    
    mesh.scale.set(0.6, 0.6, 1);
    trailMesh.scale.set(0.8, 0.8, 1);
    
    scene.add(mesh);
    scene.add(trailMesh);

    return {
      mesh,
      trailMesh,
      edgeIndex,
      progress: 0,
      speed: 0.5 + Math.random() * 0.5,
      alive: true,
      color
    };
  }

  dispose(scene: THREE.Scene, nodes: ExplorerNode[], edges: ExplorerEdge[], particles: FiringParticle[]): void {
    if (this.brainGroup) {
      scene.remove(this.brainGroup);
      this.brainGroup.children.forEach(c => {
        if (c instanceof THREE.Mesh) {
          if (c.geometry) c.geometry.dispose();
          if (c.material) (c.material as THREE.Material).dispose();
        }
      });
      this.brainGroup = null;
    }
    
    if (this.brainMist) {
      scene.remove(this.brainMist);
      if (this.brainMist.geometry) this.brainMist.geometry.dispose();
      if (this.brainMist.material) (this.brainMist.material as THREE.Material).dispose();
      this.brainMist = null;
    }
    
    for (const node of nodes) {
      scene.remove(node.mesh as any);
      scene.remove(node.glowMesh);
      if (node.labelSprite) scene.remove(node.labelSprite);
      
      const mesh = node.mesh as any;
      if (mesh.geometry) mesh.geometry.dispose();
      if (mesh.material) mesh.material.dispose();
      
      if (node.glowMesh.material) {
        const mat = node.glowMesh.material as THREE.SpriteMaterial;
        if (mat.map) mat.map.dispose();
        mat.dispose();
      }
      
      if (node.labelSprite && node.labelSprite.material) {
        const mat = node.labelSprite.material as THREE.SpriteMaterial;
        if (mat.map) mat.map.dispose();
        mat.dispose();
      }
    }
    
    for (const edge of edges) {
      scene.remove(edge.line as any);
      if (edge.labelSprite) scene.remove(edge.labelSprite);
      if (edge.weightSprite) scene.remove(edge.weightSprite);
      
      const mesh = edge.line as any;
      if (mesh.geometry) mesh.geometry.dispose();
      if (mesh.material) mesh.material.dispose();
      
      if (edge.labelSprite && edge.labelSprite.material) {
        const mat = edge.labelSprite.material as THREE.SpriteMaterial;
        if (mat.map) mat.map.dispose();
        mat.dispose();
      }
      
      if (edge.weightSprite && edge.weightSprite.material) {
        const mat = edge.weightSprite.material as THREE.SpriteMaterial;
        if (mat.map) mat.map.dispose();
        mat.dispose();
      }
    }
    
    for (const particle of particles) {
      scene.remove(particle.mesh);
      scene.remove(particle.trailMesh);
      if (particle.mesh.material) (particle.mesh.material as THREE.Material).dispose();
      if (particle.trailMesh.material) (particle.trailMesh.material as THREE.Material).dispose();
    }
    
    for (const terminal of this.synapticTerminals) {
      scene.remove(terminal);
      if (terminal.geometry) terminal.geometry.dispose();
      if (terminal.material) (terminal.material as THREE.Material).dispose();
    }
    
    this.synapticTerminals = [];
    this.dendriteCurves.clear();
    this.scene = null;
  }
}
