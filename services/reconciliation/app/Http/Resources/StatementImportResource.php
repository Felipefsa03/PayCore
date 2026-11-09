<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class StatementImportResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'original_filename' => $this->original_filename,
            'file_hash' => $this->file_hash,
            'status' => $this->status,
            'total_rows' => $this->total_rows,
            'matched_rows' => $this->matched_rows,
            'duplicate_rows' => $this->duplicate_rows,
            'error_rows' => $this->error_rows,
            'failure_reason' => $this->failure_reason,
            'started_at' => $this->started_at,
            'completed_at' => $this->completed_at,
            'created_at' => $this->created_at,
        ];
    }
}
