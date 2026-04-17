import { Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import GraphPage from './pages/GraphPage';
import SchemaPage from './pages/SchemaPage';
import ObjectsPage from './pages/ObjectsPage';
import AiPage from './pages/AiPage';
import SourcesPage from './pages/SourcesPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/ai" element={<AiPage />} />
        <Route path="/graph" element={<GraphPage />} />
        <Route path="/schema" element={<SchemaPage />} />
        <Route path="/objects" element={<ObjectsPage />} />
        <Route path="/sources" element={<SourcesPage />} />
        <Route path="*" element={<Navigate to="/ai" replace />} />
      </Route>
    </Routes>
  );
}
