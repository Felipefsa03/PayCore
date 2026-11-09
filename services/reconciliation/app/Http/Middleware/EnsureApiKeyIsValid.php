<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureApiKeyIsValid
{
    /**
     * @param  Closure(Request): (Response)  $next
     */
    public function handle(Request $request, Closure $next): Response
    {
        $configured = config('reconciliation.api_key');

        if (! is_string($configured) || $configured === '') {
            abort(500, 'RECONCILIATION_API_KEY is not configured.');
        }

        $provided = (string) $request->header('X-Api-Key', '');

        if (! hash_equals($configured, $provided)) {
            abort(401, 'Invalid or missing API key.');
        }

        return $next($request);
    }
}
