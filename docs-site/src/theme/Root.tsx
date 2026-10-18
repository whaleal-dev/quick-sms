/**
 * 全站包裹组件（Docusaurus theme Root）
 *
 * 职责（都只此一处，别散到别处）：
 * 1. 浏览器启动时注入 gtag（幂等；含 Consent Mode v2 默认拒绝 + 主机名闸门）
 * 2. SPA 路由切换时手动发 page_view（gtag 自动 page_view 已关）
 * 3. 挂载分析同意横幅
 *
 * 站点是 Docusaurus SPA：路由切换不触发浏览器页面加载 ⇒ page_view 必须手动，
 * 这与主站（SSG，gtag 自理 page_view）不同，但 Consent Mode 口径与主站完全一致。
 */

import React, { useEffect } from 'react';
import { useLocation } from '@docusaurus/router';
import { ensureGtagLoaded, trackPageView } from '../ga/analytics';
import ConsentBanner from '../components/ConsentBanner';

export default function Root({ children }: { children: React.ReactNode }): React.ReactElement {
  const { pathname, search } = useLocation();

  // 启动时注入一次 gtag（幂等）
  useEffect(() => {
    ensureGtagLoaded();
  }, []);

  // 路由变化发 page_view；首次挂载也发一次（已同意的访客首屏不丢）
  useEffect(() => {
    trackPageView(pathname + search);
  }, [pathname, search]);

  return (
    <>
      {children}
      <ConsentBanner />
    </>
  );
}
