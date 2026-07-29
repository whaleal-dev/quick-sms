package com.whaleal.ark.cloud.third.sms.provider.vonage;

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
import java.util.HashMap;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.validation.validator.PhoneValidator;

/**
 * Vonage号码校验器
 * 使用Vonage Number Insight API进行号码校验
 * 
 * API文档: https://developer.vonage.com/en/number-insight/overview
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class VonagePhoneValidator implements PhoneValidator {
    
    private static final String VONAGE_INSIGHT_API_URL = "https://api.nexmo.com/ni/";
    private static final int TIMEOUT_MS = 10000;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public PhoneValidationResult validate(String phoneNumber, SmsProviderConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始Vonage校验号码: {}", phoneNumber);
            
            // 检查配置
            if (config == null || config.getApiKey() == null || config.getApiSecret() == null) {
                return createErrorResult(phoneNumber, "Vonage配置不完整，需要API Key和API Secret", startTime);
            }
            
            // 先尝试基础校验（免费）
            String basicResponse = callVonageBasicApi(phoneNumber, config);
            PhoneValidationResult basicResult = parseBasicResponse(phoneNumber, basicResponse, startTime);
            
            // 如果基础校验成功，尝试标准校验（付费，获取更多信息）
            if (basicResult.getIsValid() != null && basicResult.getIsValid()) {
                try {
                    String standardResponse = callVonageStandardApi(phoneNumber, config);
                    return parseStandardResponse(phoneNumber, standardResponse, startTime);
                } catch (Exception e) {
                    log.warn("Vonage标准校验失败，返回基础校验结果: {}", e.getMessage());
                    return basicResult;
                }
            }
            
            return basicResult;
            
        } catch (Exception e) {
            log.error("Vonage号码校验失败: {}", phoneNumber, e);
            return createErrorResult(phoneNumber, "Vonage API调用失败: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * 调用Vonage基础校验API（免费）
     */
    private String callVonageBasicApi(String phoneNumber, SmsProviderConfig config) throws Exception {
        return callVonageApi(phoneNumber, config, "basic");
    }
    
    /**
     * 调用Vonage标准校验API（付费）
     */
    private String callVonageStandardApi(String phoneNumber, SmsProviderConfig config) throws Exception {
        return callVonageApi(phoneNumber, config, "standard");
    }
    
    /**
     * 调用Vonage Number Insight API
     */
    private String callVonageApi(String phoneNumber, SmsProviderConfig config, String level) throws Exception {
        // 编码号码
        String encodedNumber = URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8);
        
        // 构建请求URL
        String url = String.format("%s%s?api_key=%s&api_secret=%s&number=%s",
                VONAGE_INSIGHT_API_URL, level,
                URLEncoder.encode(config.getApiKey(), StandardCharsets.UTF_8),
                URLEncoder.encode(config.getApiSecret(), StandardCharsets.UTF_8),
                encodedNumber);
        
        // 创建HTTP客户端
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            
            // 设置请求头
            request.setHeader("Accept", "application/json");
            request.setHeader("User-Agent", "SMS-SDK/1.0");
            
            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                if (statusCode == 200) {
                    return responseBody;
                } else {
                    throw new RuntimeException("Vonage API返回错误状态码: " + statusCode + ", 响应: " + responseBody);
                }
            }
        }
    }
    
    /**
     * 解析基础校验响应
     */
    private PhoneValidationResult parseBasicResponse(String originalNumber, String responseBody, long startTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // 检查状态
            int status = root.path("status").asInt();
            if (status != 0) {
                String errorText = root.path("status_message").asText("Unknown error");
                return createErrorResult(originalNumber, "Vonage API错误: " + errorText, startTime);
            }
            
            // 基本信息
            String internationalFormat = root.path("international_format_number").asText();
            String nationalFormat = root.path("national_format_number").asText();
            String countryCode = root.path("country_code").asText();
            String countryName = root.path("country_name").asText();
            
            // 构建基础结果
            return PhoneValidationResult.builder()
                    .originalNumber(originalNumber)
                    .standardizedNumber(internationalFormat)
                    .status(PhoneValidationResult.ValidationStatus.VALID)
                    .isValid(true)
                    .countryCode(countryCode)
                    .countryName(countryName)
                    .numberType(PhoneValidationResult.NumberType.MOBILE) // 基础API无法确定类型
                    .isMobile(true)
                    .canReceiveSms(true)
                    .validatedTime(new java.util.Date())
                    .validationDuration(System.currentTimeMillis() - startTime)
                    .riskAssessment(PhoneValidationResult.RiskAssessment.builder()
                            .riskLevel(PhoneValidationResult.RiskLevel.LOW)
                            .riskScore(10)
                            .isDisposable(false)
                            .isVirtual(false)
                            .riskDescription("Vonage基础校验")
                            .build())
                    .build();
            
        } catch (Exception e) {
            log.error("解析Vonage基础响应失败: {}", responseBody, e);
            return createErrorResult(originalNumber, "解析Vonage响应失败: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * 解析标准校验响应
     */
    private PhoneValidationResult parseStandardResponse(String originalNumber, String responseBody, long startTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // 检查状态
            int status = root.path("status").asInt();
            if (status != 0) {
                String errorText = root.path("status_message").asText("Unknown error");
                return createErrorResult(originalNumber, "Vonage API错误: " + errorText, startTime);
            }
            
            // 基本信息
            String internationalFormat = root.path("international_format_number").asText();
            String nationalFormat = root.path("national_format_number").asText();
            String countryCode = root.path("country_code").asText();
            String countryName = root.path("country_name").asText();
            
            PhoneValidationResult.PhoneValidationResultBuilder builder = PhoneValidationResult.builder()
                    .originalNumber(originalNumber)
                    .standardizedNumber(internationalFormat)
                    .status(PhoneValidationResult.ValidationStatus.VALID)
                    .isValid(true)
                    .countryCode(countryCode)
                    .countryName(countryName)
                    .validatedTime(new java.util.Date())
                    .validationDuration(System.currentTimeMillis() - startTime);
            
            // 号码类型
            String currentCarrier = root.path("current_carrier").asText();
            String originalCarrier = root.path("original_carrier").asText();
            
            PhoneValidationResult.NumberType numberType = determineNumberType(root);
            builder.numberType(numberType)
                   .isMobile(numberType == PhoneValidationResult.NumberType.MOBILE)
                   .canReceiveSms(numberType == PhoneValidationResult.NumberType.MOBILE);
            
            // 运营商信息
            if (!currentCarrier.isEmpty()) {
                builder.carrierInfo(PhoneValidationResult.CarrierInfo.builder()
                        .name(currentCarrier)
                        .networkType("Mobile")
                        .build());
            }
            
            // 地理位置信息（如果有）
            if (root.has("lookup_outcome_message")) {
                String location = root.path("lookup_outcome_message").asText();
                builder.locationInfo(PhoneValidationResult.LocationInfo.builder()
                        .country(countryName)
                        .build());
            }
            
            // 风险评估
            boolean isRoaming = root.path("roaming").asBoolean(false);
            boolean isReachable = root.path("reachable").asText("unknown").equals("reachable");
            
            PhoneValidationResult.RiskLevel riskLevel = determineRiskLevel(isRoaming, isReachable);
            int riskScore = calculateRiskScore(isRoaming, isReachable);
            
            builder.riskAssessment(PhoneValidationResult.RiskAssessment.builder()
                    .riskLevel(riskLevel)
                    .riskScore(riskScore)
                    .isDisposable(false)
                    .isVirtual(false)
                    .riskDescription("Vonage标准校验，漫游状态: " + isRoaming + ", 可达性: " + isReachable)
                    .build());
            
            // 原始数据
            Map<String, Object> rawData = new HashMap<>();
            rawData.put("vonage_response", responseBody);
            builder.rawData(rawData);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("解析Vonage标准响应失败: {}", responseBody, e);
            return createErrorResult(originalNumber, "解析Vonage响应失败: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * 判断号码类型
     */
    private PhoneValidationResult.NumberType determineNumberType(JsonNode root) {
        // Vonage API可能包含号码类型信息
        String currentCarrier = root.path("current_carrier").asText();
        String originalCarrier = root.path("original_carrier").asText();
        
        // 如果有运营商信息，通常是移动号码
        if (!currentCarrier.isEmpty() || !originalCarrier.isEmpty()) {
            return PhoneValidationResult.NumberType.MOBILE;
        }
        
        return PhoneValidationResult.NumberType.UNKNOWN;
    }
    
    /**
     * 确定风险等级
     */
    private PhoneValidationResult.RiskLevel determineRiskLevel(boolean isRoaming, boolean isReachable) {
        if (!isReachable) {
            return PhoneValidationResult.RiskLevel.HIGH;
        } else if (isRoaming) {
            return PhoneValidationResult.RiskLevel.MEDIUM;
        } else {
            return PhoneValidationResult.RiskLevel.LOW;
        }
    }
    
    /**
     * 计算风险评分
     */
    private int calculateRiskScore(boolean isRoaming, boolean isReachable) {
        int score = 10; // 基础分数
        
        if (!isReachable) {
            score += 50; // 不可达，高风险
        }
        
        if (isRoaming) {
            score += 20; // 漫游，中等风险
        }
        
        return Math.min(score, 100);
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
        return "VONAGE";
    }
    
    @Override
    public boolean supportsBatchValidation() {
        return false; // Vonage Number Insight API不支持批量查询
    }
    
    @Override
    public String getCostInfo() {
        return "基础校验免费，标准校验 €0.0050/次，高级校验 €0.0150/次";
    }
    
    @Override
    public String getLimitInfo() {
        return "API限制：30次/秒";
    }
} 