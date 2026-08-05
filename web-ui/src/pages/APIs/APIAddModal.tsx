import React from 'react';
import { Form, Button, ButtonColorClasses } from '@inductiveautomation/ignition-web-ui';
import { Close } from '@inductiveautomation/ignition-icons';
import { UseFormReturn } from 'react-hook-form';
import { getAPIClientPageStyles } from '../_APIClient.styles';
import APIForm from './APIForm';

export interface APIAddModalProps {
  close(): void;
  create(): void;
  context: UseFormReturn;
}

const APIAddModal = ({ close, create, context }: APIAddModalProps) => {
  const {
    classes: { acModalTitle, acModal, acFormRoot, acModalFooter, acForm },
  } = getAPIClientPageStyles();

  const {
    formState: { isValid },
  } = context;

  return (
    <>
      <div className={acModalTitle}>
        <p className={'title'}>{'Add API'}</p>
        <button>
          <Close onClick={close} data-icon={'close'} title={'close'} data-label="X" />
        </button>
      </div>
      <div className={acModal}>
        <div className={acForm}>
          <Form onSubmit={create} context={context} id="api-add" className={acFormRoot}>
            <APIForm isEdit={false} />
          </Form>
        </div>
        <div className={acModalFooter}>
          <Button colorClass={ButtonColorClasses.SECONDARY} onClick={close}>
            Cancel
          </Button>
          <Button colorClass={ButtonColorClasses.PRIMARY} onClick={create} disabled={!isValid}>
            {'Add API'}
          </Button>
        </div>
      </div>
    </>
  );
};

export default APIAddModal;
