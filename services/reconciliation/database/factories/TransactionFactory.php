<?php

namespace Database\Factories;

use App\Models\Transaction;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Transaction>
 */
class TransactionFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'reference' => 'TXN-'.Str::upper(Str::random(10)),
            'amount_cents' => $this->faker->numberBetween(100, 5_000_00),
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => $this->faker->dateTimeBetween('-30 days', 'now'),
        ];
    }
}
