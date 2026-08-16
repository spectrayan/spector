// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Galaxy View Strategy
// Copyright (c) 2025–2026 Spectrayan. Licensed under BSL 1.1.
// ═══════════════════════════════════════════════════════════════════════

import * as THREE from 'three';
import { GraphNode, GraphEdge } from '../../../core/services/memory-table.service';
import { GraphViewStrategy, ExplorerNode, ExplorerEdge, FiringParticle } from './view-strategy.interface';

const TIER_COLORS: Record<string, number> = {
  WORKING: 0xffb74d,
  EPISODIC: 0x66bb6a,
  SEMANTIC: 0x42a5f5,
  PROCEDURAL: 0xab47bc,
};

const EDGE_TYPE_COLORS: Record<string, number> = {
  HEBBIAN: 0x00ffcc,   // Cyan-green for sci-fi feel
  TEMPORAL: 0x00bcd4,
  ENTITY: 0xffc107,
};

export class GalaxyViewStrategy implements GraphViewStrategy {
  readonly name = 'constellation';

  private gridGroup: THREE.Group | null = null;
  private dustParticles: THREE.Points | null = null;
  private starTextureCache = new Map<string, THREE.CanvasTexture>();
  private edgeLabelTextureCache = new Map<string, THREE.CanvasTexture>();

  private getOrCreateStarTexture(color: number, intensity: number): THREE.CanvasTexture {
    const key = `${color}_${intensity}`;
    let tex = this.starTextureCache.get(key);
    if (!tex) {
      tex = this.createStarTexture(color, intensity);
      this.starTextureCache.set(key, tex);
    }
    return tex;
  }

  private getOrCreateEdgeLabelTexture(text: string, color: number): THREE.CanvasTexture {
    const key = `${color}_${text}`;
    let tex = this.edgeLabelTextureCache.get(key);
    if (!tex) {
      tex = this.createEdgeLabelTexture(text, color);
      this.edgeLabelTextureCache.set(key, tex);
    }
    return tex;
  }

  initScene(container: HTMLElement, camera: THREE.PerspectiveCamera): THREE.Scene {
    const scene = new THREE.Scene();

    scene.add(new THREE.AmbientLight(0xffffff, 0.4));
    const pointLight = new THREE.PointLight(0xffffff, 0.8, 200);
    pointLight.position.copy(camera.position);
    scene.add(pointLight);

    // Hexagonal wireframe grid
    this.gridGroup = new THREE.Group();
    this.createHexGrid(this.gridGroup);
    scene.add(this.gridGroup);

    // Cosmic dust particles (nebula)
    this.createDustField(scene);

    return scene;
  }

