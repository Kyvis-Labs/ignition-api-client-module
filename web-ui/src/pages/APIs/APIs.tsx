import React, { useState, useEffect } from 'react';

interface APIItem {
  name: string;
  enabled: boolean;
  configuration: string;
}

const BASE_URL = '/data/api-client/api';

export default function APIs() {
  const [apis, setApis] = useState<APIItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingApi, setEditingApi] = useState<string | null>(null);

  const fetchApis = async () => {
    try {
      setLoading(true);
      const response = await fetch(BASE_URL);
      if (!response.ok) throw new Error('Failed to fetch APIs');
      const data = await response.json();
      setApis(data.resources || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchApis(); }, []);

  const handleDelete = async (name: string) => {
    if (!confirm(`Delete API "${name}"?`)) return;
    try {
      await fetch(`${BASE_URL}/${name}`, { method: 'DELETE' });
      fetchApis();
    } catch (err) {
      setError('Failed to delete API');
    }
  };

  if (loading) return <div style={{ padding: 20 }}>Loading...</div>;
  if (error) return <div style={{ padding: 20, color: 'red' }}>Error: {error}</div>;

  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>APIs</h2>
        <button onClick={() => setShowAddModal(true)}>Add API</button>
      </div>

      {apis.length === 0 ? (
        <p>No APIs configured. Click "Add API" to get started.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: '8px', borderBottom: '1px solid #ccc' }}>Name</th>
              <th style={{ textAlign: 'left', padding: '8px', borderBottom: '1px solid #ccc' }}>Enabled</th>
              <th style={{ textAlign: 'right', padding: '8px', borderBottom: '1px solid #ccc' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {apis.map((api) => (
              <tr key={api.name}>
                <td style={{ padding: '8px', borderBottom: '1px solid #eee' }}>{api.name}</td>
                <td style={{ padding: '8px', borderBottom: '1px solid #eee' }}>
                  {api.enabled ? 'Yes' : 'No'}
                </td>
                <td style={{ padding: '8px', borderBottom: '1px solid #eee', textAlign: 'right' }}>
                  <button onClick={() => setEditingApi(api.name)} style={{ marginRight: 8 }}>Edit</button>
                  <button onClick={() => handleDelete(api.name)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showAddModal && (
        <APIAddModal
          onClose={() => setShowAddModal(false)}
          onSave={() => { setShowAddModal(false); fetchApis(); }}
        />
      )}

      {editingApi && (
        <APIEditDrawer
          apiName={editingApi}
          onClose={() => setEditingApi(null)}
          onSave={() => { setEditingApi(null); fetchApis(); }}
        />
      )}
    </div>
  );
}

interface APIAddModalProps {
  onClose: () => void;
  onSave: () => void;
}

function APIAddModal({ onClose, onSave }: APIAddModalProps) {
  const [name, setName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [configuration, setConfiguration] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    if (!name.trim()) { setError('Name is required'); return; }
    if (!configuration.trim()) { setError('Configuration is required'); return; }
    setSaving(true);
    try {
      const response = await fetch(`${BASE_URL}/${name}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled, configuration, variables: [], certificate: null })
      });
      if (!response.ok) throw new Error('Failed to create API');
      onSave();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: 'white', padding: 24, borderRadius: 8, width: 600, maxHeight: '80vh', overflow: 'auto' }}>
        <h3>Add API</h3>
        {error && <div style={{ color: 'red', marginBottom: 12 }}>{error}</div>}
        <div style={{ marginBottom: 12 }}>
          <label style={{ display: 'block', marginBottom: 4 }}>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} style={{ width: '100%', padding: '6px 8px', boxSizing: 'border-box' }} />
        </div>
        <div style={{ marginBottom: 12 }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            Enabled
          </label>
        </div>
        <div style={{ marginBottom: 12 }}>
          <label style={{ display: 'block', marginBottom: 4 }}>YAML Configuration</label>
          <textarea
            value={configuration}
            onChange={(e) => setConfiguration(e.target.value)}
            rows={12}
            style={{ width: '100%', padding: '6px 8px', boxSizing: 'border-box', fontFamily: 'monospace' }}
          />
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button onClick={onClose}>Cancel</button>
          <button onClick={handleSave} disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
        </div>
      </div>
    </div>
  );
}

interface APIEditDrawerProps {
  apiName: string;
  onClose: () => void;
  onSave: () => void;
}

function APIEditDrawer({ apiName, onClose, onSave }: APIEditDrawerProps) {
  const [enabled, setEnabled] = useState(true);
  const [configuration, setConfiguration] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(`${BASE_URL}/${apiName}`)
      .then((r) => r.json())
      .then((data) => {
        setEnabled(data.enabled ?? true);
        setConfiguration(data.configuration ?? '');
        setLoading(false);
      })
      .catch(() => { setError('Failed to load API'); setLoading(false); });
  }, [apiName]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const response = await fetch(`${BASE_URL}/${apiName}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled, configuration })
      });
      if (!response.ok) throw new Error('Failed to update API');
      onSave();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, right: 0, bottom: 0, width: 600, background: 'white', boxShadow: '-2px 0 8px rgba(0,0,0,0.2)', padding: 24, overflow: 'auto', zIndex: 1000 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h3>Edit API: {apiName}</h3>
        <button onClick={onClose}>&#x2715;</button>
      </div>
      {loading ? <div>Loading...</div> : (
        <>
          {error && <div style={{ color: 'red', marginBottom: 12 }}>{error}</div>}
          <div style={{ marginBottom: 12 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
              Enabled
            </label>
          </div>
          <div style={{ marginBottom: 12 }}>
            <label style={{ display: 'block', marginBottom: 4 }}>YAML Configuration</label>
            <textarea
              value={configuration}
              onChange={(e) => setConfiguration(e.target.value)}
              rows={20}
              style={{ width: '100%', padding: '6px 8px', boxSizing: 'border-box', fontFamily: 'monospace' }}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button onClick={onClose}>Cancel</button>
            <button onClick={handleSave} disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
          </div>
        </>
      )}
    </div>
  );
}
