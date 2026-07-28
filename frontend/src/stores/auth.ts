import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'
import { fetchCurrentUser, login } from '@/api/modules/auth'
import { useMenuStore } from '@/stores/menu'
import type { CurrentUser, LoginCommand } from '@/types/auth'

const ADMIN_ROLE_CODES = new Set(['ADMIN', 'SUPER_ADMIN'])

export const useAuthStore = defineStore('auth', () => {
  const token = useStorage('idm-access-token', '')
  const currentUser = ref<CurrentUser | null>(null)
  const profileLoaded = ref(false)
  const profileLoading = ref(false)
  const menuStore = useMenuStore()

  const displayName = computed(() => currentUser.value?.realName || currentUser.value?.userId || '未登录')
  const isAdmin = computed(() => currentUser.value?.roleCodes.some((roleCode) => ADMIN_ROLE_CODES.has(roleCode)) ?? false)
  const defaultEntryPath = computed(() => '/profile')

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
      const profile = await fetchCurrentUser()
      currentUser.value = profile
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
    profileLoaded.value = false
    menuStore.clearMenus()
  }

  return {
    token,
    currentUser,
    profileLoaded,
    displayName,
    isAdmin,
    defaultEntryPath,
    signIn,
    loadProfile,
    clearSession,
  }
})
