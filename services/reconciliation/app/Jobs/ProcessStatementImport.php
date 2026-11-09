<?php

namespace App\Jobs;

use App\Models\StatementEntry;
use App\Models\StatementImport;
use App\Models\StatementImportStatus;
use App\Services\CsvStatementParser;
use App\Services\StatementReconciler;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Throwable;

class ProcessStatementImport implements ShouldQueue
{
    use Queueable;

    public function __construct(public int $statementImportId) {}

    public function handle(CsvStatementParser $parser, StatementReconciler $reconciler): void
    {
        $import = StatementImport::findOrFail($this->statementImportId);

        if ($import->status !== StatementImportStatus::Pending->value) {
            // Already processed or in flight: file_hash uniqueness should prevent this,
            // but guard against duplicate job dispatch anyway.
            return;
        }

        $import->update(['status' => StatementImportStatus::Processing->value, 'started_at' => now()]);

        $totalRows = 0;
        $matchedRows = 0;
        $duplicateRows = 0;
        $errorRows = 0;
        $seenReferences = [];

        try {
            $absolutePath = Storage::disk(config('reconciliation.statements_disk'))->path($import->storage_path);

            foreach ($parser->parse($absolutePath) as $row) {
                $totalRows++;
                $rowHash = hash('sha256', $row['raw']);

                if ($row['fields'] === null) {
                    $entry = StatementEntry::create([
                        'statement_import_id' => $import->id,
                        'line_number' => $row['line'],
                        'row_hash' => $rowHash,
                        'is_valid' => false,
                        'validation_error' => $row['error'],
                    ]);
                    $reconciler->flagInvalid($entry, $row['error']);
                    $errorRows++;

                    continue;
                }

                [$parsed, $validationError] = $this->validateFields($row['fields']);

                if ($validationError !== null) {
                    $entry = StatementEntry::create([
                        'statement_import_id' => $import->id,
                        'line_number' => $row['line'],
                        'reference' => $parsed['reference'] ?? null,
                        'row_hash' => $rowHash,
                        'is_valid' => false,
                        'validation_error' => $validationError,
                    ]);
                    $reconciler->flagInvalid($entry, $validationError);
                    $errorRows++;

                    continue;
                }

                $isDuplicate = in_array($parsed['reference'], $seenReferences, true);

                $entry = StatementEntry::create([
                    'statement_import_id' => $import->id,
                    'line_number' => $row['line'],
                    'reference' => $parsed['reference'],
                    'amount_cents' => $parsed['amount_cents'],
                    'currency' => $parsed['currency'],
                    'status' => $parsed['status'],
                    'occurred_at' => $parsed['occurred_at'],
                    'row_hash' => $rowHash,
                    'is_duplicate' => $isDuplicate,
                    'is_valid' => true,
                ]);

                if ($isDuplicate) {
                    $duplicateRows++;
                    $reconciler->flagDuplicate($entry);

                    continue;
                }

                $seenReferences[] = $parsed['reference'];

                if ($reconciler->matchEntry($entry)) {
                    $matchedRows++;
                }
            }

            $reconciler->detectMissingInStatement($import);

            $import->update([
                'status' => StatementImportStatus::Completed->value,
                'completed_at' => now(),
                'total_rows' => $totalRows,
                'matched_rows' => $matchedRows,
                'duplicate_rows' => $duplicateRows,
                'error_rows' => $errorRows,
            ]);
        } catch (Throwable $e) {
            Log::error('Statement import failed', ['statement_import_id' => $import->id, 'error' => $e->getMessage()]);

            $import->update([
                'status' => StatementImportStatus::Failed->value,
                'completed_at' => now(),
                'total_rows' => $totalRows,
                'matched_rows' => $matchedRows,
                'duplicate_rows' => $duplicateRows,
                'error_rows' => $errorRows,
                'failure_reason' => $e->getMessage(),
            ]);
        }
    }

    /**
     * @param  array<string, string>  $fields
     * @return array{0: array<string, mixed>, 1: string|null}
     */
    private function validateFields(array $fields): array
    {
        $reference = trim($fields['reference'] ?? '');
        $amountRaw = trim($fields['amount_cents'] ?? '');
        $currency = trim($fields['currency'] ?? '');
        $status = trim($fields['status'] ?? '');
        $occurredAtRaw = trim($fields['occurred_at'] ?? '');

        if ($reference === '') {
            return [['reference' => null], 'Missing reference.'];
        }

        if (! preg_match('/^-?\d+$/', $amountRaw)) {
            return [['reference' => $reference], 'Invalid amount_cents: must be an integer.'];
        }

        if (strlen($currency) !== 3) {
            return [['reference' => $reference], 'Invalid currency: must be a 3-letter code.'];
        }

        if ($status === '') {
            return [['reference' => $reference], 'Missing status.'];
        }

        try {
            $occurredAt = Carbon::parse($occurredAtRaw);
        } catch (Throwable) {
            return [['reference' => $reference], 'Invalid occurred_at: could not parse date.'];
        }

        return [[
            'reference' => $reference,
            'amount_cents' => (int) $amountRaw,
            'currency' => strtoupper($currency),
            'status' => $status,
            'occurred_at' => $occurredAt,
        ], null];
    }
}
