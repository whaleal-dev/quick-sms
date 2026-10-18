/**
 * 文档站分析（GA4）工具
 *
 * 口径与主站（project/home/src/utils/analytics.js）完全一致：
 * - **Google Consent Mode v2**：默认 analytics_storage=denied，用户明确同意后才 update 成 granted。
 *   即使脚本已加载，未同意前也不产生可识别访客的存储与上报。
 * - 只上报**聚合事件**（page_view / 路径），严禁上报任何可识别信息。
 *
 * 与主站的两处差异（环境不同，不是口径差异）：
 * 1. **注入手法**：主站是 Gatsby SSG，构建期把脚本写进静态 HTML；本站是 Docusaurus SPA，
 *    改为运行时注入（与 sms-console 公开站同款）——先 set consent default 再 append loader，
 *    顺序仍受控，且天然带主机名闸门。
 * 2. **页面浏览**：主站 send_page_view:true 由 gtag 自理；本站路由切换不刷新页面
 *    ⇒ send_page_view:false + 手动 trackPageView（SPA 必踩的坑）。
 *
 * 🔴 主机名闸门：只有 docs.whaleal.com 放行。本地 dev（localhost）与 GitHub Pages
 * 预览域（*.github.io）不加载 gtag、不弹同意条——否则会把非生产流量写进生产 GA 媒体资源。
 * 构建期 env（DOCS_SITE_URL）不可靠：docs-build.yml 的 PR 构建不注入该变量，
 * 走 config 默认值也会是生产域名。
 *
 * GA ID 与主站、sms-console 公开站共用同一媒体资源 G-JPFTDT5XS2（一个公司一个资源，
 * docs.whaleal.com 根的镜像也是它）。改 ID 时三处同改：home 的 GH secret
 * GATSBY_GA_MEASUREMENT_ID、sms-console 的 appConfig.ts、本文件。
 */

/** GA4 Measurement ID（与主站 / app 公开站共用） */
export const GA_MEASUREMENT_ID = 'G-JPFTDT5XS2';

/** localStorage 键：用户对分析的同意决定（与主站同名，localStorage 按源隔离，各站独立） */
export const CONSENT_KEY = 'whaleal_analytics_consent';

/** 生产文档站主机名；只有它放行 gtag */
const PRODUCTION_HOST = 'docs.whaleal.com';

export type Consent = 'granted' | 'denied';

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

/** 是否启用埋点：浏览器 + 生产主机名 + 配了 ID */
export function isAnalyticsOn(): boolean {
  return (
    isBrowser() &&
    window.location.hostname === PRODUCTION_HOST &&
    Boolean(GA_MEASUREMENT_ID)
  );
}

/** 当前同意状态：'granted' | 'denied' | null（未表态） */
export function getConsent(): Consent | null {
  if (!isBrowser()) return null;
  try {
    const v = window.localStorage.getItem(CONSENT_KEY);
    return v === 'granted' || v === 'denied' ? v : null;
  } catch {
    return null;
  }
}

/** 是否已就绪（可发事件）：埋点已启用 + gtag 就绪 + 用户已同意 */
export function canTrack(): boolean {
  return (
    isAnalyticsOn() &&
    getConsent() === 'granted' &&
    typeof window.gtag === 'function'
  );
}

/**
 * 注入 gtag（幂等，重复调用直接返回）。
 *
 * 🔴 顺序：先定义 dataLayer/gtag stub 并 set consent default（denied），
 * **再** append 真正的 gtag.js——consent default 必须在任何上报之前生效。
 */
export function ensureGtagLoaded(): void {
  if (!isAnalyticsOn() || typeof window.gtag === 'function') return;

  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag(...args: unknown[]) {
    window.dataLayer!.push(args);
  };

  window.gtag('consent', 'default', {
    analytics_storage: 'denied',
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
    wait_for_update: 500,
  });
  // 关掉 gtag 自动 page_view：SPA 路由切换不刷新页面，改由 trackPageView 手动发
  window.gtag('js', new Date());
  window.gtag('config', GA_MEASUREMENT_ID, { send_page_view: false });

  // 已同意过的老访客：立刻补交，否则他们的会话会被默认值一路拒到底
  if (getConsent() === 'granted') {
    window.gtag('consent', 'update', { analytics_storage: 'granted' });
  }

  const s = document.createElement('script');
  s.async = true;
  s.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`;
  document.head.appendChild(s);
}

/**
 * 记录用户对分析的同意决定，并同步给 gtag。
 * gtag 还没初始化时只落 localStorage（下次加载 ensureGtagLoaded 会补交）。
 */
export function setConsent(decision: Consent): void {
  if (!isBrowser()) return;
  try {
    window.localStorage.setItem(CONSENT_KEY, decision);
  } catch {
    // 隐私模式下 localStorage 不可用 —— 忽略，只是本次会话不再上报
  }
  if (typeof window.gtag !== 'function') return;
  window.gtag('consent', 'update', {
    analytics_storage: decision,
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
  });
  if (decision === 'granted') trackPageView(window.location.pathname);
}

/** 上报一次 page_view（SPA 每次路由变化调一次） */
export function trackPageView(path: string): void {
  if (!canTrack()) return;
  try {
    window.gtag!('event', 'page_view', {
      page_path: path,
      page_location: window.location.href,
      page_title: document.title,
    });
  } catch {
    // 上报失败不影响文档站使用
  }
}
