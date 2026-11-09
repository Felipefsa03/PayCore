<?php

namespace Tests\Feature;

use App\Models\Divergence;
use App\Models\DivergenceType;
use App\Models\StatementImport;
use App\Models\Transaction;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class StatementImportTest extends TestCase
{
    use RefreshDatabase;

    private const API_KEY = 'phpunit-test-key';

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake('local');
    }

    private function csvFile(string $content, string $filename = 'statement.csv'): UploadedFile
    {
        return UploadedFile::fake()->createWithContent($filename, $content);
    }

    #[Test]
    public function it_imports_a_statement_and_classifies_every_divergence(): void
    {
        Transaction::factory()->create([
            'reference' => 'TXN-MATCH',
            'amount_cents' => 1000,
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => '2026-11-01T10:00:00Z',
        ]);

        Transaction::factory()->create([
            'reference' => 'TXN-MISMATCH',
            'amount_cents' => 2000,
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => '2026-11-02T10:00:00Z',
        ]);

        Transaction::factory()->create([
            'reference' => 'TXN-ABSENT',
            'amount_cents' => 3000,
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => '2026-11-01T15:00:00Z',
        ]);

        $csv = "reference,amount_cents,currency,status,occurred_at\n".
            "TXN-MATCH,1000,BRL,settled,2026-11-01T10:00:00Z\n".
            "TXN-MISMATCH,2500,BRL,settled,2026-11-02T10:00:00Z\n".
            "TXN-UNKNOWN,500,BRL,settled,2026-11-01T12:00:00Z\n".
            "TXN-MATCH,1000,BRL,settled,2026-11-01T10:00:00Z\n".
            "TXN-BAD,not-a-number,BRL,settled,2026-11-01T09:00:00Z\n";

        $response = $this->withHeaders(['X-Api-Key' => self::API_KEY])
            ->post('/api/statement-imports', ['statement' => $this->csvFile($csv)]);

        $response->assertStatus(202);
        $importId = $response->json('data.id') ?? $response->json('id');

        $import = StatementImport::findOrFail($importId);

        $this->assertSame('completed', $import->status);
        $this->assertSame(5, $import->total_rows);
        $this->assertSame(1, $import->matched_rows);
        $this->assertSame(1, $import->duplicate_rows);
        $this->assertSame(1, $import->error_rows);

        $this->assertSame(5, Divergence::count());
        $this->assertSame(1, Divergence::where('type', DivergenceType::Mismatch->value)->count());
        $this->assertSame(1, Divergence::where('type', DivergenceType::MissingInLedger->value)->count());
        $this->assertSame(1, Divergence::where('type', DivergenceType::DuplicateInFile->value)->count());
        $this->assertSame(1, Divergence::where('type', DivergenceType::InvalidRow->value)->count());
        $this->assertSame(1, Divergence::where('type', DivergenceType::MissingInStatement->value)->count());

        $absent = Divergence::where('type', DivergenceType::MissingInStatement->value)->first();
        $this->assertSame('TXN-ABSENT', $absent->reference);
    }

    #[Test]
    public function reimporting_the_same_file_is_idempotent(): void
    {
        Transaction::factory()->create([
            'reference' => 'TXN-1',
            'amount_cents' => 500,
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => '2026-11-01T10:00:00Z',
        ]);

        $csv = "reference,amount_cents,currency,status,occurred_at\n".
            "TXN-1,500,BRL,settled,2026-11-01T10:00:00Z\n".
            "TXN-UNKNOWN,100,BRL,settled,2026-11-01T11:00:00Z\n";

        $first = $this->withHeaders(['X-Api-Key' => self::API_KEY])
            ->post('/api/statement-imports', ['statement' => $this->csvFile($csv, 'extract.csv')]);

        $first->assertStatus(202);
        $firstId = $first->json('data.id') ?? $first->json('id');

        $this->assertSame(1, StatementImport::count());
        $divergenceCountAfterFirst = Divergence::count();

        $second = $this->withHeaders(['X-Api-Key' => self::API_KEY])
            ->post('/api/statement-imports', ['statement' => $this->csvFile($csv, 'extract-copy.csv')]);

        $second->assertStatus(200);
        $secondId = $second->json('data.id') ?? $second->json('id');

        $this->assertSame($firstId, $secondId);
        $this->assertSame(1, StatementImport::count());
        $this->assertSame($divergenceCountAfterFirst, Divergence::count());
    }

    #[Test]
    public function a_file_with_an_internal_duplicate_flags_it_without_double_counting_matches(): void
    {
        Transaction::factory()->create([
            'reference' => 'TXN-DUP',
            'amount_cents' => 750,
            'currency' => 'BRL',
            'status' => 'settled',
            'occurred_at' => '2026-11-01T10:00:00Z',
        ]);

        $csv = "reference,amount_cents,currency,status,occurred_at\n".
            "TXN-DUP,750,BRL,settled,2026-11-01T10:00:00Z\n".
            "TXN-DUP,750,BRL,settled,2026-11-01T10:00:00Z\n".
            "TXN-DUP,750,BRL,settled,2026-11-01T10:00:00Z\n";

        $response = $this->withHeaders(['X-Api-Key' => self::API_KEY])
            ->post('/api/statement-imports', ['statement' => $this->csvFile($csv)]);

        $importId = $response->json('data.id') ?? $response->json('id');
        $import = StatementImport::findOrFail($importId);

        $this->assertSame(3, $import->total_rows);
        $this->assertSame(1, $import->matched_rows);
        $this->assertSame(2, $import->duplicate_rows);
        $this->assertSame(2, Divergence::where('type', DivergenceType::DuplicateInFile->value)->count());
    }

    #[Test]
    public function it_rejects_requests_without_a_valid_api_key(): void
    {
        $csv = "reference,amount_cents,currency,status,occurred_at\nTXN-1,100,BRL,settled,2026-11-01T10:00:00Z\n";

        $response = $this->post('/api/statement-imports', ['statement' => $this->csvFile($csv)]);

        $response->assertStatus(401);
        $this->assertSame(0, StatementImport::count());
    }
}
