export interface APIVariable {
  key: string;
  value: string;
  required: boolean;
  sensitive: boolean;
  hidden: boolean;
}

export interface APICertificate {
  certificate: string;
  privateKey: unknown;
}

export interface APIWebhookKey {
  webhookName: string;
  key: string;
  id: string | null;
  ttl: number | null;
}

// Mirrors gateway/.../records/APIResource.java - the resource's typed config payload,
// nested under "config" in the generic ConfigurationManager REST resource shape.
export interface APIConfig {
  enabled: boolean;
  configuration: string;
  variables: APIVariable[];
  certificate: APICertificate | null;
  webhookKeys: APIWebhookKey[];
}

// The generic resource envelope returned by /data/api/v1/resources/{list,find}/... -
// name/description/signature are resource-level metadata, the actual APIResource fields
// live under "config".
export interface APIListItem {
  name: string;
  description?: string;
  signature: unknown;
  config: APIConfig;
}

export const DEFAULT_API_CONFIG: APIConfig = {
  enabled: true,
  configuration: '',
  variables: [],
  certificate: null,
  webhookKeys: [],
};
