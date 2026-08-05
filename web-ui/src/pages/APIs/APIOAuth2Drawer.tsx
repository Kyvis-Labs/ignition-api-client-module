import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Drawer,
  DrawerTemplate,
  DrawerTemplateColorTheme,
  DrawerTemplateSize,
  Card,
  TextInput,
  Button,
  ButtonColorClasses,
  Loading,
  useToastNotifications,
} from '@inductiveautomation/ignition-web-ui';
import { OAuth2Status } from './APIForm.types';
import { getAPIClientPageStyles } from '../_APIClient.styles';

export interface APIOAuth2DrawerProps {
  open: boolean;
  onClose(): void;
  apiName: string | undefined;
}

const OAUTH2_BASE = (name: string) => `/data/api-client/api/v1/oauth2/${encodeURIComponent(name)}`;

const APIOAuth2Drawer = ({ open, onClose, apiName }: APIOAuth2DrawerProps) => {
  const { notifySuccess, notifyError } = useToastNotifications();
  const [loading, setLoading] = useState<boolean>(true);
  const [status, setStatus] = useState<OAuth2Status | null>(null);
  const [captchaImage, setCaptchaImage] = useState<string | null>(null);
  const [captchaCode, setCaptchaCode] = useState<string>('');
  const [authCode, setAuthCode] = useState<string>('');
  const [twoFactorCode, setTwoFactorCode] = useState<string>('');
  const csrfToken = useSelector((state: any) => state?.userSession?.csrfToken);

  const {
    classes: { acForm, acDrawerCard, acStack },
  } = getAPIClientPageStyles();

  const load = () => {
    if (!apiName) return;
    setLoading(true);
    fetch(OAUTH2_BASE(apiName))
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load OAuth2 status');
        return r.json();
      })
      .then((data: OAuth2Status) => {
        setStatus(data);
        setLoading(false);
      })
      .catch(() => {
        notifyError('Failed to load OAuth2 status', true);
        setLoading(false);
      });
  };

  useEffect(() => {
    if (open) {
      setCaptchaImage(null);
      setCaptchaCode('');
      setAuthCode('');
      setTwoFactorCode('');
      load();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, apiName]);

  const authorize = async () => {
    if (!apiName) return;
    try {
      const response = await fetch(`${OAUTH2_BASE(apiName)}/authorize`, {
        method: 'POST',
        headers: { 'X-CSRF-Token': csrfToken },
      });
      if (response.ok) {
        const data = await response.json();
        if (data.captchaImageBase64) {
          setCaptchaImage(data.captchaImageBase64);
        } else {
          notifySuccess('Authorization started', true);
        }
      } else {
        notifyError('Failed to start authorization', true);
      }
    } catch (e) {
      notifyError('Failed to start authorization. View logs for more details.', true);
    }
  };

  const submitCode = async (path: string, code: string, successMessage: string) => {
    if (!apiName) return;
    try {
      const response = await fetch(`${OAUTH2_BASE(apiName)}/${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken },
        body: JSON.stringify({ code }),
      });
      if (response.ok) {
        notifySuccess(successMessage, true);
        onClose();
      } else {
        notifyError('Failed to save code', true);
      }
    } catch (e) {
      notifyError('Failed to save code. View logs for more details.', true);
    }
  };

  const resetTwoFactor = async () => {
    if (!apiName) return;
    try {
      const response = await fetch(`${OAUTH2_BASE(apiName)}/2fa-reset`, {
        method: 'POST',
        headers: { 'X-CSRF-Token': csrfToken },
      });
      if (response.ok) {
        notifySuccess('2FA reset', true);
        onClose();
      } else {
        notifyError('Failed to reset 2FA', true);
      }
    } catch (e) {
      notifyError('Failed to reset 2FA. View logs for more details.', true);
    }
  };

  return (
    <Drawer open={open} anchor={'right'} id={`oauth2-drawer-${apiName}`}>
      <DrawerTemplate
        path={[`OAuth2: ${apiName}`]}
        size={DrawerTemplateSize.SMALL}
        onClose={onClose}
        theme={DrawerTemplateColorTheme.GREY}
        hideButtonBar={true}
      >
        {loading ? (
          <Loading isLoading={true} />
        ) : !status?.enabled ? (
          <div className={acForm}>
            <p>This API is not configured for OAuth2 authentication.</p>
          </div>
        ) : (
          <div className={acForm}>
            {status.grantType === 'AUTHORIZATIONCODE' && !status.requiresPKCE && (
              <Card title={'AUTHORIZE'} className={acDrawerCard}>
                <div className={acStack}>
                  <p>Redirect URL: {status.redirectUrl}</p>
                  {status.authorizationUrl && (
                    <a href={status.authorizationUrl} target="_blank" rel="noreferrer">
                      Authorize
                    </a>
                  )}
                </div>
              </Card>
            )}

            {status.requiresPKCE && !status.requiresAuthCode && (
              <Card title={'AUTHORIZE'} className={acDrawerCard}>
                <div className={acStack}>
                  <Button colorClass={ButtonColorClasses.PRIMARY} onClick={authorize}>
                    Authorize
                  </Button>
                  {captchaImage && (
                    <div className={acStack}>
                      <img src={`data:image/png;base64,${captchaImage}`} alt="captcha" />
                      <TextInput value={captchaCode} onChange={(e) => setCaptchaCode(e.target.value)} placeholder={'Captcha code'} />
                      <Button
                        colorClass={ButtonColorClasses.PRIMARY}
                        onClick={() => submitCode('captcha-code', captchaCode, 'Captcha code saved')}
                      >
                        Submit
                      </Button>
                    </div>
                  )}
                </div>
              </Card>
            )}

            {status.requiresAuthCode && (
              <Card title={'AUTHORIZATION CODE'} className={acDrawerCard}>
                <div className={acStack}>
                  {status.authorizationUrl && (
                    <a href={status.authorizationUrl} target="_blank" rel="noreferrer">
                      Open authorization page
                    </a>
                  )}
                  <TextInput value={authCode} onChange={(e) => setAuthCode(e.target.value)} placeholder={'Authorization code'} />
                  <Button colorClass={ButtonColorClasses.PRIMARY} onClick={() => submitCode('auth-code', authCode, 'Authorization code saved')}>
                    Submit
                  </Button>
                </div>
              </Card>
            )}

            {status.requiresTwoFactor && (
              <Card title={'TWO-FACTOR CODE'} className={acDrawerCard}>
                <div className={acStack}>
                  <TextInput value={twoFactorCode} onChange={(e) => setTwoFactorCode(e.target.value)} placeholder={'2FA code'} />
                  <Button colorClass={ButtonColorClasses.PRIMARY} onClick={() => submitCode('2fa-code', twoFactorCode, '2FA code saved')}>
                    Submit
                  </Button>
                  <Button colorClass={ButtonColorClasses.SECONDARY} onClick={resetTwoFactor}>
                    Reset 2FA
                  </Button>
                </div>
              </Card>
            )}
          </div>
        )}
      </DrawerTemplate>
    </Drawer>
  );
};

export default APIOAuth2Drawer;
