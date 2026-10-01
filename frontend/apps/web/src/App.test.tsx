import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import App from './App'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('App', () => {
  it('affiche la version renvoyée par le backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ service: 'service-template', version: '1' }),
        { status: 200 },
      ),
    )
    render(<App />)
    expect(
      await screen.findByText(/service-template is up, version 1/),
    ).toBeTruthy()
  })

  it('affiche une erreur si le backend est injoignable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network'))
    render(<App />)
    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})
