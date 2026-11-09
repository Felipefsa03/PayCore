<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Every parsed row is kept, including duplicates and invalid ones, so an import is
     * fully auditable; is_duplicate/is_valid gate which rows feed reconciliation matching.
     */
    public function up(): void
    {
        Schema::create('statement_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('statement_import_id')->constrained()->cascadeOnDelete();
            $table->unsignedInteger('line_number');
            $table->string('reference')->nullable();
            $table->bigInteger('amount_cents')->nullable();
            $table->char('currency', 3)->nullable();
            $table->string('status')->nullable();
            $table->timestampTz('occurred_at')->nullable();
            $table->string('row_hash', 64);
            $table->boolean('is_duplicate')->default(false);
            $table->boolean('is_valid')->default(true);
            $table->text('validation_error')->nullable();
            $table->timestamps();

            $table->index(['statement_import_id', 'reference']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('statement_entries');
    }
};
