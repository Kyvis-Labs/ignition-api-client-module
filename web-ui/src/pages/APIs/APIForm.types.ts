export interface APIVariable {
  key: string;
  value: string;
  required: boolean;
  sensitive: boolean;
  hidden: boolean;
}

export interface APICertificate {
  certificate: string;
  privateKey: string;
}

export interface APIFormData {
  enabled: boolean;
  configuration: string;
  variables: APIVariable[];
  certificate: APICertificate | null;
}

export interface APIResource {
  name: string;
  data: APIFormData;
}
