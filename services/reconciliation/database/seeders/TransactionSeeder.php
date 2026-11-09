<?php

namespace Database\Seeders;

use App\Models\Transaction;
use Illuminate\Database\Seeder;

/**
 * Populates the fictitious ledger view this service reconciles bank statements against.
 * Stands in for the financial-core export until a real cross-service feed exists.
 */
class TransactionSeeder extends Seeder
{
    public function run(): void
    {
        Transaction::factory()->count(25)->create();
    }
}
