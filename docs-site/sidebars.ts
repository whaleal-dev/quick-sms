import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  tutorialSidebar: [
    'intro',
    {
      type: 'category',
      label: '短信概念',
      items: [
        'concepts/overview',
        'concepts/outbound',
        'concepts/inbound',
        'concepts/report',
        'concepts/webhook',
      ],
    },
    {
      type: 'category',
      label: '入门',
      items: [
        'getting-started/quickstart-spring-boot',
        'getting-started/quickstart-java',
      ],
    },
    {
      type: 'category',
      label: 'Whaleal SMS 云平台',
      items: [
        'platform/overview',
        'platform/getting-started',
        'platform/console',
        'platform/open-api',
        'platform/callback',
        'platform/plans',
        'platform/roadmap',
        'platform/faq',
      ],
    },
    {
      type: 'category',
      label: '使用指南',
      items: [
        'guide/features',
        'guide/api',
        'guide/providers',
      ],
    },
    {
      type: 'category',
      label: '参考',
      items: [
        'reference/ci-cd',
        'reference/github-pages',
      ],
    },
  ],
};

export default sidebars;
