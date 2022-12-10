package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.tags.managed.WriteHandler;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.VariableAction;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TagWriteHandler implements WriteHandler, VariableStore {
    private static final String PARENT_PATH = "parentPath";
    private static final String TAG_PATH = "tagPath";
    private static final String VALUE = "value";
    private Logger logger = LoggerFactory.getLogger("API.Tag.WriteHandler");
    private String parentPath;
    private Tag tag;
    private Map<String, Object> localVariables;

    public TagWriteHandler(String parentPath, Tag tag) {
        this.parentPath = parentPath;
        this.tag = tag;
        this.localVariables = new ConcurrentHashMap<>();
    }

    @Override
    public QualityCode write(TagPath tagPath, Object o) {
        try {
            tag.getHandler().getAction().getFunction().getApi().getTagManager().tagUpdate(tagPath.toStringFull(), o);

            localVariables.put(VALUE, o);
            localVariables.put(TAG_PATH, tagPath.toStringFull());

            Handler handler = tag.getHandler();
            for (VariableAction variable : handler.getVariables()) {
                Object value = variable.getValue(this);
                localVariables.put(variable.getName(), value);
            }

            logger.debug("Handling tag write handler [tag=" + tagPath.toStringFull() + ", value=" + (o == null ? "null" : o.toString()) + ", variables = " + localVariables.keySet().stream()
                    .map(key -> key + "=" + localVariables.get(key).toString())
                    .collect(Collectors.joining(", ", "{", "}")) + ", function=" + handler.getFunction() + ", reset=" + handler.isReset() + "]");

            if (handler.hasFunction()) {
                handler.getAction().getFunction().getApi().getFunctions().getFunction(handler.getFunction()).executeAsync(this);
            }

            if (handler.isReset()) {
                handler.getAction().getFunction().getApi().getTagManager().tagUpdate(tagPath.toStringFull(), false);
            }

            return QualityCode.Good;
        } catch (Throwable ex) {
            logger.error("Error in write handler for '" + tagPath.toStringFull() + "'", ex);
            return QualityCode.Error;
        }
    }

    @Override
    public synchronized String getStoreName() {
        return "handler";
    }

    @Override
    public synchronized Object getVariable(String name) throws APIException {
        if (name.equals(PARENT_PATH)) {
            return parentPath;
        }

        if (localVariables.containsKey(name)) {
            return localVariables.get(name);
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) {
        // no-op
    }
}
