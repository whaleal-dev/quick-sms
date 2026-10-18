/**
 * 分析同意横幅（GA4 + Consent Mode v2）
 *
 * 与主站（home/src/components/AnalyticsConsent）口径一致：
 * 默认拒绝、显式同意后才上报；拒绝后不再打扰（localStorage 记住决定）。
 *
 * 只在生产主机名 docs.whaleal.com 且用户未表态时渲染（见 analytics.ts 的闸门）。
 */

import React, { useCallback, useEffect, useState } from 'react';
import { getConsent, isAnalyticsOn, setConsent } from '../ga/analytics';

export default function ConsentBanner(): React.ReactElement | null {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // 挂载时读取历史决定：已表态（granted/denied）都不再显示
    if (!isAnalyticsOn()) return;
    if (getConsent() !== null) return;
    setVisible(true);
  }, []);

  const decide = useCallback((decision: 'granted' | 'denied') => {
    setConsent(decision);
    setVisible(false);
  }, []);

  if (!visible) return null;

  return (
    <div className="ga-consent-banner" role="region" aria-label="分析 Cookie 同意">
      <p className="ga-consent-banner__text">
        本站使用 Google Analytics 了解页面访问情况，以改进文档内容。不会收集任何可识别个人的信息。
        你可以随时拒绝，拒绝不影响正常使用。
      </p>
      <div className="ga-consent-banner__actions">
        <button
          type="button"
          className="ga-consent-banner__btn ga-consent-banner__btn--accept"
          onClick={() => decide('granted')}>
          同意
        </button>
        <button
          type="button"
          className="ga-consent-banner__btn ga-consent-banner__btn--decline"
          onClick={() => decide('denied')}>
          拒绝
        </button>
      </div>
    </div>
  );
}
