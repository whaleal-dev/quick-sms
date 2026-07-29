package com.whaleal.ark.cloud.third.sms.config;

import com.whaleal.ark.cloud.third.sms.client.DefaultSmsClient;
import com.whaleal.ark.cloud.third.sms.client.DefaultSmsWebhookHandler;
import com.whaleal.ark.cloud.third.sms.client.SmsClient;
import com.whaleal.ark.cloud.third.sms.client.SmsWebhookHandler;
import com.whaleal.ark.cloud.third.sms.core.SmsModuleManager;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.adapter.InboundAdapter;
import com.whaleal.ark.cloud.third.sms.metrics.CollectorSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import com.whaleal.ark.cloud.third.sms.outbound.adapter.OutboundAdapter;
import com.whaleal.ark.cloud.third.sms.receipt.adapter.ReceiptAdapter;
import com.whaleal.ark.cloud.third.sms.report.adapter.ReportAdapter;
import com.whaleal.ark.cloud.third.sms.validation.PhoneValidationAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * SMS SDK Spring Boot 自动配置（仅注册 Bean，不读取 application.yml）
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@AutoConfiguration
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboundAdapter outboundAdapter() {
        return new OutboundAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public InboundAdapter inboundAdapter() {
        return new InboundAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReceiptAdapter receiptAdapter() {
        return new ReceiptAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReportAdapter reportAdapter() {
        return new ReportAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public PhoneValidationAdapter phoneValidationAdapter() {
        return new PhoneValidationAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public SmsModuleManager smsModuleManager(OutboundAdapter outboundAdapter,
                                             InboundAdapter inboundAdapter,
                                             ReceiptAdapter receiptAdapter,
                                             ReportAdapter reportAdapter) {
        return new SmsModuleManager(receiptAdapter, inboundAdapter, reportAdapter, outboundAdapter);
    }

    @Bean
    @ConditionalOnMissingBean(SmsMetrics.class)
    public SmsMetrics smsMetrics() {
        return CollectorSmsMetrics.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(SmsClient.class)
    public SmsClient smsClient(SmsModuleManager smsModuleManager, SmsMetrics smsMetrics) {
        return new DefaultSmsClient(
                SmsProviderConfig.builder()
                        .providerType(SmsProviderType.MOCK)
                        .name(SmsProviderType.MOCK.getDisplayName())
                        .build(),
                smsModuleManager,
                null,
                null,
                smsMetrics);
    }

    @Bean
    @ConditionalOnMissingBean(SmsWebhookHandler.class)
    public SmsWebhookHandler smsWebhookHandler(SmsModuleManager smsModuleManager,
                                               PhoneValidationAdapter phoneValidationAdapter) {
        return new DefaultSmsWebhookHandler(
                smsModuleManager,
                phoneValidationAdapter,
                SmsProviderConfig.builder()
                        .providerType(SmsProviderType.MOCK)
                        .name(SmsProviderType.MOCK.getDisplayName())
                        .build());
    }
}
