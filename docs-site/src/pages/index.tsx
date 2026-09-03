import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

const quickRoutes = [
  {
    title: '短信概念',
    description: '出站、入站、状态报告与 Webhook 回调。',
    to: '/docs/concepts/overview',
  },
  {
    title: 'Spring Boot 接入',
    description: '依赖、Bean、发信与回调入口。',
    to: '/docs/getting-started/quickstart-spring-boot',
  },
  {
    title: '纯 Java 调用',
    description: 'SmsClients.builder() 一行发信。',
    to: '/docs/getting-started/quickstart-java',
  },
  {
    title: '厂商说明',
    description: '国内 / 国际通道凭证与字段。',
    to: '/docs/guide/providers',
  },
];

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className="button button--secondary button--lg" to="/docs/intro">
            开始阅读文档
          </Link>
          <Link
            className="button button--info button--lg margin-left--md"
            to="/docs/concepts/overview">
            短信概念
          </Link>
        </div>
      </div>
    </header>
  );
}

function QuickNavigation() {
  return (
    <section className={styles.quickRoutes} aria-labelledby="quick-routes-heading">
      <div className="container">
        <Heading as="h2" id="quick-routes-heading">
          按任务进入
        </Heading>
        <p className={styles.quickRoutesIntro}>
          Quick SMS 统一封装多厂商短信 API。从一个入口开始即可。
        </p>
        <div className={styles.quickRouteGrid}>
          {quickRoutes.map((route) => (
            <Link className={styles.quickRoute} key={route.to} to={route.to}>
              <Heading as="h3">{route.title}</Heading>
              <p>{route.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="Quick SMS：多供应商短信聚合 SDK，覆盖出站、入站、Report 与 Webhook。">
      <HomepageHeader />
      <main>
        <QuickNavigation />
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
