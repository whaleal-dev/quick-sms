package com.whaleal.ark.cloud.third.sms.guard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手机号黑名单（内存实现，可替换）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class PhoneBlacklist {

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();

    public void block(String phone) {
        String n = normalize(phone);
        if (n != null) {
            blocked.add(n);
        }
    }

    public void unblock(String phone) {
        String n = normalize(phone);
        if (n != null) {
            blocked.remove(n);
        }
    }

    public boolean isBlocked(String phone) {
        String n = normalize(phone);
        return n != null && blocked.contains(n);
    }

    private static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String m = phone.trim();
        if (m.startsWith("+86")) {
            m = m.substring(3);
        } else if (m.startsWith("86") && m.length() > 11) {
            m = m.substring(2);
        }
        return m;
    }
}
