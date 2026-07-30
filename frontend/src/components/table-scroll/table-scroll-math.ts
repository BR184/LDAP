export function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(Math.max(value, minimum), maximum)
}

export function getThumbSize(trackWidth: number, viewportWidth: number, contentWidth: number, minimumSize = 44) {
  if (trackWidth <= 0 || viewportWidth <= 0 || contentWidth <= viewportWidth) {
    return trackWidth
  }
  return clamp((viewportWidth / contentWidth) * trackWidth, minimumSize, trackWidth)
}

export function getThumbOffset(
  scrollLeft: number,
  maxScrollLeft: number,
  trackWidth: number,
  thumbSize: number,
) {
  const maxOffset = Math.max(trackWidth - thumbSize, 0)
  if (maxScrollLeft <= 0 || maxOffset <= 0) {
    return 0
  }
  return clamp((scrollLeft / maxScrollLeft) * maxOffset, 0, maxOffset)
}

export function getScrollLeftForThumb(
  thumbOffset: number,
  maxScrollLeft: number,
  trackWidth: number,
  thumbSize: number,
) {
  const maxOffset = Math.max(trackWidth - thumbSize, 0)
  if (maxScrollLeft <= 0 || maxOffset <= 0) {
    return 0
  }
  return clamp((thumbOffset / maxOffset) * maxScrollLeft, 0, maxScrollLeft)
}
