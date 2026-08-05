import React from 'react';
import { Card, FormControlInput, Checkbox, TextInput, TextArea } from '@inductiveautomation/ignition-web-ui';
import { getAPIClientPageStyles } from '../_APIClient.styles';

const APIForm = ({ isEdit }: { isEdit: boolean }) => {
  const {
    classes: { acDrawerCard },
  } = getAPIClientPageStyles();

  return (
    <div>
      <Card title={'GENERAL'} className={acDrawerCard} required={true}>
        {!isEdit && <FormControlInput input={<TextInput />} name={'name'} id={'name'} label={'Name *'} />}
        <FormControlInput
          input={<Checkbox label={'Sets whether the API is enabled or disabled within the Gateway'} />}
          name={'config.enabled'}
          id={'enabled'}
          label={'Enabled'}
        />
      </Card>
      <Card title={'CONFIGURATION'} className={acDrawerCard} required={true}>
        <FormControlInput
          input={<TextArea initialHeight={300} />}
          name={'config.configuration'}
          id={'configuration'}
          label={'YAML Configuration *'}
        />
      </Card>
    </div>
  );
};

export default APIForm;
