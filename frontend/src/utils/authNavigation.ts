/**
 * 认证跳转工具：统一“登录失效”场景的整页导航行为。
 */

/**
 * 以整页导航跳转登录页并携带回跳地址。
 *
 * 使用 window.location 而非 vue-router 软跳转：强制浏览器重新拉取入口文档与
 * 最新构建资源，销毁可能驻留旧 chunk 的 SPA 内存堆栈；配合 Nginx 对 SPA 入口
 * 的 no-cache 头，避免“登录失效后重登回到旧页面、需手动刷新”的陈旧缓存问题。
 * 已在登录页或运行于无 window 环境时不做任何处理，避免刷新循环与测试报错。
 *
 * @param fullPath 登录成功后的回跳地址（路径 + 查询串）。
 */
export function hardNavigateToLogin(fullPath: string): void {
  if (typeof window === 'undefined') {
    return
  }
  if (window.location.pathname === '/login') {
    return
  }
  window.location.href = `/login?redirect=${encodeURIComponent(fullPath)}`
}
