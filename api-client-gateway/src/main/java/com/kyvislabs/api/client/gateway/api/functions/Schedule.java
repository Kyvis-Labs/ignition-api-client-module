package com.kyvislabs.api.client.gateway.api.functions;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Schedule {
    private int duration;
    private TimeUnit unit;
    private ScheduledFuture scheduledFuture;

    public Schedule(int duration, TimeUnit unit) {
        this.duration = duration;
        this.unit = unit;
    }

    public static Schedule parseSchedule(Function function, Map functionMap) {
        if (functionMap.containsKey("schedule")) {
            Map scheduleMap = (Map) functionMap.get("schedule");
            int duration = (int) scheduleMap.getOrDefault("duration", 5);
            TimeUnit unit = TimeUnit.valueOf(scheduleMap.getOrDefault("unit", "minutes").toString().toUpperCase());
            return new Schedule(duration, unit);
        }
        return null;
    }

    public synchronized int getDuration() {
        return duration;
    }

    public synchronized TimeUnit getUnit() {
        return unit;
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
