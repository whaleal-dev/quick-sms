package com.whaleal.ark.cloud.third.sms.spi;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * 通过 Java SPI 加载各供应商扩展实现。
 * <p>
 * 注册键为规范化后的 provider code（小写）。核心枚举与扩展厂商共用同一查找表，
 * 扩展厂商不必写入 {@link com.whaleal.ark.cloud.third.sms.enums.SmsProviderType}。
 * <p>
 * 位于 {@code sms-core}，供 runtime 与后续扩展共用。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public final class SmsExtensionLoader {

    private SmsExtensionLoader() {
    }

    /**
     * 按 provider code 加载 SPI 实现。
     */
    public static <T> Map<String, T> loadProviders(Class<T> serviceType,
                                                   Function<T, String> providerNameExtractor) {
        Map<String, T> providers = new HashMap<>();
        ServiceLoader<T> loader = ServiceLoader.load(serviceType, Thread.currentThread().getContextClassLoader());
        for (T implementation : loader) {
            String key = SmsProviderKeys.normalize(providerNameExtractor.apply(implementation));
            if (key == null) {
                log.warn("跳过 provider code 为空的 {} 实现: {}",
                        serviceType.getSimpleName(), implementation.getClass().getName());
                continue;
            }
            T existing = providers.put(key, implementation);
            if (existing != null) {
                log.warn("供应商 {} 存在多个 {} 实现，使用后加载的实现: {}",
                        key, serviceType.getSimpleName(), implementation.getClass().getName());
            } else {
                Optional<SmsProviderType> known = SmsProviderType.tryFromCode(key);
                if (known.isPresent()) {
                    log.debug("已加载核心供应商 {} ({}) -> {}", known.get(), key, implementation.getClass().getName());
                } else {
                    log.debug("已加载扩展供应商 code={} -> {}", key, implementation.getClass().getName());
                }
            }
        }
        return Collections.unmodifiableMap(providers);
    }
}
