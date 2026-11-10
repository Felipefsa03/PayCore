<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('divergence_decisions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('divergence_id')->constrained();
            $table->string('status', 32);
            $table->string('operator_reference', 100);
            $table->text('reason');
            $table->timestampTz('created_at');
            $table->unique(['divergence_id', 'status']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('divergence_decisions');
    }
};