  addNodes(apiNodes: GraphNode[], existingNodes: ExplorerNode[], warpIn: boolean): ExplorerNode[] {
    const newNodes: ExplorerNode[] = [];
    const nodeIdMap = new Map<string, ExplorerNode>();
    for (const n of existingNodes) nodeIdMap.set(n.id, n);

    for (let i = 0; i < apiNodes.length; i++) {
      const n = apiNodes[i];
      if (nodeIdMap.has(n.id)) continue; // Skip duplicates

      const color = TIER_COLORS[n.tier] ?? 0x888888;
      const importance = Math.max(0.1, Math.min(1.0, n.importance / 10));
      const offset = existingNodes.length + newNodes.length;

      // Volumetric golden spiral
      const golden = (1 + Math.sqrt(5)) / 2;
      const theta = (2 * Math.PI * offset) / golden;
      const phi = Math.acos(1 - (2 * (offset + 0.5)) / Math.max(1, apiNodes.length + existingNodes.length));
      const baseRadius = 15 + importance * 50;
      const jitter = 1.0 + Math.sin(offset * 7.31) * 0.4;
      const radius = baseRadius * jitter;

      const pos = new THREE.Vector3(
        radius * Math.sin(phi) * Math.cos(theta),
        radius * Math.sin(phi) * Math.sin(theta),
        radius * Math.cos(phi),
      );

      const size = 0.3 + importance * 0.6;

      // Star core sprite (cached texture)
      const starTex = this.getOrCreateStarTexture(color, 1.0);
      const starMat = new THREE.SpriteMaterial({
        map: starTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const mesh = new THREE.Sprite(starMat);
      mesh.scale.set(warpIn ? 0 : size * 3, warpIn ? 0 : size * 3, 1);
      mesh.position.copy(pos);

      // Outer glow halo sprite (cached texture)
      const glowTex = this.getOrCreateStarTexture(color, 0.3);
      const glowMat = new THREE.SpriteMaterial({
        map: glowTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const glowMesh = new THREE.Sprite(glowMat);
      glowMesh.scale.set(warpIn ? 0 : size * 8, warpIn ? 0 : size * 8, 1);
      glowMesh.position.copy(pos);

      // Node label sprite
      const labelSprite = this.createNodeLabel(n.id, n.tier, importance, color);
      labelSprite.position.copy(pos);
      labelSprite.position.y += size * 3 + 0.8;

      const explorerNode: ExplorerNode = {
        id: n.id,
        tier: n.tier,
        text: n.textPreview || '',
        importance: n.importance,
        valence: n.valence ?? 0,
        timestampMs: n.timestampMs ?? Date.now(),
        position: pos,
        velocity: new THREE.Vector3(
          (Math.random() - 0.5) * 0.005,
          (Math.random() - 0.5) * 0.005,
          (Math.random() - 0.5) * 0.005,
        ),
        mesh,
        glowMesh,
        labelSprite,
        selected: false,
        baseSize: size,
        visible: true,
        targetOpacity: 1.0,
      };
      newNodes.push(explorerNode);
    }
    return newNodes;
  }

  addNodesAroundParent(apiNodes: GraphNode[], existingNodes: ExplorerNode[], parent: ExplorerNode): ExplorerNode[] {
    const newNodes: ExplorerNode[] = [];
    const nodeIdMap = new Map<string, ExplorerNode>();
    for (const n of existingNodes) nodeIdMap.set(n.id, n);

    const golden = (1 + Math.sqrt(5)) / 2;

    for (let i = 0; i < apiNodes.length; i++) {
      const n = apiNodes[i];
      if (nodeIdMap.has(n.id)) continue;

      const color = TIER_COLORS[n.tier] ?? 0x888888;
      const importance = Math.max(0.1, Math.min(1.0, n.importance / 10));

      // Radial burst around parent: golden spiral offset
      const theta = (2 * Math.PI * i) / golden;
      const phi = Math.acos(1 - (2 * (i + 0.5)) / Math.max(1, apiNodes.length));
      const burstRadius = 8 + importance * 15;

      const pos = new THREE.Vector3(
        parent.position.x + burstRadius * Math.sin(phi) * Math.cos(theta),
        parent.position.y + burstRadius * Math.sin(phi) * Math.sin(theta),
        parent.position.z + burstRadius * Math.cos(phi),
      );

      const size = 0.3 + importance * 0.6;

      // Star core sprite (cached texture)
      const starTex = this.getOrCreateStarTexture(color, 1.0);
      const starMat = new THREE.SpriteMaterial({
        map: starTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const mesh = new THREE.Sprite(starMat);
      mesh.scale.set(0, 0, 1); // warp-in from zero
      mesh.position.copy(pos);

      // Outer glow halo (cached texture)
      const glowTex = this.getOrCreateStarTexture(color, 0.3);
      const glowMat = new THREE.SpriteMaterial({
        map: glowTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const glowMesh = new THREE.Sprite(glowMat);
      glowMesh.scale.set(0, 0, 1);
      glowMesh.position.copy(pos);

      // Node label
      const labelSprite = this.createNodeLabel(n.id, n.tier, importance, color);
      labelSprite.position.copy(pos);
      labelSprite.position.y += size * 3 + 0.8;

      const explorerNode: ExplorerNode = {
        id: n.id,
        tier: n.tier,
        text: n.textPreview || '',
        importance: n.importance,
        valence: n.valence ?? 0,
        timestampMs: n.timestampMs ?? Date.now(),
        position: pos,
        velocity: new THREE.Vector3(
          (Math.random() - 0.5) * 0.003,
          (Math.random() - 0.5) * 0.003,
          (Math.random() - 0.5) * 0.003,
        ),
        mesh,
        glowMesh,
        labelSprite,
        selected: false,
        baseSize: size,
        visible: true,
        targetOpacity: 1.0,
      };
      newNodes.push(explorerNode);
    }
    return newNodes;
  }

  addEdges(apiEdges: GraphEdge[], nodes: ExplorerNode[], existingEdges: ExplorerEdge[]): ExplorerEdge[] {
    const newEdges: ExplorerEdge[] = [];
    const nodeMap = new Map(nodes.map(n => [n.id, n]));

    for (const e of apiEdges) {
      const fromNode = nodeMap.get(e.fromId);
      const toNode = nodeMap.get(e.toId);
      if (!fromNode || !toNode) continue;

      // Skip if already exists
      if (existingEdges.some(ex => ex.from === e.fromId && ex.to === e.toId && ex.type === e.type)) continue;

      const color = EDGE_TYPE_COLORS[e.type] ?? 0x888888;
      const material =
        e.type === 'TEMPORAL'
          ? new THREE.LineDashedMaterial({
              color,
              transparent: true,
              opacity: 0.35,
              dashSize: 1,
              gapSize: 0.5,
            })
          : new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.3 });

      const geo = new THREE.BufferGeometry().setFromPoints([fromNode.position, toNode.position]);
      const line = new THREE.Line(geo, material);
      if (e.type === 'TEMPORAL') line.computeLineDistances();

      let labelSprite: THREE.Sprite | undefined;
      if (e.type === 'ENTITY' && e.relation) {
        labelSprite = this.createEdgeLabel(e.relation, fromNode.position, toNode.position, color);
      }

      let weightSprite: THREE.Sprite | undefined;
      if (e.type === 'HEBBIAN' && e.weight > 0.05) {
        weightSprite = this.createWeightBadge(e.weight, fromNode.position, toNode.position);
      }

      newEdges.push({
        from: e.fromId,
        to: e.toId,
        fromNode,
        toNode,
        type: e.type,
        weight: e.weight,
        relation: e.relation,
        fromEntityType: e.fromEntityType,
        toEntityType: e.toEntityType,
        line,
        labelSprite,
        weightSprite,
      });
    }

    return newEdges;
  }

  animateNodes(nodes: ExplorerNode[], delta: number, time: number, showLabels: boolean, cameraPos: THREE.Vector3, recallMode: boolean, recallMatchedIds: Set<string>): void {
    if (this.dustParticles) {
      this.dustParticles.rotation.y += 0.001 * delta;
    }

    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      node.position.add(node.velocity);
      node.mesh.position.copy(node.position);
      node.glowMesh.position.copy(node.position);
      if (node.position.length() > 120) node.velocity.multiplyScalar(-1);

      // Ghost-fade animation: smoothly lerp opacity
      const coreMat = node.mesh.material as THREE.SpriteMaterial;
      const glowMat = node.glowMesh.material as THREE.SpriteMaterial;
      const labelMat = node.labelSprite.material as THREE.SpriteMaterial;

      const currentOpacity = coreMat.opacity;
      const targetOpacity = node.targetOpacity;
      const newOpacity = currentOpacity + (targetOpacity - currentOpacity) * Math.min(1, delta * 4);
      coreMat.opacity = newOpacity;
      glowMat.opacity = newOpacity * 0.5;
      labelMat.opacity = node.visible ? 1.0 : 0.0;

      // Star pulsing — recall mode matched nodes get sonar pulse
      const isRecallMatched = recallMode && recallMatchedIds.has(node.id);
      const pulseAmp = isRecallMatched ? 0.5 : 0.15;
      const pulseSpeed = isRecallMatched ? 3.0 : 1.5;
      const pulse = 1.0 + pulseAmp * Math.sin(time * pulseSpeed + i * 0.7);
      const coreScale = node.baseSize * 3 * pulse * (node.visible ? 1.0 : 0.5);
      node.mesh.scale.set(coreScale, coreScale, 1);
      const glowMultiplier = isRecallMatched ? 12 : 8;
      const glowScale = node.baseSize * glowMultiplier * pulse * (node.visible ? 1.0 : 0.3);
      node.glowMesh.scale.set(glowScale, glowScale, 1);

      // Warp-in: scale from 0 to target
      if (coreScale > 0 && node.mesh.scale.x < node.baseSize * 2) {
        const warpScale = Math.min(node.baseSize * 3, node.mesh.scale.x + delta * node.baseSize * 10);
        node.mesh.scale.set(warpScale, warpScale, 1);
        node.glowMesh.scale.set(warpScale * 2.5, warpScale * 2.5, 1);
      }

      // Label position
      node.labelSprite.position.copy(node.position);
      node.labelSprite.position.y += node.baseSize * 4 + 2.0;
      node.labelSprite.visible = showLabels && node.visible;

      if (showLabels && node.visible) {
        const dist = cameraPos.distanceTo(node.labelSprite.position);
        const s = dist * 0.06;
        node.labelSprite.scale.set(s * 3.2, s, 1);
      }
    }
  }

  animateEdges(edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, showHebbian: boolean, showTemporal: boolean, showEntity: boolean, showLabels: boolean, orbitRadius: number, cameraPos: THREE.Vector3): void {
    for (let i = 0; i < edges.length; i++) {
      const edge = edges[i];
      const fromNode = edge.fromNode;
      const toNode = edge.toNode;
      if (fromNode && toNode) {
        const positions = edge.line.geometry.attributes['position'] as THREE.BufferAttribute;
        positions.setXYZ(0, fromNode.position.x, fromNode.position.y, fromNode.position.z);
        positions.setXYZ(1, toNode.position.x, toNode.position.y, toNode.position.z);
        positions.needsUpdate = true;

        if (edge.labelSprite) {
          const mid = new THREE.Vector3()
            .addVectors(fromNode.position, toNode.position)
            .multiplyScalar(0.5);
          mid.y += 0.4;
          edge.labelSprite.position.copy(mid);
          const dist = cameraPos.distanceTo(mid);
          const s = dist * 0.035;
          edge.labelSprite.scale.set(s * 2.5, s * 0.5, 1);
        }
        if (edge.weightSprite) {
          const mid = new THREE.Vector3()
            .addVectors(fromNode.position, toNode.position)
            .multiplyScalar(0.5);
          mid.y -= 1.2;
          edge.weightSprite.position.copy(mid);
          const dist = cameraPos.distanceTo(mid);
          const s = dist * 0.04;
          edge.weightSprite.scale.set(s * 2.5, s, 1);
        }
      }

      // Layer visibility + filter: both endpoints must be visible
      const bothVisible = fromNode?.visible && toNode?.visible;
      const layerVisible =
        (edge.type === 'HEBBIAN' && showHebbian) ||
        (edge.type === 'TEMPORAL' && showTemporal) ||
        (edge.type === 'ENTITY' && showEntity);
      edge.line.visible = layerVisible && !!bothVisible;
      if (edge.labelSprite) {
        edge.labelSprite.visible = edge.line.visible && orbitRadius < 150;
      }
      if (edge.weightSprite) {
        edge.weightSprite.visible = edge.line.visible && showLabels;
      }
    }
  }

  animateParticles(particles: FiringParticle[], edges: ExplorerEdge[], nodes: ExplorerNode[], delta: number, scene: THREE.Scene): FiringParticle[] {
    for (const particle of particles) {
      if (!particle.alive) continue;

      particle.progress += particle.speed;
      if (particle.progress >= 1) {
        particle.alive = false;
        scene.remove(particle.mesh);
        particle.mesh.material.dispose();
        scene.remove(particle.trailMesh);
        particle.trailMesh.material.dispose();
        continue;
      }

      const edge = edges[particle.edgeIndex];
      if (!edge || !edge.line.visible) {
        particle.alive = false;
        scene.remove(particle.mesh);
        particle.mesh.material.dispose();
        scene.remove(particle.trailMesh);
        particle.trailMesh.material.dispose();
        continue;
      }

      const fromNode = edge.fromNode || nodes.find(n => n.id === edge.from);
      const toNode = edge.toNode || nodes.find(n => n.id === edge.to);
      if (!fromNode || !toNode) continue;

      const pos = new THREE.Vector3();
      pos.lerpVectors(fromNode.position, toNode.position, particle.progress);
      particle.mesh.position.copy(pos);

      // Trail follows behind
      const trailProgress = Math.max(0, particle.progress - 0.1);
      particle.trailMesh.position.lerpVectors(fromNode.position, toNode.position, trailProgress);

      // Fade in/out along path
      const alpha = Math.sin(particle.progress * Math.PI);
      (particle.mesh.material as THREE.SpriteMaterial).opacity = alpha * 0.95;
      (particle.trailMesh.material as THREE.SpriteMaterial).opacity = alpha * 0.3;
      particle.mesh.scale.setScalar(1.0 + alpha * 0.8);

      // Pulse the source/destination nodes when particle arrives
      if (particle.progress > 0.85) {
        const glowMat = toNode.glowMesh.material as THREE.SpriteMaterial;
        glowMat.opacity = Math.min(1, glowMat.opacity + delta * 2);
        toNode.glowMesh.scale.setScalar(toNode.baseSize * 10);
      }
      if (particle.progress < 0.15) {
        const glowMat = fromNode.glowMesh.material as THREE.SpriteMaterial;
        glowMat.opacity = Math.min(1, glowMat.opacity + delta * 2);
        fromNode.glowMesh.scale.setScalar(fromNode.baseSize * 10);
      }
    }

    return particles.filter(p => p.alive);
  }

  spawnParticle(edges: ExplorerEdge[], particles: FiringParticle[], scene: THREE.Scene, particleTexture: THREE.CanvasTexture, trailTexture: THREE.CanvasTexture): FiringParticle | null {
    if (edges.length === 0) return null;

    // Pick a random visible edge
    const visibleEdges = edges.filter(e => e.line.visible);
    if (visibleEdges.length === 0) return null;
    const edge = visibleEdges[Math.floor(Math.random() * visibleEdges.length)];
    const edgeIndex = edges.indexOf(edge);
    const color = EDGE_TYPE_COLORS[edge.type] ?? 0x00ffcc;

    // Create colored particle texture
    const particleMat = new THREE.SpriteMaterial({
      map: particleTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthTest: false,
      color: new THREE.Color(color),
    });
    const mesh = new THREE.Sprite(particleMat);
    mesh.scale.set(1.5, 1.5, 1);
    scene.add(mesh);

    // Trail
    const trailMat = new THREE.SpriteMaterial({
      map: trailTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthTest: false,
      color: new THREE.Color(color),
    });
    const trailMesh = new THREE.Sprite(trailMat);
    trailMesh.scale.set(3, 3, 1);
    scene.add(trailMesh);

    return {
      mesh,
      trailMesh,
      edgeIndex,
      progress: 0,
      speed: 0.015 + Math.random() * 0.025,
      alive: true,
      color,
    };
  }

  dispose(scene: THREE.Scene, nodes: ExplorerNode[], edges: ExplorerEdge[], particles: FiringParticle[]): void {
    this.starTextureCache.forEach(t => t.dispose());
    this.starTextureCache.clear();
    this.edgeLabelTextureCache.forEach(t => t.dispose());
    this.edgeLabelTextureCache.clear();

    // Dispose nodes
    for (const node of nodes) {
      scene.remove(node.mesh);
      scene.remove(node.glowMesh);
      scene.remove(node.labelSprite);
      (node.mesh.material as THREE.SpriteMaterial).map?.dispose();
      node.mesh.material.dispose();
      (node.glowMesh.material as THREE.SpriteMaterial).map?.dispose();
      node.glowMesh.material.dispose();
      (node.labelSprite.material as THREE.SpriteMaterial).map?.dispose();
      node.labelSprite.material.dispose();
    }
    // Dispose edges
    for (const edge of edges) {
      scene.remove(edge.line);
      edge.line.geometry.dispose();
      if (edge.labelSprite) {
        scene.remove(edge.labelSprite);
        (edge.labelSprite.material as THREE.SpriteMaterial).map?.dispose();
        edge.labelSprite.material.dispose();
      }
      if (edge.weightSprite) {
        scene.remove(edge.weightSprite);
        (edge.weightSprite.material as THREE.SpriteMaterial).map?.dispose();
        edge.weightSprite.material.dispose();
      }
    }
    // Dispose particles
    for (const p of particles) {
      scene.remove(p.mesh);
      p.mesh.material.dispose();
      scene.remove(p.trailMesh);
      p.trailMesh.material.dispose();
    }
    // Dispose internally created elements
    if (this.gridGroup) {
      scene.remove(this.gridGroup);
      this.gridGroup.children.forEach(c => {
        if (c instanceof THREE.Line) {
          c.geometry.dispose();
          if (Array.isArray(c.material)) c.material.forEach(m => m.dispose());
          else c.material.dispose();
        }
      });
      this.gridGroup = null;
    }
    if (this.dustParticles) {
      scene.remove(this.dustParticles);
      this.dustParticles.geometry.dispose();
      if (Array.isArray(this.dustParticles.material)) this.dustParticles.material.forEach(m => m.dispose());
      else this.dustParticles.material.dispose();
      this.dustParticles = null;
    }
  }

  // ── Helper Methods ────────────────────────────────────────

  private createHexGrid(group: THREE.Group): void {
    const gridMat = new THREE.LineBasicMaterial({
      color: 0x00ffcc,
      transparent: true,
      opacity: 0.03,
    });

    // Equatorial and meridian circles with hex feel
    for (let i = 0; i < 3; i++) {
      const curve = new THREE.EllipseCurve(0, 0, 45, 45, 0, Math.PI * 2, false, 0);
      const pts = curve.getPoints(6); // hexagonal shape
      pts.push(pts[0].clone()); // close the hexagon
      const geo = new THREE.BufferGeometry().setFromPoints(
        pts.map((p) => {
          if (i === 0) return new THREE.Vector3(p.x, 0, p.y);
          if (i === 1) return new THREE.Vector3(p.x, p.y, 0);
          return new THREE.Vector3(0, p.x, p.y);
        }),
      );
      group.add(new THREE.Line(geo, gridMat));
    }

    // Concentric hex rings
    for (const r of [15, 30, 60]) {
      const curve = new THREE.EllipseCurve(0, 0, r, r, 0, Math.PI * 2, false, 0);
      const pts = curve.getPoints(6);
      pts.push(pts[0].clone());
      const geo = new THREE.BufferGeometry().setFromPoints(
        pts.map((p) => new THREE.Vector3(p.x, 0, p.y)),
      );
      group.add(
        new THREE.Line(
          geo,
          new THREE.LineBasicMaterial({ color: 0x00ffcc, transparent: true, opacity: 0.02 }),
        ),
      );
    }
  }

  private createDustField(scene: THREE.Scene): void {
    const count = 800;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const sizes = new Float32Array(count);

    for (let i = 0; i < count; i++) {
      const r = 50 + Math.random() * 120;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(2 * Math.random() - 1);
      positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      positions[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
      positions[i * 3 + 2] = r * Math.cos(phi);

      // Cyan-teal palette
      const brightness = 0.2 + Math.random() * 0.4;
      colors[i * 3] = brightness * 0.3;
      colors[i * 3 + 1] = brightness * 0.8;
      colors[i * 3 + 2] = brightness * 1.0;

      sizes[i] = 0.06 + Math.random() * 0.12;
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    geo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    const mat = new THREE.PointsMaterial({
      size: 0.12,
      vertexColors: true,
      transparent: true,
      opacity: 0.5,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });

    this.dustParticles = new THREE.Points(geo, mat);
    scene.add(this.dustParticles);
  }

  private createStarTexture(color: number, intensity: number): THREE.CanvasTexture {
    const size = 128;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    const r = (color >> 16) & 0xff;
    const g = (color >> 8) & 0xff;
    const b = color & 0xff;

    const cx = size / 2;
    const cy = size / 2;
    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2);

    if (intensity > 0.6) {
      grad.addColorStop(0.0, `rgba(255, 255, 255, ${intensity})`);
      grad.addColorStop(0.15, `rgba(${r}, ${g}, ${b}, ${intensity * 0.9})`);
      grad.addColorStop(0.4, `rgba(${r}, ${g}, ${b}, ${intensity * 0.4})`);
      grad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
    } else {
      grad.addColorStop(0.0, `rgba(${r}, ${g}, ${b}, ${intensity})`);
      grad.addColorStop(0.3, `rgba(${r}, ${g}, ${b}, ${intensity * 0.5})`);
      grad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
    }

    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, size, size);

    if (intensity > 0.6) {
      ctx.globalCompositeOperation = 'lighter';
      const spikeGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2);
      spikeGrad.addColorStop(0.0, `rgba(255, 255, 255, 0.4)`);
      spikeGrad.addColorStop(0.5, `rgba(${r}, ${g}, ${b}, 0.05)`);
      spikeGrad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
      ctx.fillStyle = spikeGrad;
      ctx.fillRect(0, cy - 1, size, 2);
      ctx.fillRect(cx - 1, 0, 2, size);
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    return texture;
  }

  private createNodeLabel(
    id: string,
    tier: string,
    importance: number,
    color: number,
  ): THREE.Sprite {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 512;
    canvas.height = 160;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const tierCode = tier.substring(0, 3).toUpperCase();
    const impPct = Math.round(importance * 100);
    const shortId = id.replace('mem-', '#');
    const displayText = tierCode + ' ' + shortId;
    const impText = '\u2b24 ' + impPct + '%';

    const hexColor = '#' + color.toString(16).padStart(6, '0');

    const textMetrics = (() => {
      ctx.font = 'bold 26px Consolas, "Courier New", monospace';
      return ctx.measureText(displayText);
    })();
    const pillW = Math.max(textMetrics.width + 32, 140);
    const pillH = 42;
    const pillX = (canvas.width - pillW) / 2;
    const pillY = 24;
    ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
    ctx.beginPath();
    ctx.roundRect(pillX, pillY, pillW, pillH, 10);
    ctx.fill();

    ctx.globalAlpha = 0.9;
    ctx.font = 'bold 26px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = hexColor;
    ctx.fillText(displayText, canvas.width / 2, pillY + pillH / 2);

    ctx.globalAlpha = 0.5;
    const barW = 80;
    const barH = 4;
    const barX = (canvas.width - barW) / 2;
    const barY = pillY + pillH + 10;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.12)';
    ctx.beginPath();
    ctx.roundRect(barX, barY, barW, barH, 2);
    ctx.fill();
    ctx.fillStyle = hexColor;
    ctx.beginPath();
    ctx.roundRect(barX, barY, barW * Math.min(1, importance), barH, 2);
    ctx.fill();

    ctx.globalAlpha = 0.7;
    ctx.font = '600 18px Consolas, "Courier New", monospace';
    ctx.fillStyle = '#bbc4dd';
    ctx.fillText(impText, canvas.width / 2, barY + barH + 16);

    ctx.globalAlpha = 1.0;

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(8, 2.5, 1);
    return sprite;
  }

  private createEdgeLabelTexture(text: string, color: number): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 256;
    canvas.height = 40;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const hexColor = '#' + color.toString(16).padStart(6, '0');
    ctx.font = '600 16px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.globalAlpha = 0.85;
    ctx.fillStyle = hexColor;
    ctx.fillText(text, canvas.width / 2, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    return texture;
  }

  private createEdgeLabel(
    text: string,
    from: THREE.Vector3,
    to: THREE.Vector3,
    color: number,
  ): THREE.Sprite {
    const texture = this.getOrCreateEdgeLabelTexture(text, color);
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(4, 0.7, 1);
    return sprite;
  }

  private createWeightBadge(weight: number, from: THREE.Vector3, to: THREE.Vector3): THREE.Sprite {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 192;
    canvas.height = 80;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.45)';
    ctx.beginPath();
    ctx.roundRect(40, 16, 112, 48, 10);
    ctx.fill();

    ctx.font = 'bold 28px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
    ctx.fillText(weight.toFixed(2), canvas.width / 2, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(4, 1.6, 1);
    return sprite;
  }
}

