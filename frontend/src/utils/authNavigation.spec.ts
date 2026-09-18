import { afterEach, describe, expect, it, vi } from 'vitest'

import { hardNavigateToLogin } from './authNavigation'

function installLocationStub(pathname: string): { pathname: string; href: string } {
  const location = { pathname, href: '' }
  vi.stubGlobal('window', { location })
  return location
}

describe('hardNavigateToLogin 整页跳转登录页', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sets full-page href with encoded redirect for protected paths', () => {
    const location = installLocationStub('/users')

    hardNavigateToLogin('/users?page=2')

    expect(location.href).toBe('/login?redirect=%2Fusers%3Fpage%3D2')
  })

  it('does not redirect when already on the login page', () => {
    const location = installLocationStub('/login')

    hardNavigateToLogin('/users')

    expect(location.href).toBe('')
  })

  it('is a no-op when window is unavailable', () => {
    vi.unstubAllGlobals()

    expect(() => hardNavigateToLogin('/users')).not.toThrow()
  })
})
