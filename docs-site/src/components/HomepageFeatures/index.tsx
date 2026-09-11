import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: '统一出站 API',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        <code>sendText</code> / <code>sendTemplate</code> 覆盖验证码与通知；多通道 failover 与动态凭证面向 SaaS。
      </>
    ),
  },
  {
    title: '回执 · 上行 · Report',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        Webhook 解析回执与用户回复，主动拉取状态报告；短信网关闭环能力一等公民。
      </>
    ),
  },
  {
    title: '国内 + 国际',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        分模块引入 <code>sms-providers-cn</code> / <code>intl</code>，或直接用 <code>sms-all</code>。
      </>
    ),
  },
];

function Feature({title, Svg, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
