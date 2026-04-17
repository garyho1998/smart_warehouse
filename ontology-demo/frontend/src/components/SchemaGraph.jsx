import { useRef, useEffect } from 'react';
import cytoscape from 'cytoscape';
import dagre from 'cytoscape-dagre';
import { TYPE_COLORS, CARDINALITY_SHORT } from './graphConstants';

cytoscape.use(dagre);

const stylesheet = [
  // ── Nodes ──
  {
    selector: 'node',
    style: {
      shape: 'round-rectangle',
      width: 140,
      height: 56,
      label: 'data(label)',
      'text-valign': 'center',
      'text-halign': 'center',
      'text-wrap': 'wrap',
      'text-max-width': '130px',
      'font-size': '10px',
      color: '#fff',
      'background-color': '#94a3b8',
    },
  },
  ...Object.entries(TYPE_COLORS).map(([type, color]) => ({
    selector: `node[id="${type}"]`,
    style: { 'background-color': color },
  })),

  // ── Edges: default — show only cardinality badge ──
  {
    selector: 'edge',
    style: {
      label: 'data(badge)',
      'font-size': '8px',
      'text-background-color': '#fff',
      'text-background-opacity': 0.85,
      'text-background-padding': '2px',
      'curve-style': 'bezier',
      'target-arrow-shape': 'triangle',
      'arrow-scale': 0.8,
      'line-color': '#94a3b8',
      'target-arrow-color': '#64748b',
      width: 1.5,
      color: '#64748b',
    },
  },
  // Hover — show full relationship name + cardinality (aligned with GraphView)
  {
    selector: 'edge.hover',
    style: {
      label: 'data(fullLabel)',
      'font-size': '9px',
      'text-wrap': 'wrap',
      'text-max-width': '160px',
      color: '#475569',
      'text-background-color': '#fff',
      'text-background-opacity': 0.9,
      'text-background-padding': '2px',
      'line-color': '#64748b',
      'target-arrow-color': '#64748b',
      width: 2,
    },
  },
  // Reference edges (many_to_one) — dashed line
  {
    selector: 'edge.reference-edge',
    style: {
      'line-style': 'dashed',
      'line-dash-pattern': [6, 4],
      'line-color': '#a78bfa',
      'target-arrow-color': '#7c3aed',
    },
  },
];

function toSchemaElements(types, links) {
  const nodes = types.map((t) => ({
    group: 'nodes',
    data: {
      id: t.id,
      label: `${t.id}\n${(t.description || '').slice(0, 20)}`,
    },
  }));

  const edges = links.map((l) => ({
    group: 'edges',
    data: {
      id: l.id,
      source: l.fromType,
      target: l.toType,
      badge: CARDINALITY_SHORT[l.cardinality] || l.cardinality,
      fullLabel: `${l.id.replace(/_/g, ' ')}\n${CARDINALITY_SHORT[l.cardinality] || l.cardinality}`,
      cardinality: l.cardinality,
    },
    classes: l.cardinality === 'many_to_one' ? 'reference-edge' : '',
  }));

  return [...nodes, ...edges];
}

/**
 * Compute type-rank columns using only one_to_many links.
 * many_to_one links are reference/lookup edges and should not
 * affect the hierarchical placement.
 */
function computeTypeRanks(types, links) {
  // Build type-level DAG from one_to_many links only
  const typeChildren = {};
  const typeInDeg = {};
  const allTypes = new Set(types.map((t) => t.id));
  allTypes.forEach((t) => { typeChildren[t] = new Set(); typeInDeg[t] = 0; });

  links.forEach((l) => {
    if (l.cardinality !== 'one_to_many') return;
    const from = l.fromType;
    const to = l.toType;
    if (from !== to && !typeChildren[from]?.has(to)) {
      typeChildren[from]?.add(to);
      typeInDeg[to] = (typeInDeg[to] || 0) + 1;
    }
  });

  // Longest path from roots (in-degree = 0)
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
  allTypes.forEach((t) => { if (typeRank[t] === undefined) typeRank[t] = maxRank; });

  return typeRank;
}

export default function SchemaGraph({ types, links, onNodeClick }) {
  const containerRef = useRef(null);
  const cyRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current || !types || !types.length) return;

    if (cyRef.current) {
      cyRef.current.destroy();
      cyRef.current = null;
    }

    const elements = toSchemaElements(types, links || []);

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: stylesheet,
      layout: {
        name: 'dagre',
        rankDir: 'LR',
        nodeSep: 50,
        rankSep: 100,
        edgeSep: 15,
        animate: false,
        padding: 40,
        nodeDimensionsIncludeLabels: true,
      },
    });

    // ── Type-rank repositioning ──
    const typeRank = computeTypeRanks(types, links || []);
    const COL_WIDTH = 180;
    const ROW_GAP = 80;

    // Group nodes into columns by their type rank
    const columns = {};
    cy.nodes().forEach((node) => {
      const rank = typeRank[node.data('id')] ?? 0;
      if (!columns[rank]) columns[rank] = [];
      columns[rank].push({ node, dagreY: node.position().y });
    });

    // Position nodes in each column, sorted by dagre Y
    Object.entries(columns).forEach(([rank, items]) => {
      items.sort((a, b) => a.dagreY - b.dagreY);
      const totalHeight = (items.length - 1) * ROW_GAP;
      let y = -totalHeight / 2;
      items.forEach((item) => {
        item.node.position({ x: Number(rank) * COL_WIDTH, y });
        y += ROW_GAP;
      });
    });

    cy.fit(undefined, 30);

    // ── Edge hover — show full label on hover ──
    cy.on('mouseover', 'edge', (evt) => evt.target.addClass('hover'));
    cy.on('mouseout', 'edge', (evt) => evt.target.removeClass('hover'));

    // ── Node click ──
    cy.on('tap', 'node', (evt) => {
      const typeId = evt.target.data('id');
      if (onNodeClick) {
        const typeDef = types.find((t) => t.id === typeId);
        if (typeDef) onNodeClick(typeDef);
      }
    });

    cy.on('tap', (evt) => {
      if (evt.target === cy && onNodeClick) {
        onNodeClick(null);
      }
    });

    cyRef.current = cy;

    return () => {
      cy.destroy();
    };
  }, [types, links, onNodeClick]);

  if (!types || !types.length) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
        載入中...
      </div>
    );
  }

  return (
    <div className="relative h-full">
      <div className="absolute top-2 right-2 z-10 bg-white/90 border border-gray-200 rounded p-2 text-xs flex flex-wrap gap-x-3 gap-y-1">
        {Object.entries(TYPE_COLORS).map(([type, color]) => (
          <span key={type} className="flex items-center gap-1">
            <span
              className="inline-block w-3 h-2 rounded-sm"
              style={{ backgroundColor: color }}
            />
            {type}
          </span>
        ))}
      </div>
      <div className="absolute top-2 left-2 z-10 bg-white/90 border border-gray-200 rounded px-2 py-1 text-xs text-gray-500">
        {types.length} 類型 &middot; {(links || []).length} 關係
        <span className="ml-2 text-gray-400">
          <span className="inline-block w-4 border-t border-gray-400 align-middle mr-1" />實線=containment
          <span className="inline-block w-4 border-t border-dashed border-purple-400 align-middle ml-2 mr-1" />虛線=reference
        </span>
      </div>
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </div>
  );
}
