import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { FieldValues, useForm } from 'react-hook-form';
import { useSelector } from 'react-redux';
import { yupResolver } from '@hookform/resolvers/yup';
import {
  BadgeStatus,
  BlankState,
  DataGrid,
  DataGridActionButtons,
  MenuItem,
  Button,
  ButtonColorClasses,
  PageHeader,
  StatusBadge,
  Modal,
  ModalType,
  useToastNotifications,
} from '@inductiveautomation/ignition-web-ui';
import { AddSmall, EditGw, DeleteGw, Key, Security, NavServices } from '@inductiveautomation/ignition-icons';
import { getAPIClientPageStyles } from '../_APIClient.styles';
import { APIListItem, APIConfig, APIRow, APIUtils, DEFAULT_API_CONFIG } from './APIForm.types';
import useFetch from '../../utils/useFetch';
import APIAddModal from './APIAddModal';
import APIEditDrawer from './APIEditDrawer';
import APIVariablesDrawer from './APIVariablesDrawer';
import APICertificateDrawer from './APICertificateDrawer';
import APIOAuth2Drawer from './APIOAuth2Drawer';

const { APISchema, APIDefaultValues } = APIUtils;

const RESOURCE_PATH = 'com.kyvislabs.api.client/api';
const LIST_URL = `/data/api/v1/resources/list/${RESOURCE_PATH}`;
const FIND_URL = `/data/api/v1/resources/find/${RESOURCE_PATH}`;
const RESOURCE_URL = `/data/api/v1/resources/${RESOURCE_PATH}`;

const statusFromHealthy = (healthy: boolean, message: string): BadgeStatus => {
  const m = message?.toLowerCase() ?? '';
  if (m.includes('disabled') || m.includes('initializing') || m.includes('starting') || m === '') {
    return BadgeStatus.DEFAULT;
  }
  return healthy ? BadgeStatus.SUCCESS : BadgeStatus.ERROR;
};

// Reads whatever the server actually sent back on a failed request instead of showing a generic
// message regardless of cause - e.g. a 409 name-collision response, permission errors, etc.
const getErrorMessage = async (response: Response, fallback: string): Promise<string> => {
  if (response.status === 409) {
    return 'That name is already in use by another API.';
  }
  try {
    const text = await response.text();
    if (!text) return fallback;
    try {
      const json = JSON.parse(text);
      return json.message || json.error || text;
    } catch {
      return text;
    }
  } catch {
    return fallback;
  }
};

// Flattens the raw resource envelope down to only what the table renders. Raw healthcheck/metric
// objects carry a timestamp/duration that changes on every poll even when nothing meaningful
// changed, which would defeat useFetch's dedup check and force a re-render every 2s regardless -
// that constant re-rendering was what made the show-more menu appear to do nothing (its transient
// open/closed state kept getting reset out from under it).
const toRow = (row: any): APIRow => ({
  name: row.name,
  enabled: !!row.config?.enabled,
  statusHealthy: row.healthchecks?.status?.result?.healthy ?? false,
  statusMessage: row.healthchecks?.status?.result?.message ?? 'Disabled',
  functionsRunning: row.metrics?.functionsRunning?.metric?.value ?? 0,
  functionsUnknown: row.metrics?.functionsUnknown?.metric?.value ?? 0,
  functionsFailed: row.metrics?.functionsFailed?.metric?.value ?? 0,
  webhooksRunning: row.metrics?.webhooksRunning?.metric?.value ?? 0,
  webhooksWaiting: row.metrics?.webhooksWaiting?.metric?.value ?? 0,
  webhooksFailed: row.metrics?.webhooksFailed?.metric?.value ?? 0,
});

