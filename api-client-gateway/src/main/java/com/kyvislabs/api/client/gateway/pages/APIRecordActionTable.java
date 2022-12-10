package com.kyvislabs.api.client.gateway.pages;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.i18n.Localized;
import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.util.RecordInstanceForeignKey;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.components.ConfirmedTaskVetoException;
import com.inductiveautomation.ignition.gateway.web.components.InvisibleContainer;
import com.inductiveautomation.ignition.gateway.web.components.NbspLabel;
import com.inductiveautomation.ignition.gateway.web.components.actions.DeleteRecordAction;
import com.inductiveautomation.ignition.gateway.web.components.actions.EditRecordAction;
import com.inductiveautomation.ignition.gateway.web.components.actions.InlineActions;
import com.inductiveautomation.ignition.gateway.web.components.actions.NewRecordAction;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;
import com.inductiveautomation.ignition.gateway.web.models.RecordListModel;
import com.inductiveautomation.ignition.gateway.web.models.RecordModel;
import com.inductiveautomation.ignition.gateway.web.models.RecordTypeNameModel;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.inductiveautomation.ignition.gateway.web.util.CSSAttributeAppender;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.convert.IConverter;
import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.time.Duration;
import simpleorm.dataset.*;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * <p>
 * A RecordActionTable is commonly used in the Configure section of the gateway. It displays a table of each record for
 * a given record type. By default, it includes <i>edit</i> and <i>delete</i> links next to each record, and an
 * <i>add</i> link at the bottom. By default, the columns are created by looking at the record's RecordMeta and finding
 * all fields marked with {@link SFieldFlags#SDESCRIPTIVE}
 * </p>
 * <p>
 * This class is very customizable by subclassing it and overriding functions. Common points of overriding are:
 * <ul>
 * <li>Override {@link #addRecordInstanceActions(RepeatingView, PersistentRecord)} to add new action links next to each
 * record.</li>
 * <li>Override {@link #addRecordActions(RepeatingView)} To add new links below the table</li>
 * <li>Override {@link #getCalculatedFields()} to add new columns to the table</li>
 * </ul>
 * </p>
 *
 * @author carl.gould
 */
public abstract class APIRecordActionTable<R extends PersistentRecord> extends ConfigPanel {

    protected IConfigPage configPage;
    private static final Logger log = Logger.getLogger(APIRecordActionTable.class);

    /**
     * A list of components that should get refreshed via ajax.
     */
    private List<Component> ajaxUpdatedComponents = new ArrayList<>();
    private boolean timerAdded = false;

    private String tableId;

    /**
     * Creates a RecordActionTable, using a default RecordListModel
     */
    public APIRecordActionTable(IConfigPage configPage) {
        this.configPage = configPage;

        initComponents(createRecordModel(getRecordMeta()));
    }

    public APIRecordActionTable(IConfigPage configPage, String id) {
        super(id, "?");

        this.configPage = configPage;

        initComponents(createRecordModel(getRecordMeta()));
    }

    protected IModel<List<R>> createRecordModel(RecordMeta<R> meta) {
        return new RecordListModel<>(getRecordMeta());
    }

    /**
     * Creates a RecordActionTable using a custom model to list the records
     */
    public APIRecordActionTable(IConfigPage configPage, IModel<? extends List<? extends PersistentRecord>> model) {
        this.configPage = configPage;

        initComponents(model);
    }

    protected abstract RecordMeta<R> getRecordMeta();

    /**
     * Returns the panel that will be used by actions as the parent. By default is "this", but can be overridden for
     * cases where multiple record action tables are used inside of another panel.
     */
    protected ConfigPanel getActionParentPanel() {
        return this;
    }

    /**
     * Looks through the SFieldMeta's defined by the record's RecordMeta, finding all descriptive fields.
     */
    protected List<SFieldMeta> getDescriptiveFields() {
        List<SFieldMeta> fields = new ArrayList<>();
        for (SFieldMeta field : getRecordMeta().getFieldMetas()) {
            if (field.isDescriptive()) {
                fields.add(field);
            }
        }
        return fields;
    }

    protected void initComponents(IModel<? extends List<? extends PersistentRecord>> model) {
        List<SFieldMeta> fields = getDescriptiveFields();
        List<ICalculatedField<R>> calcFields = getCalculatedFields();
        int calcFieldsSize = calcFields == null ? 0 : calcFields.size();

        int totalFields = fields.size() + calcFieldsSize;

        WebMarkupContainer table = new WebMarkupContainer("table");
        table.setOutputMarkupId(true);
        this.tableId = table.getMarkupId();

        RepeatingView headers = new RepeatingView("field-header");

        // Add headers
        for (int i = 0; i < fields.size(); i++) {
            SFieldMeta field = fields.get(i);

            FormMeta fm = (FormMeta) field.getUserProperty(PersistentRecord.FORM_META_KEY);
            String key = fm == null ? field.getFieldName() : fm.getFieldNameKey();
            Label lbl = new Label(headers.newChildId(), new LenientResourceModel(key, field.getFieldName()));
            if (i == totalFields - 1) {
                lbl.add(new CSSAttributeAppender("last"));
            }

            headers.add(lbl);
        }
        for (int i = 0; i < calcFieldsSize; i++) {
            ICalculatedField<R> field = calcFields.get(i);

            String key = field.getHeaderKey();
            Label lbl = new Label(headers.newChildId(), new LenientResourceModel(key));
            if (i + fields.size() == totalFields - 1) {
                lbl.add(new CSSAttributeAppender("last"));
            }

            headers.add(lbl);
        }

        // Make sure that there are some columns at least!
        if (totalFields == 0) {
            headers.add(new Label(headers.newChildId(), new LenientResourceModel(getNoDescriptiveFieldsKey())));
        }

        // Add rows
        ListView<PersistentRecord> rows = new ListView<>("rows", model) {

            @SuppressWarnings("unchecked")
            @Override
            protected void populateItem(ListItem<PersistentRecord> item) {
                List<SFieldMeta> fields = getDescriptiveFields();
                List<ICalculatedField<R>> calcFields = getCalculatedFields();
                int calcFieldsSize = calcFields == null ? 0 : calcFields.size();

                int totalFields = fields.size() + calcFieldsSize;

                R record = (R) item.getModelObject();
                RepeatingView values = new RepeatingView("field");

                for (int i = 0; i < totalFields; i++) {
                    Component lbl;
                    if (i < fields.size()) {
                        // Standard field - found from the Meta's descriptive field set
                        SFieldMeta field = fields.get(i);
                        FormMeta formMeta = field.getUserProperty(PersistentRecord.FORM_META_KEY);
                        if (formMeta == null) {
                            continue;
                        }
                        lbl = new NbspLabel(values.newChildId(), new Model(getFieldValue(record, formMeta)));
                        lbl.setEscapeModelStrings(formMeta.isEscapeHTMLOnDisplay());
                    } else {
                        // Calculated field
                        ICalculatedField<R> field = calcFields.get(i - fields.size());

                        lbl = new NbspLabel(values.newChildId(), getCalculatedFieldModel(field, record))
                                .setEscapeModelStrings(false)
                                .setOutputMarkupId(true);

                        ajaxUpdatedComponents.add(lbl);
                    }
                    if (i == 0) {
                        lbl.add(new CSSAttributeAppender("first"));
                    }
                    if (item.getIndex() % 2 == 1) {
                        lbl.add(new CSSAttributeAppender("odd"));
                    }
                    if (i == totalFields - 1) {
                        lbl.add(new CSSAttributeAppender("last"));
                    }
                    values.add(lbl);
                }
                item.add(values);


                InlineActions actions = new InlineActions("actions");
                addRecordInstanceActions(actions.getView(), record);
                actions.done();
                item.add(actions);
            }
        };

        WebMarkupContainer noRows = new WebMarkupContainer("norows", model) {
            @Override
            public boolean isVisible() {
                List<?> list = (List<?>) getDefaultModelObject();
                return list == null || list.isEmpty();
            }
        };
        Label noRowsLabel = new Label("norows-cell", new RecordTypeNameModel(getRecordMeta(), getNoRowsKey(), true));
        noRowsLabel.add(new AttributeModifier("colspan", Integer.toString(totalFields)));
        noRows.add(noRowsLabel);

        RepeatingView recordActionsContainer = new RepeatingView("record-action");
        addRecordActions(recordActionsContainer);

        table.add(headers);
        table.add(rows);
        table.add(noRows);

        add(table);
        add(recordActionsContainer);
        add(createFooter("footer"));

    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();

        // Add the ajax timer at this point to update calculated fields
        if (!ajaxUpdatedComponents.isEmpty() && !timerAdded) {
            timerAdded = true;
            add(new AbstractAjaxTimerBehavior(Duration.ONE_SECOND) {

                @Override
                protected void onTimer(AjaxRequestTarget target) {
                    for (Component c : ajaxUpdatedComponents) {
                        Page p = c.findParent(Page.class);
                        // Compensates for behavior in Wicket's XmlAjaxResponse#writeComponent(). It checks for a
                        // Page instance in the parent hierarchy. In Wicket prior to 6.30, if the page instance could
                        // not be found, it would log a debug message and continue. In Wicket 6.30, the log message was
                        // changed to a warning message, which floods the system logs.
                        if (p != null) {
                            target.add(c);
                        }
                    }
                }
            });
        }
    }


    /**
     * Returns the maximum number of instance actions per row. If there are more actions than this, then a new column
     * will be started. Default is 3
     */
    protected int getInstanceActionMaxRowCount() {
        return 3;
    }

    @SuppressWarnings({"RedundantCast", "unchecked"}) // type erasure prevents passing correct type to convertor
    protected String getCalculatedFieldDisplayValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value.getClass() == String.class) {
            return value.toString();
            //return Strings.escapeMarkup((String) value).toString();
        } else if (Collection.class.isAssignableFrom(value.getClass())) {
            Collection collection = (Collection) value;
            StringBuilder sb = new StringBuilder();
            sb.append("<ul class=\"bulletlist\">");
            for (Object o : collection) {
                sb.append("<li>");
                sb.append(Strings.escapeMarkup(getCalculatedFieldDisplayValue(o)));
                sb.append("</li>");
            }
            sb.append("</ul>");
            return sb.toString();
        } else {
            IConverter<Object> converter = (IConverter<Object>) getConverter(value.getClass());
            return converter.convertToString(value, getLocale());
        }

    }

    protected LoadableDetachableModel<Object> getCalculatedFieldModel(ICalculatedField<R> field, R record) {
        return new CalculatedFieldModel(field, record);
    }

    private class CalculatedFieldModel extends LoadableDetachableModel<Object> {

        ICalculatedField<R> field;
        RecordModel<R> model;

        public CalculatedFieldModel(ICalculatedField<R> field, R record) {
            this.field = field;
            this.model = new RecordModel<>(record);
        }

        @Override
        protected Object load() {
            R rec = model.getObject();
            String ret = null;
            if (rec != null) {
                ret = getCalculatedFieldDisplayValue(field.getFieldvalue(rec));
            }
            return ret;
        }

        @Override
        protected void onDetach() {
            model.detach();
        }
    }

    /**
     * Override this to add calculated fields (columns) to the table. Default implementation returns null.
     */
    protected List<ICalculatedField<R>> getCalculatedFields() {
        return null;
    }

    /**
     * Override this to return a Wicket component to display beneath the table.
     */
    protected Component createFooter(String id) {
        return new InvisibleContainer(id);
    }

    /**
     * Provides an opportunity to add any actions that deal with specific record instances. Default actions are added
     * for edit and delete.
     */
    protected void addRecordInstanceActions(RepeatingView view, R record) {
        WebMarkupContainer edit = newEditRecordAction(view.newChildId(), record);
        if (edit != null) {
            view.add(edit);
        }
        WebMarkupContainer delete = newDeleteRecordAction(view.newChildId(), record);
        if (delete != null) {
            view.add(delete);
        }
    }

    protected WebMarkupContainer newEditRecordAction(String id, R record) {
        return new EditRecordAction<>(id, configPage, getActionParentPanel(), record);
    }

    protected WebMarkupContainer newDeleteRecordAction(String id, R record) {
        return new RecordActionTableDelete(id, configPage, getActionParentPanel(), record);
    }

    /**
     * Subclass of {@link DeleteRecordAction} that delegates canDelete and onDelete to the RecordActionTable
     */
    public class RecordActionTableDelete extends DeleteRecordAction<R> {

        public RecordActionTableDelete(String id, IConfigPage configPage, ConfigPanel parentPanel, R record) {
            super(id, configPage, parentPanel, record);
        }

        @Override
        public void canDelete(R record) throws ConfirmedTaskVetoException {
            APIRecordActionTable.this.canDelete(record);
        }

        @Override
        public void onDelete(R record) {
            APIRecordActionTable.this.onDelete(record);
        }

        @Override
        protected void doDeleteRelatedRecords(PersistenceSession session, R record) {
            APIRecordActionTable.this.doDeleteRelatedRecords(session, record);
        }

    }

    /**
     * Provides a hook to add record actions, like "Add new.." to the bottom of the panel. Make sure that the wicket ids
     * of the children are obtained via {@code view.newChildId()}. Default implementation adds a basic "New Record"
     * action (which itself can be customized by overriding {@link #newRecordAction(String)}
     */
    protected void addRecordActions(RepeatingView view) {
        view.add(newRecordAction(view.newChildId()));
    }

    /**
     * Creates a link that will navigate to creating a new record. Uses a basic {@link NewRecordAction}
     */
    protected WebMarkupContainer newRecordAction(String id) {
        return new NewRecordAction<>(id, configPage, getActionParentPanel(), getRecordMeta()) {
            @Override
            protected void setupNewRecord(R record) {
                APIRecordActionTable.this.setupNewRecord(record);
            }
        };
    }

    /**
     * Provides subclasses a chance to perform initialization on a new instance of of the record.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    protected void setupNewRecord(R record) {
    }

    /**
     * Verifies if a record can be deleted. Throw an exception if the record should not be deleted. The default
     * implementation will check defined foreign keys and will throw an error if any records refer to the record in
     * question.
     */
    protected void canDelete(R record) throws ConfirmedTaskVetoException {
        GatewayContext context = (((IgnitionWebApp) Application.get()).getContext());
        //Get all of the records that reference the record being deleted.
        List<RecordInstanceForeignKey> referencing = context.getSchemaUpdater().findReferencingRecords(record);

        Collection<RecordMeta<? extends PersistentRecord>> exempt = getExemptForeignKeys();

        StringBuilder sb = new StringBuilder();
        for (RecordInstanceForeignKey rfk : referencing) {
            SRecordInstance res = rfk.getRecord();
            RecordMeta<?> meta = (RecordMeta<?>) res.getMeta();
            if (!exempt.contains(meta)) {
                addItem(sb, meta.getRecordTypeName(getLocale()), RecordMeta.getRecordNameIfExists(rfk));
            }
        }
        if (sb.length() > 1) {
            String msg = BundleUtil.get().getStringLenient(getLocale(),
                    "RecordActionTable.DeleteRecordAction.CannotDelete",
                    StringUtils.lowerCase(record.getMeta().getRecordTypeName(getLocale())),
                    RecordMeta.getRecordNameIfExists(record));

            throw new ConfirmedTaskVetoException(msg + "\n[" + sb.substring(1) + "]");
        }
    }

    protected Collection<RecordMeta<? extends PersistentRecord>> getExemptForeignKeys() {
        return Collections.emptyList();
    }

    private static void addItem(StringBuilder sb, String itemTypeName, String itemName) {
        sb.append("\n");
        sb.append("'strong'");    // MultiLineFeedbackPanel will convert this to a <strong> tag.
        sb.append(itemTypeName).append(": ");
        sb.append("'/strong'");
        sb.append("\"").append(itemName).append("\"");
    }

    /**
     * Called after a record is successfully deleted
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    protected void onDelete(R record) {
        //no-op
    }

    /**
     * Provides a chance for subclasses to delete any related records <i>before</i> this record is deleted. Will be
     * called with an active SimpleORM session.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    protected void doDeleteRelatedRecords(PersistenceSession session, R record) {
        //no-op
    }

    private static final String defaultNoRowsKey = "RecordActionTable.NoRows";
    private static final String defaultNoDescriptiveFieldsKey = "RecordActionTable.NoDescriptiveFields";

    /**
     * Override this to return a custom resource key for what to display when no records exist
     */
    protected String getNoRowsKey() {
        return defaultNoRowsKey;
    }

    protected String getNoDescriptiveFieldsKey() {
        return defaultNoDescriptiveFieldsKey;
    }

    @SuppressWarnings("unchecked")
    protected String getFieldValue(R record, FormMeta formMeta) {
        SFieldMeta field = formMeta.getField();
        if (field instanceof SFieldReference) {
            SRecordInstance ref;
            if (record.getDataSet().isAttached()) {
                ref = record.findReference((SFieldReference) field, SQueryMode.SREAD_ONLY, SSelectMode.SDESCRIPTIVE);
            } else {
                GatewayContext context = ((IgnitionWebApp) Application.get()).getContext();
                SSessionJdbc session = null;
                try {
                    session = context.getPersistenceInterface().getSession(record.getDataSet());
                    ref = record
                            .findReference((SFieldReference) field, SQueryMode.SREAD_ONLY, SSelectMode.SDESCRIPTIVE);
                } finally {
                    if (session != null) {
                        try {
                            session.commitAndDetachDataSet();
                        } catch (SException ex) {
                            log.error("Error detaching persistent session.", ex);
                        }
                        try {
                            session.close();
                        } catch (SException ex) {
                            log.error("Error closing persistent session.", ex);
                        }
                    }
                }
            }

            if (ref == null) {
                return "";
            } else {
                return RecordMeta.getRecordName(ref);
            }

        } else if (field instanceof SFieldEnum) {
            SFieldEnum enumField = (SFieldEnum) field;
            Enum value = record.getEnum(enumField);
            if (value == null) {
                return "";
            } else {

                String stringVal = value.toString();
                String text = null;

                if (value instanceof Localized) {
                    text = ((Localized) value).toString(getLocale());
                } else {

                    try {
                        String key = value.getClass().getSimpleName() + "." + stringVal + ".Display";
                        text = Application.get().getResourceSettings().getLocalizer().getString(key, this);
                    } catch (MissingResourceException ignored) {
                    }
                }

                if (text != null) {
                    return text;
                } else {
                    return stringVal;
                }
            }
        } else if (field instanceof DateField) {
            Date date = record.getTimestamp(field);
            if (date == null) {
                return "";
            } else {
                return formatDate(field, date);
            }
        } else {
            return record.getString(field);
        }
    }

    private String formatDate(SFieldMeta field, Date date) {
        return SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM, getLocale()).format(date);
    }

    @Override
    protected String getTitleKey() {
        return getRecordMeta().getUserProperty(RecordMeta.RECORD_NOUN_PLURAL_KEY);
    }

    public interface ICalculatedField<R extends PersistentRecord> extends IClusterable {
        String getHeaderKey();

        /**
         * Return an object to display. If the object is a String, it will be displayed directly, otherwise, it will
         * pass through wicket's converter mechanism. If the object is a collection, the contents will be displayed in a
         * bullet list.
         */
        Object getFieldvalue(R record);

    }
}