package com.example.veglens.ai;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AiUsageLimiter {

    private static final int MAX_REQUESTS_PER_DAY = 50; // adjust safely
    private static final int MAX_REQUESTS_PER_MINUTE = 20; // anti-spam protection

    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicInteger minuteCount = new AtomicInteger(0);

    private LocalDate currentDay = LocalDate.now();
    private long currentMinute = System.currentTimeMillis() / 60000;

    public synchronized void checkLimit() {

        // Reset daily counter
        if (!LocalDate.now().equals(currentDay)) {
            currentDay = LocalDate.now();
            dailyCount.set(0);
        }

        // Reset minute counter
        long nowMinute = System.currentTimeMillis() / 60000;
        if (nowMinute != currentMinute) {
            currentMinute = nowMinute;
            minuteCount.set(0);
        }

        if (dailyCount.incrementAndGet() > MAX_REQUESTS_PER_DAY) {
            throw new RuntimeException("Daily AI usage limit reached");
        }

        if (minuteCount.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            throw new RuntimeException("Too many AI requests per minute");
        }
    }
}
