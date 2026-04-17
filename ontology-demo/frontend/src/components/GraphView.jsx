import { useRef, useEffect, useMemo } from 'react';
import cytoscape from 'cytoscape';
import dagre from 'cytoscape-dagre';
import { buildColorMap } from './graphConstants';

cytoscape.use(dagre);

const BASE_STYLESHEET = [
  // Rectangle nodes with 2-line label (Type + ID)
  {
    selector: 'node',
    style: {
      shape: 'round-rectangle',
      width: 120,
      height: 48,
      label: 'data(label)',
      'text-valign': 'center',
      'text-halign': 'center',
      'text-wrap': 'wrap',
      'text-max-width': '110px',
      'font-size': '9px',
      color: '#fff',
      'background-color': '#94a3b8',
    },
  },
  {
    selector: 'node.start-node',
    style: {
      width: 140,
      height: 56,
      'border-width': 3,
      'border-color': '#1e293b',
      'font-size': '10px',
      'font-weight': 'bold',
    },
  },
  // Edges: no label by default
  {
    selector: 'edge',
    style: {
      label: '',
      'curve-style': 'bezier',
      'target-arrow-shape': 'triangle',
      'arrow-scale': 0.8,
      'line-color': '#cbd5e1',
      'target-arrow-color': '#94a3b8',
      width: 1.5,
    },
  },
  // Show label on hover
  {
    selector: 'edge.hover',
    style: {
      label: 'data(label)',
      'font-size': '9px',
      color: '#475569',
      'text-background-color': '#fff',
      'text-background-opacity': 0.9,
      'text-background-padding': '2px',
      'line-color': '#64748b',
      'target-arrow-color': '#64748b',
      width: 2,
    },
  },
];

function toElements(graphResult) {
  const nodes = graphResult.nodes.map((n) => ({
    group: 'nodes',
    data: {
      id: n.key,
      label: `${n.type}\n${n.id}`,
      nodeType: n.type,
    },
  }));

  const edges = graphResult.edges.map((e) => ({
    group: 'edges',
    data: {
      id: `${e.type}|${e.from}|${e.to}`,
      source: e.from,
      target: e.to,
      label: e.type.replace(/_/g, ' '),
    },
  }));

  return [...nodes, ...edges];
}

