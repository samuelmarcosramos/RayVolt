import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

type Hub = { id: number; name: string; department: string; capacity: number; lat: number | null; lon: number | null }

export default function Hubs() {
  const [hubs, setHubs] = useState<Hub[]>([])
  const [editing, setEditing] = useState<Hub | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [version, setVersion] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/hubs', { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Could not load hubs.')
        setHubs(await response.json())
      })
      .catch(error => { if (!controller.signal.aborted) setError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [version])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const response = await fetch(editing ? `/api/hubs/${editing.id}` : '/api/hubs', {
        method: editing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: data.get('name'), department: data.get('department'),
          capacity: Number(data.get('capacity')), lat: Number(data.get('lat')), lon: Number(data.get('lon')) }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.detail || 'Could not save the hub.')
      setHubs(current => [...current.filter(hub => hub.id !== body.id), body].sort((a, b) => a.name.localeCompare(b.name)))
      setEditing(null)
      form.reset()
      setMessage('Hub saved.')
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Could not save the hub.')
    } finally {
      setSaving(false)
    }
  }

  return <section className="hubs" aria-labelledby="hubs-title">
    <h2 id="hubs-title">Hubs</h2>
    <button type="button" disabled={loading || saving} onClick={() => { setLoading(true); setError(''); setVersion(v => v + 1) }}>Refresh hubs</button>
    {loading && <p role="status">Loading hubs...</p>}
    {error && <p role="alert" className="error-message">{error}</p>}
    {message && <p role="status">{message}</p>}
    {!loading && hubs.length === 0 && !error && <p>No hubs yet.</p>}
    <ul className="hub-list">{hubs.map(hub => <li key={hub.id}>
      <div><strong>{hub.name}</strong><p>{hub.department} · Capacity: {hub.capacity}</p>
        <small>Location: {hub.lat ?? 'Unknown'}, {hub.lon ?? 'Unknown'}</small></div>
      <button type="button" disabled={saving} aria-label={`Edit ${hub.name}`} onClick={() => { setEditing(hub); setMessage('') }}>Edit</button>
    </li>)}</ul>
    <h3>{editing ? 'Edit hub' : 'Add hub'}</h3>
    <form key={editing?.id ?? 'new'} onSubmit={save} className="hub-form">
      <label>Name<input name="name" required maxLength={120} defaultValue={editing?.name ?? ''} /></label>
      <label>Department<input name="department" required maxLength={120} defaultValue={editing?.department ?? ''} /></label>
      <label>Capacity<input name="capacity" type="number" required min={1} step={1} defaultValue={editing?.capacity ?? 1} /></label>
      <label>Latitude<input name="lat" type="number" required min={-90} max={90} step="any" defaultValue={editing?.lat ?? ''} /></label>
      <label>Longitude<input name="lon" type="number" required min={-180} max={180} step="any" defaultValue={editing?.lon ?? ''} /></label>
      <div><button disabled={saving || loading} type="submit">{saving ? 'Saving...' : 'Save hub'}</button>
        {editing && <button disabled={saving} type="button" onClick={() => setEditing(null)}>Cancel</button>}</div>
    </form>
  </section>
}
