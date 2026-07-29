package com.whaleal.ark.cloud.third.sms.outbound.sender;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Mock测试平台短信发送器
 * 用于测试和开发环境的模拟短信发送
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class MockOutboundSender implements OutboundSender {
    
    private final Random random = new Random();
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        log.info("Mock发送短信 - 接收方: {}, 内容: {}", message.getTo(), message.getContent());
        
        // 模拟发送延迟
        simulateDelay();
        
        // 模拟发送结果
        SmsOutboundMessage.SendStatus status = simulateSendStatus();
        
        // 设置发送结果
        message.setMessageId(generateMockMessageId());
        message.setProviderMessageId("mock_provider_" + System.currentTimeMillis());
        message.setSendStatus(status);
        message.setSentTime(LocalDateTime.now());
        message.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(random.nextInt(5) + 1));
        
        // 设置费用信息
        message.setCostInfo(createMockCostInfo(message));
        
        // 设置扩展信息
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        message.getExtraInfo().put("mock_simulation", true);
        message.getExtraInfo().put("mock_delay_ms", getLastSimulatedDelay());
        message.getExtraInfo().put("mock_provider", "Mock Test Platform");
        
        log.info("Mock发送完成 - 消息ID: {}, 状态: {}", message.getMessageId(), status);
        
        return message;
    }
    
    @Override
    public List<SmsOutboundMessage> sendMessages(List<SmsOutboundMessage> messages, SmsProviderConfig config) {
        log.info("Mock批量发送短信 - 数量: {}", messages.size());
        
        return messages.stream()
                .map(msg -> sendMessage(msg, config))
                .toList();
    }
    
    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        log.info("Mock发送模板短信 - 模板ID: {}",
                message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "未知");

        String content = message.getContent();
        if (content == null || content.isBlank()) {
            String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "unknown";
            content = "[MOCK_TEMPLATE:" + templateId + "]";
        }
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            for (Map.Entry<String, String> entry : message.getBusinessInfo().getTemplateParams().entrySet()) {
                content = content.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }
        message.setContent(content);

        return sendMessage(message, config);
    }
    
    /**
     * 模拟发送延迟
     */
    private void simulateDelay() {
        try {
            int delay = random.nextInt(500) + 100; // 100-600ms随机延迟
            Thread.sleep(delay);
            this.lastSimulatedDelay = delay;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private int lastSimulatedDelay = 0;
    
    private int getLastSimulatedDelay() {
        return lastSimulatedDelay;
    }
    
    /**
     * 模拟发送状态（Mock 默认始终成功，便于本地开发与集成测试）
     */
    private SmsOutboundMessage.SendStatus simulateSendStatus() {
        return SmsOutboundMessage.SendStatus.SENT;
    }
    
    /**
     * 生成Mock消息ID
     */
    private String generateMockMessageId() {
        return "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 创建模拟费用信息
     */
    private SmsOutboundMessage.CostInfo createMockCostInfo(SmsOutboundMessage message) {
        // 根据内容长度计算模拟费用
        int contentLength = message.getContent() != null ? message.getContent().length() : 0;
        int messageCount = (contentLength / 70) + 1; // 假设70字符一条
        
        String[] amounts = {"0.045", "0.050", "0.055", "0.060"};
        String[] currencies = {"CNY", "USD", "EUR"};
        
        return SmsOutboundMessage.CostInfo.builder()
                .amount(amounts[random.nextInt(amounts.length)])
                .currency(currencies[random.nextInt(currencies.length)])
                .billingType("per_message")
                .messageCount(messageCount)
                .unitPrice("0.050")
                .billingTime(LocalDateTime.now())
                .build();
    }
    
    @Override
    public String getSupportedProvider() {
        return "MOCK";
    }
} 