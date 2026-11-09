<?php

use App\Http\Controllers\Api\StatementImportController;
use Illuminate\Support\Facades\Route;

Route::middleware('api-key')->group(function () {
    Route::post('/statement-imports', [StatementImportController::class, 'store']);
    Route::get('/statement-imports/{statementImport}', [StatementImportController::class, 'show']);
    Route::get('/statement-imports/{statementImport}/divergences', [StatementImportController::class, 'divergences']);
});
