<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable([
    'original_filename', 'storage_path', 'file_hash', 'status', 'total_rows',
    'matched_rows', 'duplicate_rows', 'error_rows', 'failure_reason', 'started_at',
    'completed_at',
])]
class StatementImport extends Model
{
    protected function casts(): array
    {
        return [
            'started_at' => 'immutable_datetime',
            'completed_at' => 'immutable_datetime',
        ];
    }

    public function entries(): HasMany
    {
        return $this->hasMany(StatementEntry::class);
    }

    public function divergences(): HasMany
    {
        return $this->hasMany(Divergence::class);
    }
}
