<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\StoreStatementImportRequest;
use App\Http\Resources\DivergenceResource;
use App\Http\Resources\StatementImportResource;
use App\Jobs\ProcessStatementImport;
use App\Models\StatementImport;
use App\Models\StatementImportStatus;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

class StatementImportController extends Controller
{
    /**
     * Uploading the exact same bytes twice is idempotent: it returns the existing import
     * instead of reprocessing, no matter what filename was used either time.
     */
    public function store(StoreStatementImportRequest $request): JsonResponse
    {
        $file = $request->file('statement');
        $hash = hash_file('sha256', $file->getRealPath());

        $existing = StatementImport::where('file_hash', $hash)->first();

        if ($existing) {
            return (new StatementImportResource($existing))
                ->response()
                ->setStatusCode(JsonResponse::HTTP_OK);
        }

        $disk = config('reconciliation.statements_disk');
        $storagePath = "statements/{$hash}.csv";

        try {
            abort_unless(Storage::disk($disk)->put($storagePath, file_get_contents($file->getRealPath())), 503);
            $import = DB::transaction(function () use ($file, $storagePath, $hash) {

                $import = StatementImport::create([
                    'original_filename' => $file->getClientOriginalName(),
                    'storage_path' => $storagePath,
                    'file_hash' => $hash,
                    'status' => StatementImportStatus::Pending->value,
                ]);
                ProcessStatementImport::dispatch($import->id);

                return $import;
            });
        } catch (UniqueConstraintViolationException) {
            // Lost a race against a concurrent upload of the same file: the other
            // request owns processing, so hand back its result instead of duplicating it.
            $existing = StatementImport::where('file_hash', $hash)->firstOrFail();

            return (new StatementImportResource($existing))
                ->response()
                ->setStatusCode(JsonResponse::HTTP_OK);
        }

        return (new StatementImportResource($import))
            ->response()
            ->setStatusCode(JsonResponse::HTTP_ACCEPTED);
    }

    public function show(StatementImport $statementImport): StatementImportResource
    {
        return new StatementImportResource($statementImport);
    }

    public function divergences(StatementImport $statementImport): AnonymousResourceCollection
    {
        return DivergenceResource::collection(
            $statementImport->divergences()->orderBy('id')->paginate(50)
        );
    }
}
