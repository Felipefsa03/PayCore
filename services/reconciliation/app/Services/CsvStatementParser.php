<?php

namespace App\Services;

use Generator;
use RuntimeException;
use SplFileObject;

/**
 * Streams a bank statement CSV one row at a time so large files never load
 * fully into memory. Expected header: reference,amount_cents,currency,status,occurred_at
 */
class CsvStatementParser
{
    /** @var list<string> */
    private const REQUIRED_COLUMNS = ['reference', 'amount_cents', 'currency', 'status', 'occurred_at'];

    /**
     * @return Generator<int, array{line: int, raw: string, fields: array<string, string>|null, error: ?string}>
     */
    public function parse(string $absolutePath): Generator
    {
        $file = new SplFileObject($absolutePath, 'r');
        $file->setFlags(SplFileObject::READ_CSV | SplFileObject::SKIP_EMPTY | SplFileObject::DROP_NEW_LINE);
        $file->setCsvControl(escape: '\\');

        $header = $file->fgetcsv();

        if ($header === false || $header === null) {
            throw new RuntimeException('Statement file is empty.');
        }

        $header = array_map(fn ($column) => is_string($column) ? trim($column) : $column, $header);

        $missing = array_diff(self::REQUIRED_COLUMNS, $header);

        if ($missing !== []) {
            throw new RuntimeException('Missing required columns: '.implode(', ', $missing));
        }

        $lineNumber = 1;

        while (! $file->eof()) {
            $row = $file->fgetcsv();
            $lineNumber++;

            if ($row === false || $row === null || $row === [null]) {
                continue;
            }

            $raw = implode(',', array_map(fn ($value) => (string) $value, $row));

            if (count($row) !== count($header)) {
                yield [
                    'line' => $lineNumber,
                    'raw' => $raw,
                    'fields' => null,
                    'error' => sprintf('Expected %d columns, got %d.', count($header), count($row)),
                ];

                continue;
            }

            /** @var array<string, string> $fields */
            $fields = array_combine($header, $row);

            yield [
                'line' => $lineNumber,
                'raw' => $raw,
                'fields' => $fields,
                'error' => null,
            ];
        }
    }
}
