export const SUPPORTED_EVENT_TYPES = ['payment.completed', 'payment.rejected'] as const;

export type SupportedEventType = (typeof SUPPORTED_EVENT_TYPES)[number];
