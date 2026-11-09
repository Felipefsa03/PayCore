<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable([
    'statement_import_id', 'line_number', 'reference', 'amount_cents', 'currency',
    'status', 'occurred_at', 'row_hash', 'is_duplicate', 'is_valid', 'validation_error',
])]
class StatementEntry extends Model
{
    protected function casts(): array
    {
        return [
            'amount_cents' => 'integer',
            'occurred_at' => 'immutable_datetime',
            'is_duplicate' => 'boolean',
            'is_valid' => 'boolean',
        ];
    }

    public function statementImport(): BelongsTo
    {
        return $this->belongsTo(StatementImport::class);
    }
}
