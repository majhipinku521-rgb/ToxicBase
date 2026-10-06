import React, { useEffect, useState } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_TOXICBASE_URL || 'http://localhost:8080';

export default function ToxicBaseAdminConsole() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [projects, setProjects] = useState<any[]>([]);
  const [selectedProject, setSelectedProject] = useState<any>(null);
  const [apiKey, setApiKey] = useState<string>('');
  const [users, setUsers] = useState<any[]>([]);
  const [collection, setCollection] = useState<string>('users_profile');
  const [documents, setDocuments] = useState<any[]>([]);

  useEffect(() => {
    fetch(`${API_BASE}/projects`)
      .then((r) => r.json())
      .then((d) => {
        if (d.projects) {
          setProjects(d.projects);
          if (d.projects.length > 0 && !selectedProject) setSelectedProject(d.projects[0]);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!apiKey) return;
    fetch(`${API_BASE}/users`, { headers: { 'X-ToxicBase-Key': apiKey } })
      .then((r) => r.json())
      .then((d) => d.users && setUsers(d.users))
      .catch(() => {});
    fetch(`${API_BASE}/database/${collection}`, { headers: { 'X-ToxicBase-Key': apiKey } })
      .then((r) => r.json())
      .then((d) => d.documents && setDocuments(d.documents))
      .catch(() => {});
  }, [apiKey, collection]);

  return (
    <div style={{ background: '#0B0F17', color: '#E2E8F0', minHeight: '100vh', fontFamily: 'monospace', display: 'flex' }}>
      <aside style={{ width: 240, borderRight: '1px solid #1E293B', padding: 20 }}>
        <h2 style={{ color: '#00FF66', letterSpacing: 2 }}>TOXICBASE</h2>
        {['dashboard', 'projects', 'authentication', 'users', 'database', 'apikeys', 'rules', 'logs', 'usage', 'settings'].map((tab) => (
          <div
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: '10px 12px',
              margin: '6px 0',
              cursor: 'pointer',
              borderRadius: 6,
              background: activeTab === tab ? '#00FF6622' : 'transparent',
              color: activeTab === tab ? '#00FF66' : '#94A3B8',
              textTransform: 'uppercase'
            }}
          >
            {tab}
          </div>
        ))}
      </aside>
      <main style={{ flex: 1, padding: 32 }}>
        <header style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
          <h1>{activeTab.toUpperCase()}</h1>
          <input
            placeholder="Paste Project X-ToxicBase-Key..."
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            style={{ background: '#1E293B', color: '#00FF66', border: '1px solid #334155', padding: '8px 12px', borderRadius: 6, width: 340 }}
          />
        </header>
        <section>
          <p>Connected to: {API_BASE} | Projects: {projects.length} | Users: {users.length} | Documents in '{collection}': {documents.length}</p>
        </section>
      </main>
    </div>
  );
}
