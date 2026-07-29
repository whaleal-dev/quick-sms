package com.whaleal.ark.cloud.third.sms.provider.twilio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.validation.entity.PhoneValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.validation.validator.PhoneValidator;

/**
 * Twilio号码校验器
 * 使用Twilio Lookup API进行号码校验
 * 
 * API文档: https://www.twilio.com/docs/lookup/api
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class TwilioPhoneValidator implements PhoneValidator {
    
    private static final String TWILIO_LOOKUP_API_URL = "https://lookups.twilio.com/v2/PhoneNumbers/";
    private static final int TIMEOUT_MS = 10000;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public PhoneValidationResult validate(String phoneNumber, SmsProviderConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始Twilio校验号码: {}", phoneNumber);
            
            // 检查配置
            if (config == null || config.getApiKey() == null || config.getApiSecret() == null) {
                return createErrorResult(phoneNumber, "Twilio配置不完整，需要Account SID和Auth Token", startTime);
            }
            
            // 调用Twilio Lookup API
            String response = callTwilioLookupApi(phoneNumber, config);
            
            // 解析响应
            PhoneValidationResult result = parseResponse(phoneNumber, response, startTime);
            
            log.debug("Twilio校验完成: {} -> {}", phoneNumber, result.getStatus());
            
            return result;
            
        } catch (Exception e) {
            log.error("Twilio号码校验失败: {}", phoneNumber, e);
            return createErrorResult(phoneNumber, "Twilio API调用失败: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * 调用Twilio Lookup API
     */
    private String callTwilioLookupApi(String phoneNumber, SmsProviderConfig config) throws Exception {
        // 编码号码
        String encodedNumber = URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8);
        
        // 构建请求URL，包含carrier和caller-name信息
        String url = TWILIO_LOOKUP_API_URL + encodedNumber + "?Fields=carrier,caller_name";
        
        // 创建HTTP客户端
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            
            // 设置Basic认证
            String auth = config.getApiKey() + ":" + config.getApiSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            request.setHeader("Authorization", "Basic " + encodedAuth);
            
            // 设置请求头
            request.setHeader("Accept", "application/json");
            request.setHeader("User-Agent", "SMS-SDK/1.0");
            
            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                if (statusCode == 200) {
                    return responseBody;
                } else if (statusCode == 404) {
                    // 号码无效
                    return createInvalidNumberResponse(phoneNumber);
                } else {
                    throw new RuntimeException("Twilio API返回错误状态码: " + statusCode + ", 响应: " + responseBody);
                }
            }
        }
    }
    
    /**
     * 解析Twilio API响应
     */
    private PhoneValidationResult parseResponse(String originalNumber, String responseBody, long startTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // 基本信息
            String phoneNumber = root.path("phone_number").asText();
            String countryCode = root.path("country_code").asText();
            boolean valid = root.path("valid").asBoolean(false);
            
            PhoneValidationResult.PhoneValidationResultBuilder builder = PhoneValidationResult.builder()
                    .originalNumber(originalNumber)
                    .standardizedNumber(phoneNumber)
                    .status(valid ? PhoneValidationResult.ValidationStatus.VALID : PhoneValidationResult.ValidationStatus.INVALID)
                    .isValid(valid)
                    .countryCode(countryCode)
                    .validatedTime(new java.util.Date())
                    .validationDuration(System.currentTimeMillis() - startTime);
            
            // 国家信息
            if (root.has("country_code")) {
                builder.countryName(getCountryName(countryCode));
            }
            
            // 号码类型
            String nationalFormat = root.path("national_format").asText();
            PhoneValidationResult.NumberType numberType = determineNumberType(nationalFormat);
            builder.numberType(numberType)
                   .isMobile(numberType == PhoneValidationResult.NumberType.MOBILE)
                   .canReceiveSms(numberType == PhoneValidationResult.NumberType.MOBILE);
            
            // 运营商信息
            if (root.has("carrier")) {
                JsonNode carrier = root.get("carrier");
                builder.carrierInfo(PhoneValidationResult.CarrierInfo.builder()
                        .name(carrier.path("name").asText())
                        .code(carrier.path("mobile_country_code").asText())
                        .mcc(carrier.path("mobile_country_code").asText())
                        .mnc(carrier.path("mobile_network_code").asText())
                        .networkType(carrier.path("type").asText())
                        .build());
            }
            
            // 风险评估
            builder.riskAssessment(PhoneValidationResult.RiskAssessment.builder()
                    .riskLevel(PhoneValidationResult.RiskLevel.LOW)
                    .riskScore(15)
                    .isDisposable(false)
                    .isVirtual(false)
                    .riskDescription("Twilio Lookup API校验")
                    .build());
            
            // 原始数据
            Map<String, Object> rawData = new HashMap<>();
            rawData.put("twilio_response", responseBody);
            builder.rawData(rawData);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("解析Twilio响应失败: {}", responseBody, e);
            return createErrorResult(originalNumber, "解析Twilio响应失败: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * 创建无效号码响应
     */
    private String createInvalidNumberResponse(String phoneNumber) {
        return String.format("""
            {
                "phone_number": "%s",
                "valid": false,
                "country_code": "unknown",
                "national_format": "%s"
            }
            """, phoneNumber, phoneNumber);
    }
    
    /**
     * 判断号码类型
     */
    private PhoneValidationResult.NumberType determineNumberType(String nationalFormat) {
        // 简单的号码类型判断逻辑
        if (nationalFormat != null && nationalFormat.matches(".*[0-9]{10,}.*")) {
            return PhoneValidationResult.NumberType.MOBILE;
        }
        return PhoneValidationResult.NumberType.UNKNOWN;
    }
    
    /**
     * 获取国家名称
     */
    private String getCountryName(String countryCode) {
        return switch (countryCode.toUpperCase()) {
            case "CN" -> "中国";
            case "US" -> "美国";
            case "GB" -> "英国";
            case "JP" -> "日本";
            case "KR" -> "韩国";
            case "DE" -> "德国";
            case "FR" -> "法国";
            case "AU" -> "澳大利亚";
            case "CA" -> "加拿大";
            case "IN" -> "印度";
            default -> countryCode;
        };
    }
    
    /**
     * 创建错误结果
     */
    private PhoneValidationResult createErrorResult(String originalNumber, String errorMessage, long startTime) {
        return PhoneValidationResult.builder()
                .originalNumber(originalNumber)
                .status(PhoneValidationResult.ValidationStatus.ERROR)
                .isValid(false)
                .errorMessage(errorMessage)
                .validatedTime(new java.util.Date())
                .validationDuration(System.currentTimeMillis() - startTime)
                .build();
    }
    
    @Override
    public String getSupportedProvider() {
        return "TWILIO";
    }
    
    @Override
    public boolean supportsBatchValidation() {
        return false; // Twilio Lookup API不支持批量查询
    }
    
    @Override
    public String getCostInfo() {
        return "付费API - 基础查询 $0.005/次，运营商信息 $0.01/次";
    }
    
    @Override
    public String getLimitInfo() {
        return "API限制：1000次/秒";
    }
} 