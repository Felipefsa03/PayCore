<?php

use App\Http\Controllers\Api\DivergenceController;
use App\Http\Controllers\Api\StatementImportController;
use Illuminate\Support\Facades\Route;

Route::middleware('api-key')->group(function () {
    Route::get('/reconciliation-report', [DivergenceController::class, 'report']);
    Route::get('/divergences/{divergence}/decisions', [DivergenceController::class, 'history']);
    Route::post('/divergences/{divergence}/decisions', [DivergenceController::class, 'decide']);
    Route::post('/statement-imports', [StatementImportController::class, 'store']);
    Route::get('/statement-imports/{statementImport}', [StatementImportController::class, 'show']);
    Route::get('/statement-imports/{statementImport}/divergences', [StatementImportController::class, 'divergences']);
});