const APIsPage = () => {
  const { notifySuccess, notifyError } = useToastNotifications();
  const {
    classes: { acTableContainer, acBlankStateContainer, acFontRed, acDeleteButton },
  } = getAPIClientPageStyles();

  const [createModal, setCreateModal] = useState<boolean>(false);
  const [deleteModal, setDeleteModal] = useState<boolean>(false);
  const [editDrawer, setEditDrawer] = useState<boolean>(false);
  const [variablesDrawer, setVariablesDrawer] = useState<boolean>(false);
  const [certificateDrawer, setCertificateDrawer] = useState<boolean>(false);
  const [oauth2Drawer, setOAuth2Drawer] = useState<boolean>(false);
  const [apiResource, setApiResource] = useState<APIListItem>();
  const [selectedApiName, setSelectedApiName] = useState<string>();
  const [queryParams, setQueryParams] = useState<string>('?limit=20&offset=0');
  const [headers] = useState<any>({ Accept: 'application/json', 'Content-Type': 'application/json' });

  const csrfToken = useSelector((state: any) => state?.userSession?.csrfToken);

  const mapper = useCallback(
    (jsonObj: any) => ({
      items: (jsonObj.items || []).map(toRow) as APIRow[],
      metadata: jsonObj.metadata,
    }),
    []
  );

  const defaultFetchValues = useMemo(
    () => ({ items: [] as APIRow[], metadata: { total: 0, matching: 0, limit: 20, offset: 0 } }),
    []
  );

  const {
    data,
    error,
    refresh: tableRefresh,
  } = useFetch(`${LIST_URL}${queryParams}`, headers, mapper, defaultFetchValues, 2000);

  useEffect(() => {
    if (error) {
      console.error(error);
    }
  }, [error]);

  const columnDefs = useMemo(
    () => [
      { fieldName: 'name', header: 'Name' },
      {
        fieldName: 'statusHealthy',
        header: 'Status',
        cell: ({ row }) => (
          <StatusBadge type={statusFromHealthy(row.originalValue.statusHealthy, row.originalValue.statusMessage)} customLabel={row.originalValue.statusMessage} />
        ),
      },
      {
        fieldName: 'functionsRunning',
        header: 'Functions',
        cell: ({ row }) => (
          <div>
            <div>{row.originalValue.functionsRunning} running</div>
            <div>{row.originalValue.functionsUnknown} unknown</div>
            <div className={acFontRed}>{row.originalValue.functionsFailed} failed</div>
          </div>
        ),
      },
      {
        fieldName: 'webhooksRunning',
        header: 'Webhooks',
        cell: ({ row }) => (
          <div>
            <div>{row.originalValue.webhooksRunning} running</div>
            <div>{row.originalValue.webhooksWaiting} waiting</div>
            <div className={acFontRed}>{row.originalValue.webhooksFailed} failed</div>
          </div>
        ),
      },
      {
        fieldName: 'enabled',
        header: 'Enabled',
        cell: ({ row }) => <>{row.originalValue.enabled ? 'Yes' : 'No'}</>,
      },
    ],
    [acFontRed]
  );

  const context = useForm({
    mode: 'onBlur',
    values: apiResource as FieldValues,
    defaultValues: APIDefaultValues,
    resolver: yupResolver(APISchema),
  });

  const createAPI = useCallback(() => {
    context.reset({ ...APIDefaultValues });
    setCreateModal(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const actionButtons: DataGridActionButtons[] = useMemo(
    () => [
      {
        inputType: 'button',
        children: 'Add API',
        colorClass: ButtonColorClasses.PRIMARY,
        endIcon: <AddSmall />,
        onClick: () => createAPI(),
      },
    ],
    [createAPI]
  );

  const fetchResource = useCallback(
    async (name: string): Promise<APIListItem | null> => {
      try {
        const response = await fetch(`${FIND_URL}/${encodeURIComponent(name)}`, headers);
        if (response.ok) {
          return await response.json();
        }
        notifyError('Failed to fetch API', true);
        return null;
      } catch (e) {
        notifyError('Failed to fetch API', true);
        return null;
      }
    },
    [headers, notifyError]
  );

  const handleEditClick = useCallback(
    async (row) => {
      const data = await fetchResource(row.name);
      if (data) {
        setApiResource(data);
        setEditDrawer(true);
      }
    },
    [fetchResource]
  );

  const handleVariablesClick = useCallback((row) => {
    setSelectedApiName(row.name);
    setVariablesDrawer(true);
  }, []);

  const handleCertificateClick = useCallback((row) => {
    setSelectedApiName(row.name);
    setCertificateDrawer(true);
  }, []);

  const handleOAuth2Click = useCallback((row) => {
    setSelectedApiName(row.name);
    setOAuth2Drawer(true);
  }, []);

  const handleDeleteClick = useCallback(
    async (row) => {
      const data = await fetchResource(row.name);
      if (data) {
        setApiResource(data);
        setDeleteModal(true);
      }
    },
    [fetchResource]
  );

  const showMoreCallback = useCallback(
    (row): MenuItem[] => [
      {
        onClick: (row) => handleEditClick(row),
        icon: () => <EditGw width="1.5rem" height="1.5rem" data-icon="edit" />,
        text: 'Edit',
        divider: true,
      },
      {
        onClick: (row) => handleVariablesClick(row),
        icon: () => <Key width="1.5rem" height="1.5rem" data-icon="variables" />,
        text: 'Variables',
      },
      {
        onClick: (row) => handleCertificateClick(row),
        icon: () => <Security width="1.5rem" height="1.5rem" data-icon="certificate" />,
        text: 'Certificate',
      },
      {
        onClick: (row) => handleOAuth2Click(row),
        icon: () => <Key width="1.5rem" height="1.5rem" data-icon="oauth2" />,
        text: 'OAuth2',
        divider: true,
      },
      {
        text: 'Delete',
        icon: () => <DeleteGw height={'1.5rem'} width={'1.5rem'} />,
        onClick: (row) => handleDeleteClick(row),
        className: acDeleteButton,
      },
    ],
    [handleEditClick, handleVariablesClick, handleCertificateClick, handleOAuth2Click, handleDeleteClick, acDeleteButton]
  );

  const addAPI = async () => {
    try {
      const formValues = context.getValues();

      if (data.items.some((item) => item.name === formValues.name)) {
        notifyError(`An API named "${formValues.name}" already exists`, true);
        return;
      }

      const config: APIConfig = { ...DEFAULT_API_CONFIG, enabled: formValues.config.enabled, configuration: formValues.config.configuration };
      const response = await fetch(RESOURCE_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify([{ name: formValues.name, config }]),
      });
      if (response.ok) {
        setCreateModal(false);
        notifySuccess('API added', true);
        tableRefresh();
        context.reset({ ...APIDefaultValues });
      } else if (response.status === 409) {
        notifyError(`An API named "${formValues.name}" already exists`, true);
      } else {
        notifyError(await getErrorMessage(response, 'Failed to add API'), true);
      }
    } catch (e) {
      notifyError('Failed to add API. View logs for more details.', true);
    }
  };

  const editAPI = async () => {
    if (!apiResource) return;
    try {
      const formValues = context.getValues();
      const config: APIConfig = { ...apiResource.config, enabled: formValues.config.enabled, configuration: formValues.config.configuration };
      const response = await fetch(RESOURCE_URL, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify([{ name: apiResource.name, signature: apiResource.signature, config }]),
      });
      if (response.ok) {
        setEditDrawer(false);
        notifySuccess('Changes saved', true);
        tableRefresh();
        setApiResource(undefined);
      } else {
        notifyError(await getErrorMessage(response, 'Failed to update API'), true);
      }
    } catch (e) {
      notifyError('Failed to update API. View logs for more details.', true);
    }
  };

  const deleteAPI = async () => {
    if (!apiResource) return;
    try {
      const response = await fetch(`${RESOURCE_URL}/${encodeURIComponent(apiResource.name)}/${encodeURIComponent(String(apiResource.signature))}`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
      });
      if (response.ok) {
        setDeleteModal(false);
        notifySuccess('API deleted', true);
        tableRefresh();
        setApiResource(undefined);
      } else {
        notifyError(await getErrorMessage(response, 'Failed to delete API'), true);
      }
    } catch (e) {
      notifyError('Failed to delete API. View logs for more details.', true);
    }
  };

  const createModalContent = (
    <Modal
      open={createModal}
      onClose={() => setCreateModal(false)}
      hideHeader={true}
      title={'Add API'}
      modalConfig={{ content: <APIAddModal close={() => setCreateModal(false)} create={addAPI} context={context} /> }}
      type={ModalType.CUSTOM}
    />
  );

  const blankStateComponent = (
    <>
      <BlankState
        content={'Interact with REST APIs from Ignition without scripting.'}
        label={'No APIs'}
        primaryButton={
          <Button colorClass={ButtonColorClasses.PRIMARY} endIcon={<AddSmall data-icon="create" />} onClick={() => createAPI()}>
            Add API
          </Button>
        }
        icon={<NavServices width="4rem" height="4rem" />}
      />
      {createModalContent}
    </>
  );

  const apisContent = (
    <div className={acTableContainer}>
      <DataGrid
        columnDefs={columnDefs}
        itemName="API"
        data={data.items}
        id="apis-data-grid"
        paginationParams={{
          total: data.metadata.total,
          matching: data.metadata.matching,
          limit: data.metadata.limit,
          offset: data.metadata.offset,
        }}
        setTableQueryParams={setQueryParams}
        actionButtons={actionButtons}
        showMore={showMoreCallback}
      />
      <APIEditDrawer open={editDrawer} onClose={() => setEditDrawer(false)} apiName={apiResource?.name} context={context} onComplete={editAPI} />
      <APIVariablesDrawer open={variablesDrawer} onClose={() => setVariablesDrawer(false)} apiName={selectedApiName} />
      <APICertificateDrawer open={certificateDrawer} onClose={() => setCertificateDrawer(false)} apiName={selectedApiName} />
      <APIOAuth2Drawer open={oauth2Drawer} onClose={() => setOAuth2Drawer(false)} apiName={selectedApiName} />
      <Modal
        open={deleteModal}
        title={'Delete API?'}
        hideHeader={false}
        modalConfig={{
          primaryText: 'Delete',
          secondaryText: 'Cancel',
          confirmationText: `Are you sure you want to delete API ${apiResource?.name}?`,
        }}
        type={ModalType.CONFIRM}
        onClose={() => setDeleteModal(false)}
        onConfirm={deleteAPI}
      />
      {createModalContent}
    </div>
  );

  return (
    <>
      <PageHeader pageTitle="APIs" />
      {data.metadata.total === 0 ? <div className={acBlankStateContainer}>{blankStateComponent}</div> : apisContent}
    </>
  );
};

export default APIsPage;
