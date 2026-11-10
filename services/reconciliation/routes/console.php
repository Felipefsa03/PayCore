<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

Artisan::command('reconciliation:sync {reference}', function () {
    $reference = $this->argument('reference');
    if (! \Illuminate\Support\Str::isUuid($reference)) {
        $this->error('Expected a transaction UUID.');
        return 1;
    }
    $url = config('reconciliation.integration_url');
    $key = config('reconciliation.partner_key');
    if (! $url || ! $key) {
        $this->error('Configure the Integration API URL and partner key.');
        return 1;
    }
    $data = \Illuminate\Support\Facades\Http::acceptJson()->withoutRedirecting()
        ->withHeaders(['X-Api-Key' => $key])->timeout(5)
        ->get(rtrim($url, '/').'/v1/transactions/'.$reference)->throw()->json();
    if ($data['id'] !== $reference || $data['state'] !== 'COMPLETED' || empty($data['journalEntryId'])) {
        $this->error('Expected a completed transfer with a journal entry.');
        return 1;
    }
    $values = ['amount_cents' => $data['amount'], 'currency' => $data['currency'],
        'status' => 'settled', 'occurred_at' => \Carbon\CarbonImmutable::parse($data['updatedAt'])->utc()];
    $snapshot = \App\Models\Transaction::firstOrCreate(['reference' => $reference], $values);
    if ($snapshot->fill($values)->isDirty()) {
        $this->error('Existing reference snapshot conflicts with the Integration API.');
        return 1;
    }
    return 0;
})->purpose('Import a settled transfer through the authenticated Integration API');
