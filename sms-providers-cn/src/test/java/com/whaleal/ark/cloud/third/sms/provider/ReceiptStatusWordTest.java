package com.whaleal.ark.cloud.third.sms.provider;

import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt.ReceiptStatus;
import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回执「状态词」分类的跨厂商不变量测试（CN 线）。
 *
 * <p>解析器列表由 {@link ServiceLoader} 从 SPI 注册里取 —— <b>新增解析器自动纳入</b>，
 * 不会出现「测试里写死一份名单、新加的文件漏测」。</p>
 *
 * <p>本类守的两条不变量（都是 2026-10-09 复核时真实存在的缺陷）：</p>
 * <ol>
 *   <li><b>认不出的状态词不得判成终态</b>（任何方向）。平台以 {@code delivered}/{@code failed}
 *       为终态闸门，误报终态<b>不可恢复</b>（后续真实回执被永久挡住）。
 *       反例（本轮修掉的 {@code ChinaMobileReceiptParser}）：{@code "DELIVRD".equals(s) ?
 *       DELIVERED : FAILED} ⇒ 中间态 {@code ACCEPTD} 被判成终态失败，一条成功投递的消息
 *       永远报「失败」。</li>
 *   <li><b>子串判定必须先排否定词</b>。反例（本轮修掉的 17 个 CN 解析器）：
 *       {@code contains("deliv")} 排在 {@code contains("undeliv")} 之前，而
 *       {@code "undeliv"} / {@code "not_delivered"} <b>都含子串 {@code "deliv"}</b>
 *       ⇒ 投递失败被吃成「已送达」。</li>
 * </ol>
 */
class ReceiptStatusWordTest {

    /** SPI 里注册的 CN 线解析器（当前 24 个）。断言它别空，否则本测试会「全过但什么都没查」 */
    private static final int MIN_REGISTERED_PARSERS = 20;

    /**
     * 子串匹配型解析器的探针：{@code "deliv"} 是<b>词根而非状态词</b> ——
     * 精确匹配型（如阿里云的 {@code switch("DELIVRD"/"UNDELIV"/…)}）会判 UNKNOWN，
     * 只有「含 deliv 就算成功」的子串实现才会判 DELIVERED。
     * 用它自动识别实现形态，避免把测试名单写死。
     */
    private static final String SUBSTRING_PROBE = "deliv";

    private static List<ReceiptParser> allParsers() {
        List<ReceiptParser> list = new ArrayList<>();
        ServiceLoader.load(ReceiptParser.class).forEach(list::add);
        return list;
    }

