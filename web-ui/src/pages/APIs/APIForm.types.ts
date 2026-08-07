import { yup } from '@inductiveautomation/ignition-web-ui';
import { FieldValues } from 'react-hook-form';

export interface APIVariable {
  key: string;
  value: unknown; // encrypted SecretConfig - populated only when sensitive is true
  plainValue: string | null; // populated only when sensitive is false
  required: boolean;
  sensitive: boolean;
  hidden: boolean;
}

export interface APICertificate {
  certificate: string;
  privateKey: unknown;
}

// Mirrors gateway/.../records/APIResource.java - the resource's typed config payload,
// nested under "config" in the generic ConfigurationManager REST resource shape. Webhook keys are
// runtime state persisted separately (see WebhookKeyStore), not part of this resource at all.
export interface APIConfig {
  enabled: boolean;
  configuration: string;
  variables: APIVariable[];
  certificate: APICertificate | null;
}

// Populated by ResourceTypes.buildStatusDelegate + API.registerMetrics() - see gateway/.../records/ResourceTypes.java
export interface APIHealthChecks {
  status?: { result?: { healthy: boolean; message: string } };
}

export interface APIMetrics {
  functionsRunning?: { metric?: { value: number } };
  functionsUnknown?: { metric?: { value: number } };
  functionsFailed?: { metric?: { value: number } };
  webhooksRunning?: { metric?: { value: number } };
  webhooksWaiting?: { metric?: { value: number } };
  webhooksFailed?: { metric?: { value: number } };
}

// The generic resource envelope returned by /data/api/v1/resources/{list,find}/... -
// name/description/signature are resource-level metadata, the actual APIResource fields
// live under "config".
export interface APIListItem {
  name: string;
  description?: string;
  signature: unknown;
  config: APIConfig;
  healthchecks?: APIHealthChecks;
  metrics?: APIMetrics;
}

// A flattened, minimal projection of APIListItem used only for the list table row state.
// Deliberately drops anything not rendered in a column (raw healthcheck/metric objects carry
// volatile timestamp/duration fields that change on every poll even when nothing meaningful
// changed, which defeats useFetch's dedup check and forces a re-render every poll tick).
export interface APIRow {
  name: string;
  enabled: boolean;
  statusHealthy: boolean;
  statusMessage: string;
  functionsRunning: number;
  functionsUnknown: number;
  functionsFailed: number;
  webhooksRunning: number;
  webhooksWaiting: number;
  webhooksFailed: number;
}

export const DEFAULT_API_CONFIG: APIConfig = {
  enabled: true,
  configuration: '',
  variables: [],
  certificate: null,
};

// Matches APIManager's variables REST routes (Variables.VariableInfo on the Java side).
// value is only populated for non-sensitive variables - sensitive ones only ever expose hasValue.
export interface VariableInfo {
  key: string;
  required: boolean;
  sensitive: boolean;
  hidden: boolean;
  hasValue: boolean;
  value: string | null;
}

export interface VariablesResponse {
  editable: VariableInfo[];
  readOnly: VariableInfo[];
}

// Matches APIManager's certificate REST routes
export interface CertificateInfo {
  certificate: string;
  hasPrivateKey: boolean;
}

// Matches APIManager's oauth2 REST routes
export interface OAuth2Status {
  enabled: boolean;
  grantType: string | null;
  requiresPKCE: boolean;
  requiresAuthCode: boolean;
  requiresCaptcha: boolean;
  requiresTwoFactor: boolean;
  authorizationUrl: string | null;
  redirectUrl: string | null;
}

export namespace APIUtils {
  export const APISchema: yup.ObjectSchema<FieldValues> = yup.object({
    name: yup.string().required(),
    config: yup.object().shape({
      enabled: yup.boolean(),
      configuration: yup.string().required('YAML configuration is required'),
    }),
  });

  export const APIDefaultValues: FieldValues = {
    name: '',
    config: {
      enabled: true,
      configuration: '',
    },
  };
}
