<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, reactive, ref } from 'vue'
import { createElementTableScrollAdapter, type ElementTableScrollAdapter } from './element-table-scroll-adapter'
import { getScrollLeftForThumb, getThumbOffset, getThumbSize } from './table-scroll-math'
import { usePersistentTableScrollCoordinator } from './persistent-table-scroll-coordinator'

const FRAME_ID = `persistent-table-scroll-${Math.random().toString(36).slice(2)}`

const frameRef = ref<HTMLElement | null>(null)
const trackRef = ref<HTMLElement | null>(null)
const coordinator = usePersistentTableScrollCoordinator()
const adapter = ref<ElementTableScrollAdapter | null>(null)
const resizeObserver = ref<ResizeObserver | null>(null)
const removeScrollListener = ref<(() => void) | null>(null)
const drag = reactive({ active: false, startPointerX: 0, startThumbOffset: 0 })
const metrics = reactive({ viewportWidth: 0, contentWidth: 0, scrollLeft: 0, trackWidth: 0, left: 0 })

const hasOverflow = computed(() => metrics.contentWidth > metrics.viewportWidth + 1)
const isActive = computed(() => coordinator.activeFrameId.value === FRAME_ID)
const thumbSize = computed(() => getThumbSize(metrics.trackWidth, metrics.viewportWidth, metrics.contentWidth))
const thumbOffset = computed(() => getThumbOffset(
  metrics.scrollLeft,
  Math.max(metrics.contentWidth - metrics.viewportWidth, 0),
  metrics.trackWidth,
  thumbSize.value,
))
const trackStyle = computed(() => ({ left: `${metrics.left}px`, width: `${metrics.trackWidth}px` }))
const thumbStyle = computed(() => ({ transform: `translateX(${thumbOffset.value}px)`, width: `${thumbSize.value}px` }))

function activate() {
  if (hasOverflow.value) {
    coordinator.activate(FRAME_ID)
  }
}

function refresh() {
  const frame = frameRef.value
  const currentAdapter = adapter.value
  if (!frame || !currentAdapter) {
    return
  }
  const bounds = frame.getBoundingClientRect()
  metrics.viewportWidth = currentAdapter.getViewportWidth()
  metrics.contentWidth = currentAdapter.getContentWidth()
  metrics.scrollLeft = currentAdapter.getScrollLeft()
  metrics.left = Math.max(bounds.left, 0)
  metrics.trackWidth = Math.max(Math.min(bounds.width, window.innerWidth - metrics.left), 0)
  if (!hasOverflow.value) {
    coordinator.release(FRAME_ID)
  }
}

function refreshAfterRender() {
  window.requestAnimationFrame(refresh)
}

function onViewportScroll() {
  refresh()
  activate()
}

function setScrollFromThumbOffset(offset: number) {
  const currentAdapter = adapter.value
  if (!currentAdapter) {
    return
  }
  const targetScrollLeft = getScrollLeftForThumb(
    offset,
    Math.max(metrics.contentWidth - metrics.viewportWidth, 0),
    metrics.trackWidth,
    thumbSize.value,
  )
  currentAdapter.setScrollLeft(targetScrollLeft)
  metrics.scrollLeft = targetScrollLeft
}

function startDragging(event: PointerEvent) {
  if (!hasOverflow.value) {
    return
  }
  event.preventDefault()
  drag.active = true
  drag.startPointerX = event.clientX
  drag.startThumbOffset = thumbOffset.value
  document.body.classList.add('persistent-table-scroll--dragging')
  trackRef.value?.setPointerCapture(event.pointerId)
  activate()
}

function moveDragging(event: PointerEvent) {
  if (!drag.active) {
    return
  }
  event.preventDefault()
  const maximumOffset = Math.max(metrics.trackWidth - thumbSize.value, 0)
  setScrollFromThumbOffset(Math.min(Math.max(drag.startThumbOffset + event.clientX - drag.startPointerX, 0), maximumOffset))
}

function stopDragging(event: PointerEvent) {
  if (!drag.active) {
    return
  }
  drag.active = false
  document.body.classList.remove('persistent-table-scroll--dragging')
  if (trackRef.value?.hasPointerCapture(event.pointerId)) {
    trackRef.value.releasePointerCapture(event.pointerId)
  }
}

