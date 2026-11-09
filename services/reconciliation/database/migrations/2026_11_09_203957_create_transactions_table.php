<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Fictitious ledger transactions this service reconciles bank statements against.
     * In production this is populated from financial-core's transaction export; here it
     * is seeded to simulate that boundary without introducing a live cross-service call.
     */
    public function up(): void
    {
        Schema::create('transactions', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->bigInteger('amount_cents');
            $table->char('currency', 3);
            $table->string('status');
            $table->timestampTz('occurred_at');
            $table->timestamps();

            $table->index('occurred_at');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('transactions');
    }
};
