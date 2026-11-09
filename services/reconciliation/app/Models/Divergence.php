<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;

#[Fillable([
    'statement_import_id', 'statement_entry_id', 'transaction_id', 'reference', 'type',
    'details', 'status',
])]
class Divergence extends Model
{
    protected function casts(): array
    {
        return [
            'details' => 'array',
        ];
    }
}
