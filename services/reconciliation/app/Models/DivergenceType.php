<?php

namespace App\Models;

enum DivergenceType: string
{
    case DuplicateInFile = 'duplicate_in_file';
    case Mismatch = 'mismatch';
    case MissingInLedger = 'missing_in_ledger';
    case MissingInStatement = 'missing_in_statement';
    case InvalidRow = 'invalid_row';
}