export default function GraphView({ graphResult, onNodeClick }) {
  const containerRef = useRef(null);
  const cyRef = useRef(null);

  const colorMap = useMemo(() => {
    if (!graphResult) return {};
    const typeNames = [...new Set(graphResult.nodes.map((n) => n.type))];
    return buildColorMap(typeNames);
  }, [graphResult]);

  const stylesheet = useMemo(() => [
    ...BASE_STYLESHEET,
    ...Object.entries(colorMap).map(([type, color]) => ({
      selector: `node[nodeType="${type}"]`,
      style: { 'background-color': color },
    })),
  ], [colorMap]);

  useEffect(() => {
    if (!containerRef.current || !graphResult || !graphResult.nodes.length) return;

    if (cyRef.current) {
      cyRef.current.destroy();
      cyRef.current = null;
    }

    const elements = toElements(graphResult);
    const startKey = graphResult.start?.key;

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: stylesheet,
      layout: {
        name: 'dagre',
        rankDir: 'LR',           // Left → Right hierarchy (PLTR style)
        nodeSep: 30,             // Min vertical gap between siblings
        rankSep: 80,             // Min horizontal gap between depth columns
        edgeSep: 10,             // Min gap between edges
        animate: false,
        padding: 20,
        nodeDimensionsIncludeLabels: true,
      },
    });

    // Compute type rank from edge directions (not hardcoded)
    // Build type-level DAG: which types point to which types
    const typeChildren = {};
    const typeInDeg = {};
    const typesInGraph = new Set();
    graphResult.nodes.forEach((n) => typesInGraph.add(n.type));
    typesInGraph.forEach((t) => { typeChildren[t] = new Set(); typeInDeg[t] = 0; });
    graphResult.edges.forEach((e) => {
      const fromType = e.from.split(':')[0];
      const toType = e.to.split(':')[0];
      if (fromType !== toType && !typeChildren[fromType]?.has(toType)) {
        typeChildren[fromType]?.add(toType);
        typeInDeg[toType] = (typeInDeg[toType] || 0) + 1;
      }
    });

    // Longest path from roots (in-degree=0) → type rank
    const typeRank = {};
    Object.keys(typeInDeg).forEach((t) => {
      if (typeInDeg[t] === 0) typeRank[t] = 0;
    });
    let changed = true;
    while (changed) {
      changed = false;
      Object.entries(typeChildren).forEach(([parent, kids]) => {
        if (typeRank[parent] === undefined) return;
        kids.forEach((child) => {
          const nr = typeRank[parent] + 1;
          if (typeRank[child] === undefined || nr > typeRank[child]) {
            typeRank[child] = nr;
            changed = true;
          }
        });
      });
    }
    // Secondary roots: place just before their children, not at column 0
    // Main root = the root with the most descendants
    const roots = Object.keys(typeInDeg).filter((t) => typeInDeg[t] === 0);
    if (roots.length > 1) {
      const descCount = (t, visited = new Set()) => {
        if (visited.has(t)) return 0;
        visited.add(t);
        let c = 0;
        (typeChildren[t] || new Set()).forEach((ch) => { c += 1 + descCount(ch, visited); });
        return c;
      };
      const mainRoot = roots.reduce((a, b) => descCount(a) >= descCount(b) ? a : b);
      roots.forEach((r) => {
        if (r !== mainRoot && typeChildren[r]?.size > 0) {
          const childRanks = [...typeChildren[r]].map((c) => typeRank[c]).filter((v) => v != null);
          if (childRanks.length > 0) typeRank[r] = Math.min(...childRanks) - 1;
        }
      });
    }
    // Normalize: shift so minimum rank = 0
    const minRank = Math.min(...Object.values(typeRank));
    if (minRank < 0) Object.keys(typeRank).forEach((t) => { typeRank[t] -= minRank; });
    // Fallback: any unranked type gets max rank
    const maxRank = Math.max(0, ...Object.values(typeRank));
    typesInGraph.forEach((t) => { if (typeRank[t] === undefined) typeRank[t] = maxRank; });

    const COL_WIDTH = 160;
    const ROW_GAP = 70;

    // Group nodes into columns by type rank, keep dagre y for sort order
    const columns = {};
    cy.nodes().forEach((node) => {
      const rank = typeRank[node.data('nodeType')] ?? 0;
      if (!columns[rank]) columns[rank] = [];
      columns[rank].push({ node, dagreY: node.position().y });
    });

    // Within each column, cluster same types together (PLTR Vertex style)
    const TYPE_GROUP_GAP = 30; // extra gap between different type clusters
    Object.entries(columns).forEach(([rank, items]) => {
      // 1. Group by nodeType
      const typeGroups = {};
      items.forEach((item) => {
        const type = item.node.data('nodeType');
        if (!typeGroups[type]) typeGroups[type] = [];
        typeGroups[type].push(item);
      });

      // 2. Sort each type group internally by dagre Y
      //    Order type groups by their average dagre Y (preserve dagre's edge-crossing logic)
      const sortedGroups = Object.entries(typeGroups)
        .map(([type, groupItems]) => {
          groupItems.sort((a, b) => a.dagreY - b.dagreY);
          const avgY = groupItems.reduce((sum, it) => sum + it.dagreY, 0) / groupItems.length;
          return { type, items: groupItems, avgY };
        })
        .sort((a, b) => a.avgY - b.avgY);

      // 3. Place groups with gap between clusters
      const totalHeight = (items.length - 1) * ROW_GAP
        + (sortedGroups.length - 1) * TYPE_GROUP_GAP;
      let y = -totalHeight / 2;
      sortedGroups.forEach((group, gi) => {
        group.items.forEach((item) => {
          item.node.position({ x: Number(rank) * COL_WIDTH, y });
          y += ROW_GAP;
        });
        if (gi < sortedGroups.length - 1) y += TYPE_GROUP_GAP;
      });
    });

    cy.fit(undefined, 20);

    // Mark start node
    if (startKey) {
      const startNode = cy.getElementById(startKey);
      if (startNode.length) {
        startNode.addClass('start-node');
      }
    }

    // Edge hover — show label only on hover
    cy.on('mouseover', 'edge', (evt) => evt.target.addClass('hover'));
    cy.on('mouseout', 'edge', (evt) => evt.target.removeClass('hover'));

    // Node click
    cy.on('tap', 'node', (evt) => {
      const nodeKey = evt.target.data('id');
      if (onNodeClick) {
        const node = graphResult.nodes.find((n) => n.key === nodeKey);
        if (node) onNodeClick(node);
      }
    });

    // Background click to deselect
    cy.on('tap', (evt) => {
      if (evt.target === cy && onNodeClick) {
        onNodeClick(null);
      }
    });

    cyRef.current = cy;

    return () => {
      cy.destroy();
    };
  }, [graphResult, onNodeClick]);

  if (!graphResult || !graphResult.nodes.length) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
        未有圖形數據
      </div>
    );
  }

  return (
    <div className="relative h-full">
      {/* Legend */}
      <div className="absolute top-2 right-2 z-10 bg-white/90 border border-gray-200 rounded p-2 text-xs flex flex-wrap gap-x-3 gap-y-1">
        {Object.entries(colorMap).map(([type, color]) => (
          <span key={type} className="flex items-center gap-1">
            <span
              className="inline-block w-3 h-2 rounded-sm"
              style={{ backgroundColor: color }}
            />
            {type}
          </span>
        ))}
      </div>

      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </div>
  );
}
