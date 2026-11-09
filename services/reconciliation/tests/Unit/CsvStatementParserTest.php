<?php

namespace Tests\Unit;

use App\Services\CsvStatementParser;
use PHPUnit\Framework\Attributes\Test;
use RuntimeException;
use Tests\TestCase;

class CsvStatementParserTest extends TestCase
{
    private function writeCsv(string $contents): string
    {
        $path = tempnam(sys_get_temp_dir(), 'statement_').'.csv';
        file_put_contents($path, $contents);

        return $path;
    }

    #[Test]
    public function it_parses_well_formed_rows(): void
    {
        $path = $this->writeCsv(
            "reference,amount_cents,currency,status,occurred_at\n".
            "TXN-1,1000,BRL,settled,2026-11-01T10:00:00Z\n"
        );

        $rows = iterator_to_array((new CsvStatementParser)->parse($path));

        $this->assertCount(1, $rows);
        $this->assertNull($rows[0]['error']);
        $this->assertSame('TXN-1', $rows[0]['fields']['reference']);

        unlink($path);
    }

    #[Test]
    public function it_flags_rows_with_the_wrong_column_count(): void
    {
        $path = $this->writeCsv(
            "reference,amount_cents,currency,status,occurred_at\n".
            "TXN-1,1000,BRL\n"
        );

        $rows = iterator_to_array((new CsvStatementParser)->parse($path));

        $this->assertCount(1, $rows);
        $this->assertNull($rows[0]['fields']);
        $this->assertNotNull($rows[0]['error']);

        unlink($path);
    }

    #[Test]
    public function it_rejects_a_file_missing_required_columns(): void
    {
        $path = $this->writeCsv("reference,amount_cents\nTXN-1,1000\n");

        $this->expectException(RuntimeException::class);

        iterator_to_array((new CsvStatementParser)->parse($path));

        unlink($path);
    }
}
