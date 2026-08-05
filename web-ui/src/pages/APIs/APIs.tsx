import React, { useState, useEffect } from 'react';
import { useSelector } from 'react-redux';
import { APIConfig, APIListItem, DEFAULT_API_CONFIG } from './APIForm.types';

// Matches ResourceTypes.MODULE_ID + the "api" typeId in gateway/.../records/ResourceTypes.java.
// The generic ConfigurationManager REST routes are always /data/api/v1/resources/... - there is
// no module-specific path, so this must line up exactly with the Java-side ResourceType.
const RESOURCE_PATH = 'com.kyvislabs.api.client/api';
const LIST_URL = `/data/api/v1/resources/list/${RESOURCE_PATH}`;
const FIND_URL = `/data/api/v1/resources/find/${RESOURCE_PATH}`;
const RESOURCE_URL = `/data/api/v1/resources/${RESOURCE_PATH}`;

export default function APIs() {
  const [apis, setApis] = useState<APIListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingApi, setEditingApi] = useState<string | null>(null);

  const csrfToken = useSelector((state: any) => state?.userSession?.csrfToken);

  const fetchApis = async () => {
    try {
      setLoading(true);
      const response = await fetch(`${LIST_URL}?limit=100&offset=0`);
      if (!response.ok) throw new Error('Failed to fetch APIs');
      const data = await response.json();
      setApis(data.items || []);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchApis(); }, []);

  const handleDelete = async (api: APIListItem) => {
    if (!confirm(`Delete API "${api.name}"?`)) return;
    try {
      const response = await fetch(`${RESOURCE_URL}/${encodeURIComponent(api.name)}/${encodeURIComponent(String(api.signature))}`, {
        method: 'DELETE',
        headers: { 'X-CSRF-Token': csrfToken },
      });
      if (!response.ok) throw new Error('Failed to delete API');
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
                  {api.config?.enabled ? 'Yes' : 'No'}
                </td>
                <td style={{ padding: '8px', borderBottom: '1px solid #eee', textAlign: 'right' }}>
                  <button onClick={() => setEditingApi(api.name)} style={{ marginRight: 8 }}>Edit</button>
                  <button onClick={() => handleDelete(api)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showAddModal && (
        <APIAddModal
          csrfToken={csrfToken}
          onClose={() => setShowAddModal(false)}
          onSave={() => { setShowAddModal(false); fetchApis(); }}
        />
      )}

      {editingApi && (
        <APIEditDrawer
          apiName={editingApi}
          csrfToken={csrfToken}
          onClose={() => setEditingApi(null)}
          onSave={() => { setEditingApi(null); fetchApis(); }}
        />
      )}
    </div>
  );
}

interface APIAddModalProps {
  csrfToken: string;
  onClose: () => void;
  onSave: () => void;
}

function APIAddModal({ csrfToken, onClose, onSave }: APIAddModalProps) {
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
      const config: APIConfig = { ...DEFAULT_API_CONFIG, enabled, configuration };
      const response = await fetch(RESOURCE_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify([{ name, config }]),
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
  csrfToken: string;
  onClose: () => void;
  onSave: () => void;
}

function APIEditDrawer({ apiName, csrfToken, onClose, onSave }: APIEditDrawerProps) {
  const [resource, setResource] = useState<APIListItem | null>(null);
  const [enabled, setEnabled] = useState(true);
  const [configuration, setConfiguration] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(`${FIND_URL}/${encodeURIComponent(apiName)}`)
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load API');
        return r.json();
      })
      .then((data: APIListItem) => {
        setResource(data);
        setEnabled(data.config?.enabled ?? true);
        setConfiguration(data.config?.configuration ?? '');
        setLoading(false);
      })
      .catch(() => { setError('Failed to load API'); setLoading(false); });
  }, [apiName]);

  const handleSave = async () => {
    if (!resource) return;
    setSaving(true);
    try {
      // Preserve everything else on the config (variables, certificate, webhookKeys) - only
      // enabled/configuration are edited here, but a PUT replaces the whole resource.
      const config: APIConfig = { ...resource.config, enabled, configuration };
      const response = await fetch(RESOURCE_URL, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify([{ name: resource.name, signature: resource.signature, config }]),
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
