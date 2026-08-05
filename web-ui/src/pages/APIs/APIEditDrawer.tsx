import React from 'react';
import { Drawer, DrawerTemplate, DrawerTemplateColorTheme, DrawerTemplateSize, Form } from '@inductiveautomation/ignition-web-ui';
import { UseFormReturn } from 'react-hook-form';
import { getAPIClientPageStyles } from '../_APIClient.styles';
import APIForm from './APIForm';

export interface APIEditDrawerProps {
  open: boolean;
  onClose(): void;
  onComplete?(): void;
  apiName: string | undefined;
  context: UseFormReturn;
}

const APIEditDrawer = ({ open, onClose, onComplete, apiName, context }: APIEditDrawerProps) => {
  const {
    classes: { acForm, acFormRoot },
  } = getAPIClientPageStyles();

  const {
    formState: { isValid },
  } = context;

  return (
    <Drawer open={open} anchor={'right'} id={`drawer-${apiName}`}>
      <DrawerTemplate
        path={[`Edit ${apiName}`]}
        size={DrawerTemplateSize.SMALL}
        onClose={onClose}
        onCancel={onClose}
        onComplete={onComplete}
        primaryActionText={'Save Changes'}
        primaryDisabled={!isValid}
        secondaryActionText={'Cancel'}
        theme={DrawerTemplateColorTheme.GREY}
      >
        <div className={acForm}>
          <Form context={context} id="api-edit" className={acFormRoot}>
            <APIForm isEdit={true} />
          </Form>
        </div>
      </DrawerTemplate>
    </Drawer>
  );
};

export default APIEditDrawer;
