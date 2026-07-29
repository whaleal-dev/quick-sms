package com.whaleal.ark.cloud.third.sms.outbound.adapter;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.outbound.sender.MockOutboundSender;
import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;
import com.whaleal.ark.cloud.third.sms.spi.SmsExtensionLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 下行短信适配器 - 主动发送短信
 * 负责向各个提供商主动发送短信消息
 *
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class OutboundAdapter {

    private final Map<String, OutboundSender> senderMap;

    public OutboundAdapter() {
        this.senderMap = new HashMap<>();
        initializeSenders();
    }

    private void initializeSenders() {
        MockOutboundSender mock = new MockOutboundSender();
        senderMap.put(SmsProviderKeys.normalize(mock.getSupportedProvider()), mock);
        senderMap.putAll(SmsExtensionLoader.loadProviders(OutboundSender.class, OutboundSender::getSupportedProvider));
        log.info("下行发送器初始化完成，支持 {} 个提供商", senderMap.size());
    }

    public SmsOutboundMessage sendMessage(SmsProviderType providerType, SmsOutboundMessage message, SmsProviderConfig config) {
        return sendMessage(resolveKey(providerType, config), providerType, message, config);
    }

    public SmsOutboundMessage sendMessage(String providerCode, SmsOutboundMessage message, SmsProviderConfig config) {
        return sendMessage(SmsProviderKeys.normalize(providerCode), SmsProviderType.tryFromCode(providerCode).orElse(null), message, config);
    }

    private SmsOutboundMessage sendMessage(String key, SmsProviderType providerType, SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.debug("开始发送短信，提供商: {}, 接收方: {}", key, message.getTo());

            OutboundSender sender = key == null ? null : senderMap.get(key);
            if (sender == null) {
                log.warn("未找到提供商 {} 的发送器", key);
                return createUnsupportedMessage(providerType, message);
            }

            SmsOutboundMessage result = sender.sendMessage(message, config);
            if (providerType != null) {
                result.setProviderType(providerType);
            }

            log.debug("短信发送完成，消息ID: {}, 状态: {}", result.getMessageId(), result.getSendStatus());
            return result;
        } catch (Exception e) {
            log.error("发送短信失败，提供商: {}, 接收方: {}, 错误: {}", key, message.getTo(), e.getMessage(), e);
            return createErrorMessage(providerType, message, e.getMessage());
        }
    }

    public List<SmsOutboundMessage> sendMessages(SmsProviderType providerType, List<SmsOutboundMessage> messages, SmsProviderConfig config) {
        String key = resolveKey(providerType, config);
        try {
            log.debug("开始批量发送短信，提供商: {}, 消息数量: {}", key, messages.size());

            OutboundSender sender = key == null ? null : senderMap.get(key);
            if (sender == null) {
                log.warn("未找到提供商 {} 的发送器", key);
                return messages.stream()
                        .map(msg -> createUnsupportedMessage(providerType, msg))
                        .toList();
            }

            List<SmsOutboundMessage> results = sender.sendMessages(messages, config);
            if (providerType != null) {
                results.forEach(result -> result.setProviderType(providerType));
            }
            log.debug("批量短信发送完成，发送数量: {}, 返回数量: {}", messages.size(), results.size());
            return results;
        } catch (Exception e) {
            log.error("批量发送短信失败，提供商: {}, 错误: {}", key, e.getMessage(), e);
            return messages.stream()
                    .map(msg -> createErrorMessage(providerType, msg, e.getMessage()))
                    .toList();
        }
    }

    public SmsOutboundMessage sendTemplateMessage(SmsProviderType providerType, SmsOutboundMessage message, SmsProviderConfig config) {
        String key = resolveKey(providerType, config);
        try {
            log.debug("开始发送模板短信，提供商: {}, 模板ID: {}", key,
                    message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "未知");

            OutboundSender sender = key == null ? null : senderMap.get(key);
            if (sender == null) {
                log.warn("未找到提供商 {} 的发送器", key);
                return createUnsupportedMessage(providerType, message);
            }

            SmsOutboundMessage result = sender.sendTemplateMessage(message, config);
            if (providerType != null) {
                result.setProviderType(providerType);
            }
            log.debug("模板短信发送完成，消息ID: {}, 状态: {}", result.getMessageId(), result.getSendStatus());
            return result;
        } catch (Exception e) {
            log.error("发送模板短信失败，提供商: {}, 错误: {}", key, e.getMessage(), e);
            return createErrorMessage(providerType, message, e.getMessage());
        }
    }

    private String resolveKey(SmsProviderType providerType, SmsProviderConfig config) {
        return com.whaleal.ark.cloud.third.sms.support.ProviderSupport.resolveKey(providerType, config);
    }

    private SmsOutboundMessage createUnsupportedMessage(SmsProviderType providerType, SmsOutboundMessage originalMessage) {
        SmsOutboundMessage message = SmsOutboundMessage.builder()
                .messageId(originalMessage.getMessageId())
                .providerType(providerType)
                .from(originalMessage.getFrom())
                .to(originalMessage.getTo())
                .content(originalMessage.getContent())
                .sendStatus(SmsOutboundMessage.SendStatus.FAILED)
                .build();
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        message.getExtraInfo().put("error", "该提供商不支持短信发送");
        return message;
    }

    private SmsOutboundMessage createErrorMessage(SmsProviderType providerType, SmsOutboundMessage originalMessage, String errorMessage) {
        SmsOutboundMessage message = SmsOutboundMessage.builder()
                .messageId(originalMessage.getMessageId())
                .providerType(providerType)
                .from(originalMessage.getFrom())
                .to(originalMessage.getTo())
                .content(originalMessage.getContent())
                .sendStatus(SmsOutboundMessage.SendStatus.FAILED)
                .build();
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        message.getExtraInfo().put("error", "短信发送失败: " + errorMessage);
        return message;
    }

    public boolean isSupported(SmsProviderType providerType) {
        return isSupported(SmsProviderKeys.of(providerType));
    }

    public boolean isSupported(String providerCode) {
        String key = SmsProviderKeys.normalize(providerCode);
        return key != null && senderMap.containsKey(key);
    }

    public Set<String> getSupportedProviderCodes() {
        return Set.copyOf(senderMap.keySet());
    }

    /**
     * @deprecated 扩展厂商可能无枚举，请使用 {@link #getSupportedProviderCodes()}
     */
    @Deprecated
    public SmsProviderType[] getSupportedProviders() {
        return senderMap.keySet().stream()
                .map(SmsProviderType::tryFromCode)
                .flatMap(java.util.Optional::stream)
                .toArray(SmsProviderType[]::new);
    }
}
