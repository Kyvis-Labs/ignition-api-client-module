package com.kyvislabs.api.client.gateway.api.functions;

import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.event.InvalidListenerException;
import com.inductiveautomation.ignition.common.tags.model.event.TagChangeEvent;
import com.inductiveautomation.ignition.common.tags.model.event.TagChangeListener;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.actions.condition.Case;
import it.sauronsoftware.cron4j.Scheduler;
import org.slf4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Schedule implements TagChangeListener {
    private Logger logger;
    private ScheduleType type;
    private int duration;
    private TimeUnit unit;
    private String cron;
    private String tagPath;
    private Case.ConditionOperator operator;
    private Object value;

    private FunctionExecutor executor;
    private ScheduledFuture scheduledFuture;
    private Scheduler cronScheduler;

    public Schedule() {
        this.type = ScheduleType.MANUAL;
    }

    public Schedule(int duration, TimeUnit unit) {
        this.type = ScheduleType.TIMER;
        this.duration = duration;
        this.unit = unit;
    }

    public Schedule(String cron) {
        this.type = ScheduleType.CRON;
        this.cron = cron;
    }

    public Schedule(String tagPath, Case.ConditionOperator operator, Object value) {
        this.type = ScheduleType.TAG;
        this.tagPath = tagPath;
        this.operator = operator;
        this.value = value;
    }

    public static Schedule parseSchedule(Function function, Map functionMap) throws APIException {
        if (functionMap.containsKey("schedule")) {
            Map scheduleMap = (Map) functionMap.get("schedule");
            ScheduleType type = ScheduleType.valueOf(scheduleMap.getOrDefault("type", "manual").toString().toUpperCase());
            if (type.equals(ScheduleType.TIMER)) {
                int duration = (int) scheduleMap.getOrDefault("duration", 5);
                TimeUnit unit = TimeUnit.valueOf(scheduleMap.getOrDefault("unit", "minutes").toString().toUpperCase());
                return new Schedule(duration, unit);
            } else if (type.equals(ScheduleType.CRON)) {
                String cron = (String) scheduleMap.getOrDefault("cron", "0 * * * *");
                return new Schedule(cron);
            } else if (type.equals(ScheduleType.TAG)) {
                String tagPath = (String) scheduleMap.getOrDefault("tagPath", null);
                Case.ConditionOperator operator = Case.ConditionOperator.valueOf(scheduleMap.getOrDefault("operator", "EQ").toString().toUpperCase());
                Object value = scheduleMap.getOrDefault("value", true);

                if (tagPath == null) {
                    throw new APIException("Schedule missing tag path: " + scheduleMap.toString());
                } else if (operator == null) {
                    throw new APIException("Schedule missing tag operator or invalid: " + scheduleMap.toString());
                }

                return new Schedule(tagPath, operator, value);
            }
            return new Schedule();
        }
        return null;
    }

    public void schedule(Logger logger, Function function) {
        this.logger = logger;

        executor = new FunctionExecutor(logger, function, null);
        if (getType().equals(ScheduleType.TIMER)) {
            logger.debug("Scheduling with fixed delay at " + String.format("%d %s", getDuration(), getUnit().toString().toLowerCase()));
            if (getDuration() == 0) {
                function.getApi().getGatewayContext().getScheduledExecutorService().execute(executor);
            } else {
                setScheduledFuture(function.getApi().getGatewayContext().getScheduledExecutorService().scheduleWithFixedDelay(executor, 0, getDuration(), getUnit()));
            }
        } else if (getType().equals(ScheduleType.CRON)) {
            cronScheduler = new Scheduler();
            cronScheduler.schedule(getCron(), executor);
            cronScheduler.start();
        } else if (getType().equals(ScheduleType.TAG)) {
            TagPath tagPath = TagPathParser.parseSafe(getTagPath());
            function.getApi().getGatewayContext().getTagManager().subscribeAsync(tagPath, this);
        }
    }

    public synchronized int getDuration() {
        return duration;
    }

    public synchronized TimeUnit getUnit() {
        return unit;
    }

    public synchronized ScheduleType getType() {
        return type;
    }

    public synchronized String getCron() {
        return cron;
    }

    public synchronized String getTagPath() {
        return tagPath;
    }

    public synchronized Case.ConditionOperator getOperator() {
        return operator;
    }

    public synchronized Object getValue() {
        return value;
    }

    public synchronized Date getNextDate() {
        if (getType().equals(ScheduleType.TIMER)) {
            return new Date(new Date().getTime() + getUnit().toMillis(getDuration()));
        }

        return null;
    }

    public synchronized void setScheduledFuture(ScheduledFuture scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

    public synchronized Scheduler getCronScheduler() {
        return cronScheduler;
    }

    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }

    public void shutdown() {
        if (getScheduledFuture() != null) {
            getScheduledFuture().cancel(true);
        }

        if (getCronScheduler() != null) {
            getCronScheduler().stop();
        }
    }

    @Override
    public void tagChanged(TagChangeEvent tagChangeEvent) throws InvalidListenerException {
        try {
            Object tagValue = tagChangeEvent.getValue().getValue();

            boolean proceed = false;

            if (getOperator().equals(Case.ConditionOperator.EQ)) {
                proceed = TypeUtilities.equals(getValue(), tagValue);
            } else if (getOperator().equals(Case.ConditionOperator.NEQ)) {
                proceed = !TypeUtilities.equals(getValue(), tagValue);
            } else if (getOperator().equals(Case.ConditionOperator.LT)) {
                proceed = TypeUtilities.toDouble(getValue()) < TypeUtilities.toDouble(tagValue);
            } else if (getOperator().equals(Case.ConditionOperator.LTE)) {
                proceed = TypeUtilities.toDouble(getValue()) <= TypeUtilities.toDouble(tagValue);
            } else if (getOperator().equals(Case.ConditionOperator.GT)) {
                proceed = TypeUtilities.toDouble(getValue()) > TypeUtilities.toDouble(tagValue);
            } else if (getOperator().equals(Case.ConditionOperator.GTE)) {
                proceed = TypeUtilities.toDouble(getValue()) >= TypeUtilities.toDouble(tagValue);
            }

            if (proceed) {
                executor.run();
            }
        } catch (Throwable t) {
            logger.error("Error executing tag change listener: " + t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        if (getType().equals(ScheduleType.TIMER)) {
            return String.format("Timer: %d %s", getDuration(), getUnit().toString().toLowerCase());
        } else if (getType().equals(ScheduleType.CRON)) {
            return String.format("Cron: %s", getCron());
        } else if (getType().equals(ScheduleType.TAG)) {
            return String.format("Tag: %s%s%s", getTagPath(), getOperator().getDisplay(), getValue() == null ? "null" : getValue().toString());
        }

        return "Unknown";
    }

    public enum ScheduleType {
        MANUAL, TIMER, CRON, TAG;
    }
}
