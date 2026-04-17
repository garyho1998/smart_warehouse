export default function TypeList({ types, selected, onSelect }) {
  return (
    <ul className="space-y-0.5">
      {types.map((t) => (
        <li key={t.id}>
          <button
            onClick={() => onSelect(t.id)}
            className={`w-full text-left px-3 py-2 rounded text-sm ${
              selected === t.id
                ? 'bg-indigo-100 text-indigo-700 font-medium'
                : 'text-gray-700 hover:bg-gray-100'
            }`}
          >
            {t.id}
            {t.description && (
              <span className="block text-xs text-gray-400 mt-0.5">{t.description}</span>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
}
