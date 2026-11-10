<?php

return [
    'integration_url' => env('RECONCILIATION_INTEGRATION_URL'),
    'partner_key' => env('RECONCILIATION_PARTNER_KEY'),
    /*
    |--------------------------------------------------------------------------
    | Partner API key
    |--------------------------------------------------------------------------
    |
    | Shared secret required on the X-Api-Key header for every /api/* request.
    | This is an internal satellite service, not partner-facing, so a single
    | key is enough; it is never committed and must be set via environment.
    */
    'api_key' => env('RECONCILIATION_API_KEY'),

    /*
    |--------------------------------------------------------------------------
    | Upload limits
    |--------------------------------------------------------------------------
    */
    'max_upload_kb' => (int) env('RECONCILIATION_MAX_UPLOAD_KB', 5120),

    /*
    |--------------------------------------------------------------------------
    | Statement storage disk
    |--------------------------------------------------------------------------
    |
    | Must be a private disk (never "public"): imported statements can carry
    | sensitive financial data and are never served directly.
    */
    'statements_disk' => env('RECONCILIATION_STATEMENTS_DISK', 'local'),
];
