<?php

namespace App\Http\Requests;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class StoreStatementImportRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    /**
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'statement' => [
                'required',
                'file',
                'extensions:csv,txt',
                'mimetypes:text/csv,text/plain,application/csv,application/vnd.ms-excel',
                'max:'.config('reconciliation.max_upload_kb'),
            ],
        ];
    }
}
