/**
 * Quick SMS 公共核心层。
 * <p>
 * 放置 runtime 与 providers 共用的 SPI 装配、工具类与实现侧抽象，
 * 不包含具体厂商协议，也不暴露 Spring / 默认 {@code SmsClient} 装配。
 *
 * <pre>
 * sms-api  → 对外契约（接口、DTO、核心枚举）
 * sms-core → 公共抽象与工具（本模块）
 * sms-runtime / sms-providers-* → 依赖本模块
 * </pre>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
package com.whaleal.ark.cloud.third.sms;
