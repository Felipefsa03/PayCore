<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class DivergenceResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'reference' => $this->reference,
            'type' => $this->type,
            'status' => $this->status,
            'details' => $this->details,
            'statement_entry_id' => $this->statement_entry_id,
            'transaction_id' => $this->transaction_id,
            'created_at' => $this->created_at,
        ];
    }
}
