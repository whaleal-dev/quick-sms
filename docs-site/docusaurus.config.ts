import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

/** 公开文档站：https://whaleal.com/quick-sms/ */
const siteUrl = process.env.DOCS_SITE_URL ?? 'https://whaleal.com';
const siteBaseUrl = process.env.DOCS_SITE_BASE_URL ?? '/quick-sms/';

const config: Config = {
  title: 'Quick SMS 文档站',
  tagline: '多供应商短信聚合 SDK · 国内与国际 · JDK 21',
  favicon: 'img/favicon.ico',
  future: {v4: true},
  url: siteUrl,
  baseUrl: siteBaseUrl,
  organizationName: 'whaleal-dev',
  projectName: 'quick-sms',
  onBrokenLinks: 'throw',
  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/whaleal-dev/quick-sms/tree/main/docs-site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  themes: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        hashed: true,
        language: ['zh', 'en'],
        indexDocs: true,
        indexBlog: false,
        docsRouteBasePath: 'docs',
        searchResultLimits: 10,
        searchResultContextMaxLength: 80,
      },
    ],
  ],
  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    navbar: {
      title: 'Quick SMS 文档站',
      logo: {
        alt: 'Quick SMS Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: '文档',
        },
        {
          type: 'search',
          position: 'right',
        },
        {
          href: 'https://github.com/whaleal-dev/quick-sms',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '文档',
          items: [
            {label: '开始阅读', to: '/docs/intro'},
            {label: '短信概念', to: '/docs/concepts/overview'},
            {label: 'Spring Boot', to: '/docs/getting-started/quickstart-spring-boot'},
          ],
        },
        {
          title: '资源',
          items: [
            {label: 'GitHub', href: 'https://github.com/whaleal-dev/quick-sms'},
            {label: 'Issues', href: 'https://github.com/whaleal-dev/quick-sms/issues'},
            {label: '仓库 README', href: 'https://github.com/whaleal-dev/quick-sms#readme'},
          ],
        },
      ],
      copyright: `Copyright (c) ${new Date().getFullYear()} whaleal-dev · 基于 Docusaurus 构建`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'json'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
