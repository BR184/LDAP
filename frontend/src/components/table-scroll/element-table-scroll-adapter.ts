export interface ElementTableScrollAdapter {
  getViewportWidth(): number
  getContentWidth(): number
  getScrollLeft(): number
  setScrollLeft(value: number): void
  onScroll(listener: () => void): () => void
}

function findScrollViewport(frame: HTMLElement) {
  return frame.querySelector<HTMLElement>('.el-table__body-wrapper .el-scrollbar__wrap')
    || frame.querySelector<HTMLElement>('.el-table__body-wrapper')
}

export function createElementTableScrollAdapter(frame: HTMLElement): ElementTableScrollAdapter | null {
  const viewport = findScrollViewport(frame)
  if (!viewport) {
    return null
  }
  return {
    getViewportWidth: () => viewport.clientWidth,
    getContentWidth: () => viewport.scrollWidth,
    getScrollLeft: () => viewport.scrollLeft,
    setScrollLeft: (value) => {
      viewport.scrollLeft = value
    },
    onScroll: (listener) => {
      viewport.addEventListener('scroll', listener, { passive: true })
      return () => viewport.removeEventListener('scroll', listener)
    },
  }
}
