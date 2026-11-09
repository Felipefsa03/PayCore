export const ALPHA = {
  id: 'partner-alpha',
  name: 'Parceiro Alpha',
  apiKey: 'alpha-test-key-0001',
  ownerId: '11111111-1111-4111-8111-111111111111',
} as const;

export const BETA = {
  id: 'partner-beta',
  name: 'Parceiro Beta',
  apiKey: 'beta-test-key-0002',
  ownerId: '22222222-2222-4222-8222-222222222222',
} as const;

export const LIMITED = {
  id: 'partner-limited',
  name: 'Parceiro Limitado',
  apiKey: 'limited-test-key-0003',
  ownerId: '33333333-3333-4333-8333-333333333333',
  rateLimitPerMinute: 2,
} as const;

export const PARTNERS_ENV = [ALPHA, BETA, LIMITED];
