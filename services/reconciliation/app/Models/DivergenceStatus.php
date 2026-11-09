<?php

namespace App\Models;

enum DivergenceStatus: string
{
    case Open = 'open';
    case Resolved = 'resolved';
}