function onTrackClick(event: PointerEvent) {
  if (drag.active || !hasOverflow.value) {
    return
  }
  const bounds = trackRef.value?.getBoundingClientRect()
  if (!bounds) {
    return
  }
  setScrollFromThumbOffset(event.clientX - bounds.left - thumbSize.value / 2)
  activate()
}

function onTrackKeydown(event: KeyboardEvent) {
  const currentAdapter = adapter.value
  if (!currentAdapter || !hasOverflow.value) {
    return
  }
  const amount = Math.max(metrics.viewportWidth * 0.15, 48)
  const maxScrollLeft = Math.max(metrics.contentWidth - metrics.viewportWidth, 0)
  let nextScrollLeft: number | null = null
  if (event.key === 'ArrowLeft') nextScrollLeft = metrics.scrollLeft - amount
  if (event.key === 'ArrowRight') nextScrollLeft = metrics.scrollLeft + amount
  if (event.key === 'Home') nextScrollLeft = 0
  if (event.key === 'End') nextScrollLeft = maxScrollLeft
  if (nextScrollLeft === null) {
    return
  }
  event.preventDefault()
  currentAdapter.setScrollLeft(Math.min(Math.max(nextScrollLeft, 0), maxScrollLeft))
  refresh()
  activate()
}

onMounted(async () => {
  await nextTick()
  if (!frameRef.value) {
    return
  }
  adapter.value = createElementTableScrollAdapter(frameRef.value)
  if (!adapter.value) {
    return
  }
  removeScrollListener.value = adapter.value.onScroll(onViewportScroll)
  resizeObserver.value = new ResizeObserver(refreshAfterRender)
  resizeObserver.value.observe(frameRef.value)
  window.addEventListener('resize', refreshAfterRender, { passive: true })
  window.addEventListener('scroll', refreshAfterRender, { passive: true, capture: true })
  refreshAfterRender()
})

onUpdated(refreshAfterRender)

onBeforeUnmount(() => {
  removeScrollListener.value?.()
  resizeObserver.value?.disconnect()
  window.removeEventListener('resize', refreshAfterRender)
  window.removeEventListener('scroll', refreshAfterRender, true)
  document.body.classList.remove('persistent-table-scroll--dragging')
  coordinator.release(FRAME_ID)
})
</script>

<template>
  <section ref="frameRef" class="persistent-table-scroll-frame" @pointerenter="activate" @focusin="activate">
    <slot />
  </section>

  <div
    v-show="isActive && hasOverflow"
    ref="trackRef"
    class="persistent-table-scroll-track"
    :style="trackStyle"
    role="scrollbar"
    aria-label="表格横向滚动"
    aria-orientation="horizontal"
    tabindex="0"
    @pointerdown.self="onTrackClick"
    @pointermove="moveDragging"
    @pointerup="stopDragging"
    @pointercancel="stopDragging"
    @keydown="onTrackKeydown"
  >
    <div class="persistent-table-scroll-thumb" :style="thumbStyle" @pointerdown="startDragging" />
  </div>
</template>

<style scoped lang="scss">
.persistent-table-scroll-frame {
  min-width: 0;

  :deep(.el-scrollbar__bar.is-horizontal) {
    display: none !important;
  }

  :deep(.el-table__body-wrapper .el-scrollbar__wrap),
  :deep(.el-table__body-wrapper) {
    scrollbar-width: none;
  }

  :deep(.el-table__body-wrapper .el-scrollbar__wrap::-webkit-scrollbar),
  :deep(.el-table__body-wrapper::-webkit-scrollbar) {
    height: 0;
  }
}

.persistent-table-scroll-track {
  position: fixed;
  bottom: 16px;
  z-index: 120;
  height: 12px;
  padding: 3px 0;
  cursor: pointer;
  outline: none;
  background: transparent;
  touch-action: none;
}

.persistent-table-scroll-thumb {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: rgba(120, 120, 120, 0.28);
  transition: background-color 140ms ease;
}

.persistent-table-scroll-track:hover .persistent-table-scroll-thumb,
.persistent-table-scroll-track:focus-visible .persistent-table-scroll-thumb {
  background: rgba(76, 76, 76, 0.72);
}
</style>
