import { inject, type InjectionKey, ref, type Ref } from 'vue'

export interface PersistentTableScrollCoordinator {
  activeFrameId: Readonly<Ref<string | null>>
  activate(frameId: string): void
  release(frameId: string): void
}

export const PersistentTableScrollCoordinatorKey: InjectionKey<PersistentTableScrollCoordinator> = Symbol('persistent-table-scroll')

export function createPersistentTableScrollCoordinator(): PersistentTableScrollCoordinator {
  const activeFrameId = ref<string | null>(null)
  return {
    activeFrameId,
    activate(frameId) {
      activeFrameId.value = frameId
    },
    release(frameId) {
      if (activeFrameId.value === frameId) {
        activeFrameId.value = null
      }
    },
  }
}

export function usePersistentTableScrollCoordinator() {
  const coordinator = inject(PersistentTableScrollCoordinatorKey)
  if (!coordinator) {
    throw new Error('Persistent table scroll coordinator is not provided.')
  }
  return coordinator
}
