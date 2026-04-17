import { NavLink, Outlet } from 'react-router-dom';

const links = [
  { to: '/ai', label: 'AI 助手' },
  { to: '/graph', label: '圖形探索' },
  { to: '/schema', label: 'Schema 瀏覽' },
  { to: '/objects', label: '資料管理' },
];

export default function AppLayout() {
  return (
    <div className="h-screen bg-gray-50 flex flex-col overflow-hidden">
      <nav className="shrink-0 bg-white border-b border-gray-200 px-6 py-3 flex items-center gap-8">
        <span className="font-bold text-lg text-gray-800">Ontology Explorer</span>
        <div className="flex gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              className={({ isActive }) =>
                `px-4 py-2 rounded text-sm font-medium ${
                  isActive
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-600 hover:bg-gray-100'
                }`
              }
            >
              {l.label}
            </NavLink>
          ))}
        </div>
      </nav>
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  );
}
