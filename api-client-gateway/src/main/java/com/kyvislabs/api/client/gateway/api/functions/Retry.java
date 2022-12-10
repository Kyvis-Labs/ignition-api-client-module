package com.kyvislabs.api.client.gateway.api.functions;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Retry {
    private int duration;
    private TimeUnit unit;
    private ScheduledFuture scheduledFuture;
    private int max;
    private int executionCount;

    public Retry(int duration, TimeUnit unit, int max) {
        this.duration = duration;
        this.unit = unit;
        this.max = max;
        this.executionCount = 0;
    }

    public static Retry parseRetry(Function function, Map functionMap) {
        if (functionMap.containsKey("retry")) {
            Map retryMap = (Map) functionMap.get("retry");
            int duration = (int) retryMap.getOrDefault("duration", 5);
            TimeUnit unit = TimeUnit.valueOf(retryMap.getOrDefault("unit", "minutes").toString().toUpperCase());
            int max = (int) retryMap.getOrDefault("max", 5);
            return new Retry(duration, unit, max);
        }
        return null;
    }

    public synchronized int getDuration() {
        return duration;
    }

    public synchronized TimeUnit getUnit() {
        return unit;
    }

    public synchronized int getMax() {
        return max;
    }

    public synchronized int getExecutionCount() {
        return executionCount;
    }

    public synchronized void clearExecutionCount() {
        this.executionCount = 0;
    }

    public synchronized void increaseExecutionCount() {
        this.executionCount += 1;
    }

    public boolean canExecute() {
        return getExecutionCount() < getMax();
    }

    public Date getNextDate() {
        return new Date(new Date().getTime() + getUnit().toMillis(getDuration()));
    }

    public synchronized void setScheduledFuture(ScheduledFuture scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }

    @Override
    public String toString() {
        return String.format("%d %s", getDuration(), getUnit().toString().toLowerCase());
    }
}
