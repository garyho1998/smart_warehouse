// 20-color Google Material Design palette (600–800 level, white-text safe)
// First 9 slots match the semantic colors of the built-in types.
// Slots 9–19 are auto-assigned to any new object types added to the schema.
const PALETTE = [
  // Spatial layer (slots 0-2) — blues
  '#1A73E8', // Warehouse  — Google Blue
  '#3949AB', // Zone       — Indigo 600
  '#0097A7', // Location   — Cyan 700
  // Inventory layer (slots 3-4) — greens
  '#388E3C', // Sku        — Green 700
  '#00796B', // Inventory  — Teal 700
  // Order layer (slots 5-6) — warm
  '#D32F2F', // WarehouseOrder — Red 700
  '#E64A19', // OrderLine     — Deep Orange 700
  // Operations layer (slots 7-8) — purples
  '#512DA8', // Robot — Deep Purple 700
  '#7B1FA2', // Task  — Purple 700
  // Auto-assign pool for new types (slots 9-19)
  '#C2185B', // Pink 700
  '#0288D1', // Light Blue 700
  '#689F38', // Light Green 700
  '#F57C00', // Orange 700
  '#5D4037', // Brown 700
  '#455A64', // Blue Grey 700
  '#00838F', // Cyan 800
  '#558B2F', // Light Green 800
  '#1565C0', // Blue 800
  '#6D4C41', // Brown 600
  '#283593', // Indigo 800
];

// Semantic seed: maps built-in type names to their fixed palette slot.
// New types not listed here get the next available slot in order.
const SEMANTIC_SLOTS = {
  Warehouse: 0, Zone: 1, Location: 2,
  Sku: 3, Inventory: 4,
  WarehouseOrder: 5, OrderLine: 6,
  Robot: 7, Task: 8,
};

// Call this once with the full list of type names from the schema API.
// Returns a stable map: typeName → hex color.
// Guaranteed no collision up to PALETTE.length (20) types.
export function buildColorMap(typeNames) {
  const map = {};
  let nextSlot = Object.keys(SEMANTIC_SLOTS).length; // start after reserved slots

  typeNames.forEach((name) => {
    if (SEMANTIC_SLOTS[name] !== undefined) {
      map[name] = PALETTE[SEMANTIC_SLOTS[name]];
    } else {
      map[name] = PALETTE[nextSlot % PALETTE.length];
      nextSlot++;
    }
  });
  return map;
}

// Legacy static map kept for components that haven't migrated yet.
// Built-in types only — unknown types get the fallback grey in Cytoscape.
export const TYPE_COLORS = Object.fromEntries(
  Object.entries(SEMANTIC_SLOTS).map(([name, slot]) => [name, PALETTE[slot]])
);

export const CARDINALITY_SHORT = {
  one_to_many: '1:N',
  many_to_one: 'N:1',
  one_to_one: '1:1',
  many_to_many: 'N:N',
};
