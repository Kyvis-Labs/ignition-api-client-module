import React, { useEffect, useState } from 'react';
import { useForm, useFieldArray, FieldValues } from 'react-hook-form';
import { useSelector } from 'react-redux';
import {
  Drawer,
  DrawerTemplate,
  DrawerTemplateColorTheme,
  DrawerTemplateSize,
  Form,
  Card,
  FormControlInput,
  Checkbox,
  TextInput,
  Loading,
  useToastNotifications,
} from '@inductiveautomation/ignition-web-ui';
import { VariableInfo } from './APIForm.types';
import { getAPIClientPageStyles } from '../_APIClient.styles';

export interface APIVariablesDrawerProps {
  open: boolean;
  onClose(): void;
  apiName: string | undefined;
}

interface EditableVariableField {
  key: string;
  sensitive: boolean;
  change: boolean;
  value: string;
}

const APIVariablesDrawer = ({ open, onClose, apiName }: APIVariablesDrawerProps) => {
  const { notifySuccess, notifyError } = useToastNotifications();
  const [loading, setLoading] = useState<boolean>(true);
  const [readOnly, setReadOnly] = useState<VariableInfo[]>([]);
  const csrfToken = useSelector((state: any) => state?.userSession?.csrfToken);

  const {
    classes: { acForm, acFormRoot, acDrawerCard, acStack },
  } = getAPIClientPageStyles();

  const context = useForm({
    mode: 'onBlur',
    defaultValues: { variables: [] as EditableVariableField[] } as FieldValues,
  });

  const { fields } = useFieldArray({ control: context.control, name: 'variables' });

  useEffect(() => {
    if (!open || !apiName) {
      return;
    }

    setLoading(true);
    fetch(`/data/api-client/api/v1/variables/${encodeURIComponent(apiName)}`)
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load variables');
        return r.json();
      })
      .then((data: { editable: VariableInfo[]; readOnly: VariableInfo[] }) => {
        context.reset({
          variables: data.editable.map((v) => ({
            key: v.key,
            sensitive: v.sensitive,
            change: false,
            value: v.sensitive ? '' : v.value ?? '',
          })),
        });
        setReadOnly(data.readOnly);
        setLoading(false);
      })
      .catch(() => {
        notifyError('Failed to load variables', true);
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, apiName]);

  const save = async () => {
    if (!apiName) return;
    const values = context.getValues();
    const updates = (values.variables as EditableVariableField[])
      .filter((v) => !v.sensitive || v.change)
      .map((v) => ({ key: v.key, value: v.value }));

    try {
      const response = await fetch(`/data/api-client/api/v1/variables/${encodeURIComponent(apiName)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify(updates),
      });
      if (response.ok) {
        notifySuccess('Variables saved', true);
        onClose();
      } else {
        notifyError('Failed to save variables', true);
      }
    } catch (e) {
      notifyError('Failed to save variables. View logs for more details.', true);
    }
  };

  return (
    <Drawer open={open} anchor={'right'} id={`variables-drawer-${apiName}`}>
      <DrawerTemplate
        path={[`Variables: ${apiName}`]}
        size={DrawerTemplateSize.SMALL}
        onClose={onClose}
        onCancel={onClose}
        onComplete={save}
        primaryActionText={'Save Changes'}
        secondaryActionText={'Cancel'}
        theme={DrawerTemplateColorTheme.GREY}
      >
        {loading ? (
          <Loading isLoading={true} />
        ) : (
          <div className={acForm}>
            <Form context={context} id="api-variables" className={acFormRoot}>
              <Card title={'VARIABLES'} className={acDrawerCard}>
                {fields.length === 0 && <p>No editable variables for this API.</p>}
                {fields.map((field, index) => {
                  const variable = field as unknown as EditableVariableField;
                  return variable.sensitive ? (
                    <React.Fragment key={field.id}>
                      <FormControlInput
                        input={<Checkbox label={'Check this box to change the existing value'} />}
                        name={`variables.${index}.change`}
                        id={`variables-${index}-change`}
                        label={`Change ${variable.key}`}
                      />
                      <FormControlInput
                        disabled={!context.watch(`variables.${index}.change`)}
                        indent={true}
                        input={<TextInput type={'password'} />}
                        name={`variables.${index}.value`}
                        id={`variables-${index}-value`}
                        label={variable.key}
                      />
                    </React.Fragment>
                  ) : (
                    <FormControlInput
                      key={field.id}
                      input={<TextInput />}
                      name={`variables.${index}.value`}
                      id={`variables-${index}-value`}
                      label={variable.key}
                    />
                  );
                })}
              </Card>

              {readOnly.length > 0 && (
                <Card title={'INTERNAL'} className={acDrawerCard}>
                  <div className={acStack}>
                    {readOnly.map((v) => (
                      <p key={v.key} style={{ margin: 0 }}>
                        <strong>{v.key}</strong>: {v.hasValue ? 'Set' : 'Not set'}
                      </p>
                    ))}
                  </div>
                </Card>
              )}
            </Form>
          </div>
        )}
      </DrawerTemplate>
    </Drawer>
  );
};

export default APIVariablesDrawer;
