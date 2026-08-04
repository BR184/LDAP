import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'
import { fetchCurrentCapabilities, fetchCurrentUser, login } from '@/api/modules/auth'
import { useMenuStore } from '@/stores/menu'
import type { CurrentUser, LoginCommand } from '@/types/auth'

const ADMIN_ROLE_CODES = new Set(['ADMIN', 'SUPER_ADMIN'])

export const useAuthStore = defineStore('auth', () => {
  const token = useStorage('idm-access-token', '')
  const currentUser = ref<CurrentUser | null>(null)
  const permissionCodes = ref<string[]>([])
  const profileLoaded = ref(false)
  const profileLoading = ref(false)
  const menuStore = useMenuStore()

  const displayName = computed(() => currentUser.value?.realName || currentUser.value?.userId || '未登录')
  const isAdmin = computed(() => currentUser.value?.roleCodes.some((roleCode) => ADMIN_ROLE_CODES.has(roleCode)) ?? false)
  const defaultEntryPath = computed(() => '/profile')
  const permissionCodeSet = computed(() => new Set(permissionCodes.value))

  async function signIn(payload: LoginCommand) {
    const result = await login(payload)
    token.value = result.accessToken
    profileLoaded.value = false
    await loadProfile()
  }

  async function loadProfile() {
    if (!token.value) {
      currentUser.value = null
      profileLoaded.value = false
      return null
    }

    if (profileLoading.value) {
      return currentUser.value
    }

    profileLoading.value = true

    try {
      const [profile, capabilities] = await Promise.all([
        fetchCurrentUser(),
        fetchCurrentCapabilities(),
      ])
      currentUser.value = profile
      permissionCodes.value = [...new Set(capabilities.permissionCodes)].sort()
      profileLoaded.value = true
      menuStore.syncRoleAccess(profile.roleCodes)
      return profile
    } catch (error) {
      clearSession()
      throw error
    } finally {
      profileLoading.value = false
    }
  }

  function clearSession() {
    token.value = ''
    currentUser.value = null
    permissionCodes.value = []
    profileLoaded.value = false
    menuStore.clearMenus()
  }

  function can(permissionCode: string) {
    return permissionCodeSet.value.has(permissionCode)
  }

  function canAny(...requiredPermissionCodes: string[]) {
    return requiredPermissionCodes.some((permissionCode) => can(permissionCode))
  }

  return {
    token,
    currentUser,
    permissionCodes,
    profileLoaded,
    displayName,
    isAdmin,
    defaultEntryPath,
    can,
    canAny,
    signIn,
    loadProfile,
    clearSession,
  }
})
