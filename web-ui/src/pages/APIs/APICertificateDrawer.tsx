import React, { useEffect, useState } from 'react';
import { useForm, FieldValues } from 'react-hook-form';
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
  TextArea,
  Loading,
  useToastNotifications,
} from '@inductiveautomation/ignition-web-ui';
import { CertificateInfo } from './APIForm.types';
import { getAPIClientPageStyles } from '../_APIClient.styles';

export interface APICertificateDrawerProps {
  open: boolean;
  onClose(): void;
  apiName: string | undefined;
}

const APICertificateDrawer = ({ open, onClose, apiName }: APICertificateDrawerProps) => {
  const { notifySuccess, notifyError } = useToastNotifications();
  const [loading, setLoading] = useState<boolean>(true);
  const [hasPrivateKey, setHasPrivateKey] = useState<boolean>(false);
  const csrfToken = useSelector((state: any) => state?.userSession?.csrfToken);

  const {
    classes: { acForm, acFormRoot, acDrawerCard },
  } = getAPIClientPageStyles();

  const context = useForm({
    mode: 'onBlur',
    defaultValues: { certificate: '', replacePrivateKey: false, privateKey: '' } as FieldValues,
  });

  useEffect(() => {
    if (!open || !apiName) {
      return;
    }

    setLoading(true);
    fetch(`/data/api-client/api/v1/certificate/${encodeURIComponent(apiName)}`)
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load certificate');
        return r.json();
      })
      .then((data: CertificateInfo) => {
        context.reset({ certificate: data.certificate ?? '', replacePrivateKey: !data.hasPrivateKey, privateKey: '' });
        setHasPrivateKey(data.hasPrivateKey);
        setLoading(false);
      })
      .catch(() => {
        notifyError('Failed to load certificate', true);
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, apiName]);

  const save = async () => {
    if (!apiName) return;
    const values = context.getValues();

    try {
      const response = await fetch(`/data/api-client/api/v1/certificate/${encodeURIComponent(apiName)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify({
          certificate: values.certificate,
          privateKey: values.replacePrivateKey ? values.privateKey : null,
        }),
      });
      if (response.ok) {
        notifySuccess('Certificate saved', true);
        onClose();
      } else {
        notifyError('Failed to save certificate', true);
      }
    } catch (e) {
      notifyError('Failed to save certificate. View logs for more details.', true);
    }
  };

  const replacePrivateKey = context.watch('replacePrivateKey');

  return (
    <Drawer open={open} anchor={'right'} id={`certificate-drawer-${apiName}`}>
      <DrawerTemplate
        path={[`Certificate: ${apiName}`]}
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
            <Form context={context} id="api-certificate" className={acFormRoot}>
              <Card title={'CLIENT CERTIFICATE'} className={acDrawerCard}>
                <FormControlInput input={<TextArea />} name={'certificate'} id={'certificate'} label={'PEM Certificate'} />
                {hasPrivateKey && (
                  <FormControlInput
                    label={'Replace Private Key'}
                    input={<Checkbox label={'Check this box to replace the existing private key'} />}
                    name={'replacePrivateKey'}
                    id={'replace-private-key'}
                  />
                )}
                <FormControlInput
                  disabled={hasPrivateKey && !replacePrivateKey}
                  indent={hasPrivateKey}
                  input={<TextArea />}
                  name={'privateKey'}
                  id={'private-key'}
                  label={hasPrivateKey ? 'New PEM Private Key' : 'PEM Private Key *'}
                />
              </Card>
            </Form>
          </div>
        )}
      </DrawerTemplate>
    </Drawer>
  );
};

export default APICertificateDrawer;
