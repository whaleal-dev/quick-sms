package com.whaleal.ark.cloud.third.sms.validation;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.validation.entity.PhoneValidationResult;
import com.whaleal.ark.cloud.third.sms.spi.SmsExtensionLoader;
import com.whaleal.ark.cloud.third.sms.validation.validator.GooglePhoneValidator;
import com.whaleal.ark.cloud.third.sms.validation.validator.LocalPhoneValidator;
import com.whaleal.ark.cloud.third.sms.validation.validator.PhoneValidator;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 号码校验适配器
 * 统一管理各种号码校验器，提供统一的校验接口
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class PhoneValidationAdapter {
    
    private final Map<String, PhoneValidator> validators = new ConcurrentHashMap<>();
    
    public PhoneValidationAdapter() {
        initializeValidators();
    }
    
    /**
     * 初始化所有校验器
     */
    private void initializeValidators() {
        validators.put(SmsProviderKeys.of(SmsProviderType.MOCK), new LocalPhoneValidator());
        validators.put(SmsProviderKeys.of(SmsProviderType.CUSTOM_HTTP), new GooglePhoneValidator());
        validators.putAll(SmsExtensionLoader.loadProviders(PhoneValidator.class, PhoneValidator::getSupportedProvider));
        log.info("号码校验适配器初始化完成，支持的校验器: {}", validators.keySet());
    }
    
    /**
     * 使用指定的校验器校验号码
     * 统一参数顺序：(providerType, phoneNumber, config)
     * 
     * @param providerType 提供商类型
     * @param phoneNumber 待校验的号码
     * @param config 提供商配置（某些校验器需要）
     * @return 校验结果
     */
    public PhoneValidationResult validate(SmsProviderType providerType, String phoneNumber, SmsProviderConfig config) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return createErrorResult(phoneNumber, "号码不能为空");
        }
        
        PhoneValidator validator = validators.get(SmsProviderKeys.of(providerType));
        if (validator == null) {
            // 降级到本地校验器
            validator = validators.get(SmsProviderKeys.of(SmsProviderType.MOCK));
            if (validator == null) {
                return createErrorResult(phoneNumber, "不支持的校验器类型: " + providerType);
            }
            log.warn("校验器类型 {} 不支持，降级到本地校验器", providerType);
        }
        
        try {
            log.debug("使用{}校验器校验号码: {}", providerType, phoneNumber);
            return validator.validate(phoneNumber, config);
        } catch (Exception e) {
            log.error("号码校验失败: validator={}, phone={}", providerType, phoneNumber, e);
            return createErrorResult(phoneNumber, "校验失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用默认校验器（本地校验器）校验号码
     * 
     * @param phoneNumber 待校验的号码
     * @return 校验结果
     */
    public PhoneValidationResult validate(String phoneNumber) {
        return validate(SmsProviderType.MOCK, phoneNumber, null);
    }
    
    /**
     * 批量校验号码
     * 统一参数顺序：(providerType, phoneNumbers, config)
     * 
     * @param providerType 提供商类型
     * @param phoneNumbers 待校验的号码列表
     * @param config 提供商配置
     * @return 校验结果列表
     */
    public List<PhoneValidationResult> validateBatch(SmsProviderType providerType, List<String> phoneNumbers, SmsProviderConfig config) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            return Collections.emptyList();
        }
        
        PhoneValidator validator = validators.get(SmsProviderKeys.of(providerType));
        if (validator == null) {
            // 降级到本地校验器
            validator = validators.get(SmsProviderKeys.of(SmsProviderType.MOCK));
            if (validator == null) {
                return phoneNumbers.stream()
                        .map(phone -> createErrorResult(phone, "不支持的校验器类型: " + providerType))
                        .collect(Collectors.toList());
            }
            log.warn("校验器类型 {} 不支持，降级到本地校验器", providerType);
        }
        
        // 创建一个final引用以便在lambda表达式中使用
        final PhoneValidator finalValidator = validator;
        
        try {
            log.debug("使用{}校验器批量校验{}个号码", providerType, phoneNumbers.size());
            
            if (finalValidator.supportsBatchValidation()) {
                return finalValidator.validateBatch(phoneNumbers, config);
            } else {
                // 如果不支持批量，则逐个校验
                return phoneNumbers.stream()
                        .map(phone -> finalValidator.validate(phone, config))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("批量号码校验失败: validator={}, count={}", providerType, phoneNumbers.size(), e);
            return phoneNumbers.stream()
                    .map(phone -> createErrorResult(phone, "批量校验失败: " + e.getMessage()))
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * 智能校验 - 先使用免费校验器，如果需要更详细信息再使用付费校验器
     * 
     * @param phoneNumber 待校验的号码
     * @param config 提供商配置（用于付费校验器）
     * @return 校验结果
     */
    public PhoneValidationResult smartValidate(String phoneNumber, SmsProviderConfig config) {
        // 1. 先使用本地校验器进行基础校验
        PhoneValidationResult localResult = validate(SmsProviderType.MOCK, phoneNumber, null);
        
        // 2. 如果本地校验失败，直接返回
        if (localResult.getIsValid() == null || !localResult.getIsValid()) {
            return localResult;
        }
        
        // 3. 如果本地校验成功，尝试使用Google校验器获取更准确的信息
        try {
            PhoneValidationResult googleResult = validate(SmsProviderType.CUSTOM_HTTP, phoneNumber, null);
            if (googleResult.getStatus() != PhoneValidationResult.ValidationStatus.ERROR) {
                return googleResult;
            }
        } catch (Exception e) {
            log.debug("Google校验器不可用，继续使用本地结果: {}", e.getMessage());
        }
        
        // 4. 如果需要更详细的信息且有配置，可以尝试付费校验器
        if (config != null && config.getApiKey() != null) {
            try {
                // 优先尝试Vonage（有免费的基础校验）
                PhoneValidationResult vonageResult = validate(SmsProviderType.VONAGE, phoneNumber, config);
                if (vonageResult.getStatus() != PhoneValidationResult.ValidationStatus.ERROR) {
                    return vonageResult;
                }
            } catch (Exception e) {
                log.debug("Vonage校验器调用失败: {}", e.getMessage());
            }
        }
        
        // 5. 返回本地校验结果
        return localResult;
    }
    
    /**
     * 快速格式校验
     * 
     * @param phoneNumber 待校验的号码
     * @return 是否为有效格式
     */
    public boolean isValidFormat(String phoneNumber) {
        PhoneValidator localValidator = validators.get(SmsProviderKeys.of(SmsProviderType.MOCK));
        return localValidator != null && localValidator.isValidFormat(phoneNumber);
    }
    
    /**
     * 检查是否支持指定提供商
     * 
     * @param providerType 提供商类型
     * @return 是否支持
     */
    public boolean isSupported(SmsProviderType providerType) {
        return isSupported(SmsProviderKeys.of(providerType));
    }

    public boolean isSupported(String providerCode) {
        String key = SmsProviderKeys.normalize(providerCode);
        return key != null && validators.containsKey(key);
    }
    
    /**
     * 获取所有支持的校验器信息
     * 
     * @return 校验器信息列表
     */
    public List<ValidatorInfo> getSupportedValidators() {
        List<ValidatorInfo> validatorInfos = new ArrayList<>();
        
        for (Map.Entry<String, PhoneValidator> entry : validators.entrySet()) {
            PhoneValidator validator = entry.getValue();
            validatorInfos.add(new ValidatorInfo(
                    entry.getKey(),
                    validator.getSupportedProvider(),
                    validator.getCostInfo(),
                    validator.getLimitInfo(),
                    validator.supportsBatchValidation()
            ));
        }
        
        return validatorInfos;
    }
    
    /**
     * 添加自定义校验器
     * 
     * @param providerType 提供商类型
     * @param validator 校验器实例
     */
    public void addValidator(SmsProviderType providerType, PhoneValidator validator) {
        validators.put(SmsProviderKeys.of(providerType), validator);
        log.info("添加自定义校验器: {}", providerType);
    }

    public void addValidator(String providerCode, PhoneValidator validator) {
        validators.put(SmsProviderKeys.normalize(providerCode), validator);
        log.info("添加自定义校验器: {}", providerCode);
    }
    
    /**
     * 移除校验器
     * 
     * @param providerType 提供商类型
     */
    public void removeValidator(SmsProviderType providerType) {
        validators.remove(SmsProviderKeys.of(providerType));
        log.info("移除校验器: {}", providerType);
    }
    
    /**
     * 创建错误结果
     */
    private PhoneValidationResult createErrorResult(String phoneNumber, String errorMessage) {
        return PhoneValidationResult.builder()
                .originalNumber(phoneNumber)
                .status(PhoneValidationResult.ValidationStatus.ERROR)
                .isValid(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 校验器信息
     */
    public static class ValidatorInfo {
        private final String name;
        private final String provider;
        private final String costInfo;
        private final String limitInfo;
        private final boolean supportsBatch;
        
        public ValidatorInfo(String name, String provider, String costInfo, String limitInfo, boolean supportsBatch) {
            this.name = name;
            this.provider = provider;
            this.costInfo = costInfo;
            this.limitInfo = limitInfo;
            this.supportsBatch = supportsBatch;
        }
        
        // Getters
        public String getName() { return name; }
        public String getProvider() { return provider; }
        public String getCostInfo() { return costInfo; }
        public String getLimitInfo() { return limitInfo; }
        public boolean isSupportsBatch() { return supportsBatch; }
        
        @Override
        public String toString() {
            return String.format("ValidatorInfo{name='%s', provider='%s', cost='%s', limit='%s', batch=%s}",
                    name, provider, costInfo, limitInfo, supportsBatch);
        }
    }
} 