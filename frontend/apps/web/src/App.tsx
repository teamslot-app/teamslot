import { useEffect, useState } from 'react'

type VersionResponse = { service: string; version: string }

function App() {
  const [data, setData] = useState<VersionResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/v1/template/version')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main>
      <h1>TeamSlot</h1>
      {error && <p role="alert">Backend unreachable: {error}</p>}
      {!error && !data && <p>Loading…</p>}
      {data && (
        <p>
          {data.service} is up, version {data.version}
        </p>
      )}
    </main>
  )
}

export default App
