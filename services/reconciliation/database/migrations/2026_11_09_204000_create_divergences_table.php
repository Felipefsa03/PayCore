<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * One row per detected divergence (duplicate, mismatch, missing_in_ledger,
     * missing_in_statement, invalid_row). Resolution workflow is a separate card; status
     * here only distinguishes open from resolved so this table can be extended later
     * without a migration that touches reconciliation logic.
     */
    public function up(): void
    {
        Schema::create('divergences', function (Blueprint $table) {
            $table->id();
            $table->foreignId('statement_import_id')->nullable()->constrained()->cascadeOnDelete();
            $table->foreignId('statement_entry_id')->nullable()->constrained()->cascadeOnDelete();
            $table->foreignId('transaction_id')->nullable()->constrained()->cascadeOnDelete();
            $table->string('reference')->nullable();
            $table->string('type');
            $table->json('details')->nullable();
            $table->string('status')->default('open');
            $table->timestamps();

            $table->index(['statement_import_id', 'type']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('divergences');
    }
};
