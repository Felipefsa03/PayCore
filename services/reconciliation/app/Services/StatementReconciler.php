<?php

namespace App\Services;

use App\Models\Divergence;
use App\Models\DivergenceStatus;
use App\Models\DivergenceType;
use App\Models\StatementEntry;
use App\Models\StatementImport;
use App\Models\Transaction;

/**
 * Matches imported statement entries against the fictitious ledger transactions and
 * records every divergence so nothing is silently dropped or auto-corrected.
 */
class StatementReconciler
{
    /**
     * Matches a single valid, non-duplicate entry against the ledger. Returns true when
     * the entry matched cleanly (no divergence recorded).
     */
    public function matchEntry(StatementEntry $entry): bool
    {
        $transaction = Transaction::where('reference', $entry->reference)->first();

        if (! $transaction) {
            $this->recordDivergence(
                statementImportId: $entry->statement_import_id,
                statementEntryId: $entry->id,
                transactionId: null,
                reference: $entry->reference,
                type: DivergenceType::MissingInLedger,
                details: [
                    'statement_amount_cents' => $entry->amount_cents,
                    'statement_currency' => $entry->currency,
                    'statement_status' => $entry->status,
                ],
            );

            return false;
        }

        $mismatches = $this->diff($entry, $transaction);

        if ($mismatches !== []) {
            $this->recordDivergence(
                statementImportId: $entry->statement_import_id,
                statementEntryId: $entry->id,
                transactionId: $transaction->id,
                reference: $entry->reference,
                type: DivergenceType::Mismatch,
                details: $mismatches,
            );

            return false;
        }

        return true;
    }

    /**
     * A reference reported more than once inside the same file is a divergence on its own:
     * the duplicate rows are stored for audit but never matched against the ledger.
     */
    public function flagDuplicate(StatementEntry $entry): void
    {
        $this->recordDivergence(
            statementImportId: $entry->statement_import_id,
            statementEntryId: $entry->id,
            transactionId: null,
            reference: $entry->reference,
            type: DivergenceType::DuplicateInFile,
            details: [
                'line_number' => $entry->line_number,
                'amount_cents' => $entry->amount_cents,
                'currency' => $entry->currency,
            ],
        );
    }

    /**
     * A row that could not be parsed or failed field validation is never matched against
     * the ledger, but it still needs a trackable divergence so nothing is silently dropped.
     */
    public function flagInvalid(StatementEntry $entry, ?string $reason): void
    {
        $this->recordDivergence(
            statementImportId: $entry->statement_import_id,
            statementEntryId: $entry->id,
            transactionId: null,
            reference: $entry->reference,
            type: DivergenceType::InvalidRow,
            details: [
                'line_number' => $entry->line_number,
                'reason' => $reason,
            ],
        );
    }

    /**
     * Ledger transactions dated within the imported window that no entry in this import
     * referenced at all are absences: money the ledger knows about that the bank extract
     * never mentioned.
     */
    public function detectMissingInStatement(StatementImport $import): void
    {
        $validEntries = $import->entries()->where('is_valid', true)->where('is_duplicate', false)->get();

        if ($validEntries->isEmpty()) {
            return;
        }

        $seenReferences = $validEntries->pluck('reference')->filter()->all();
        $periodStart = $validEntries->min('occurred_at');
        $periodEnd = $validEntries->max('occurred_at');

        if (! $periodStart || ! $periodEnd) {
            return;
        }

        Transaction::whereBetween('occurred_at', [$periodStart, $periodEnd])
            ->whereNotIn('reference', $seenReferences)
            ->each(function (Transaction $transaction) use ($import) {
                $alreadyFlagged = Divergence::where('transaction_id', $transaction->id)
                    ->where('type', DivergenceType::MissingInStatement->value)
                    ->where('status', DivergenceStatus::Open->value)
                    ->exists();

                if ($alreadyFlagged) {
                    return;
                }

                $this->recordDivergence(
                    statementImportId: $import->id,
                    statementEntryId: null,
                    transactionId: $transaction->id,
                    reference: $transaction->reference,
                    type: DivergenceType::MissingInStatement,
                    details: [
                        'ledger_amount_cents' => $transaction->amount_cents,
                        'ledger_currency' => $transaction->currency,
                        'ledger_status' => $transaction->status,
                    ],
                );
            });
    }

    /**
     * @return array<string, mixed>
     */
    private function diff(StatementEntry $entry, Transaction $transaction): array
    {
        $mismatches = [];

        if ($entry->amount_cents !== $transaction->amount_cents) {
            $mismatches['amount_cents'] = ['expected' => $transaction->amount_cents, 'actual' => $entry->amount_cents];
        }

        if ($entry->currency !== $transaction->currency) {
            $mismatches['currency'] = ['expected' => $transaction->currency, 'actual' => $entry->currency];
        }

        if ($entry->status !== $transaction->status) {
            $mismatches['status'] = ['expected' => $transaction->status, 'actual' => $entry->status];
        }

        return $mismatches;
    }

    /**
     * @param  array<string, mixed>  $details
     */
    private function recordDivergence(
        int $statementImportId,
        ?int $statementEntryId,
        ?int $transactionId,
        ?string $reference,
        DivergenceType $type,
        array $details,
    ): void {
        Divergence::create([
            'statement_import_id' => $statementImportId,
            'statement_entry_id' => $statementEntryId,
            'transaction_id' => $transactionId,
            'reference' => $reference,
            'type' => $type->value,
            'details' => $details,
            'status' => DivergenceStatus::Open->value,
        ]);
    }
}