    /**
     * 反射取 {@code private SmsReceipt.ReceiptStatus parseStatus(String)}。
     * 返回 null = 该解析器把状态判定写在别处，无从在此测试（本测试不假装配齐）。
     */
    private static Method parseStatusOf(ReceiptParser parser) {
        try {
            Method m = parser.getClass().getDeclaredMethod("parseStatus", String.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static ReceiptStatus classify(Method m, ReceiptParser parser, String word) throws Exception {
        return (ReceiptStatus) m.invoke(parser, word);
    }

    private static boolean isSubstringBased(Method m, ReceiptParser parser) throws Exception {
        return classify(m, parser, SUBSTRING_PROBE) == ReceiptStatus.DELIVERED;
    }

    /** 可按 parseStatus 直接测的解析器；顺带断言确实测到了东西 */
    private static List<Object[]> testable() {
        List<ReceiptParser> parsers = allParsers();
        assertTrue(parsers.size() >= MIN_REGISTERED_PARSERS,
                "SPI 只加载到 " + parsers.size() + " 个解析器，测试等于没跑（检查 META-INF/services 是否在测试类路径上）");
        List<Object[]> out = new ArrayList<>();
        for (ReceiptParser p : parsers) {
            Method m = parseStatusOf(p);
            if (m != null) {
                out.add(new Object[]{p, m});
            }
        }
        assertTrue(!out.isEmpty(), "没有任何解析器被真正测到");
        return out;
    }

    @Test
    void unknownWordIsNeverATerminalStatus() throws Exception {
        // 不变量 1（对**所有**解析器）：认不出的词只能 UNKNOWN。
        // 判 FAILED 同样有害：终态闸门一旦落下，后续真实的 DELIVRD 就再也进不来了。
        for (Object[] pair : testable()) {
            ReceiptParser parser = (ReceiptParser) pair[0];
            Method m = (Method) pair[1];
            for (String garbage : List.of("zzz", "WEIRD_STATUS", "not-a-status")) {
                assertEquals(ReceiptStatus.UNKNOWN, classify(m, parser, garbage),
                        parser.getClass().getSimpleName() + " 把认不出的词 \"" + garbage
                                + "\" 判成了终态（应 UNKNOWN）");
            }
        }
    }

    @Test
    void substringParsersNeverClassifyFailuresAsDelivered() throws Exception {
        // 不变量 2：含否定语义、且**都含子串 "deliv"** 的词，绝不能被判成已送达
        List<String> failureWords = List.of(
                "undeliv", "UNDELIV", "undeliverable", "UNDELIVERABLE",
                "not_delivered", "NOT_DELIVERED", "notdelivered",
                "fail", "FAIL", "failed", "FAILED", "error");

        int tested = 0;
        for (Object[] pair : testable()) {
            ReceiptParser parser = (ReceiptParser) pair[0];
            Method m = (Method) pair[1];
            if (!isSubstringBased(m, parser)) {
                continue;   // 精确匹配型：结构上无本缺陷（由不变量 1 覆盖）
            }
            tested++;
            for (String word : failureWords) {
                ReceiptStatus got = classify(m, parser, word);
                assertNotEquals(ReceiptStatus.DELIVERED, got,
                        parser.getClass().getSimpleName() + " 把失败词 \"" + word + "\" 判成了已送达"
                                + "（子串判定未先排否定词）");
                assertEquals(ReceiptStatus.FAILED, got,
                        parser.getClass().getSimpleName() + " 对 \"" + word + "\" 应判 FAILED，实际 " + got);
            }
        }
        assertTrue(tested >= 1, "没有识别到任何子串匹配型解析器 —— 子串探针失效了");
    }

    @Test
    void substringParsersStillClassifySuccess() throws Exception {
        // 正向对照：没有这条，「把所有词都判 FAILED」也能让上面那个测试变绿
        int tested = 0;
        for (Object[] pair : testable()) {
            ReceiptParser parser = (ReceiptParser) pair[0];
            Method m = (Method) pair[1];
            if (!isSubstringBased(m, parser)) {
                continue;
            }
            tested++;
            for (String word : List.of("delivered", "DELIVERED", "success", "SUCCESS")) {
                assertEquals(ReceiptStatus.DELIVERED, classify(m, parser, word),
                        parser.getClass().getSimpleName() + " 丢失了成功词的判定：" + word);
            }
        }
        assertTrue(tested >= 1);
    }

    @Test
    void substringStatusWordsAreLocaleIndependent() throws Exception {
        // 状态词是 ASCII：默认 locale 为 tr_TR 时 "UNDELIV".toLowerCase() 得到 "undelıv"
        // （无点 i）⇒ 含 "undeliv" 的判定全部落空 ⇒ 静默退化成 UNKNOWN。必须走 Locale.ROOT。
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            for (Object[] pair : testable()) {
                ReceiptParser parser = (ReceiptParser) pair[0];
                Method m = (Method) pair[1];
                if (!isSubstringBased(m, parser)) {
                    continue;
                }
                assertEquals(ReceiptStatus.FAILED, classify(m, parser, "UNDELIV"),
                        parser.getClass().getSimpleName() + " 的状态词判定依赖默认 locale");
                assertEquals(ReceiptStatus.DELIVERED, classify(m, parser, "DELIVERED"),
                        parser.getClass().getSimpleName() + " 的状态词判定依赖默认 locale");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
