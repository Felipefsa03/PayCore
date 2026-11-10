<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Divergence;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

class DivergenceController extends Controller
{
    public function report(Request $request): JsonResponse
    {
        $dates = $request->validate([
            'from' => ['required', 'date_format:Y-m-d'],
            'to' => ['required', 'date_format:Y-m-d', 'after_or_equal:from'],
        ]);
        $query = Divergence::where('created_at', '>=', CarbonImmutable::parse($dates['from'], 'UTC')->startOfDay())
            ->where('created_at', '<', CarbonImmutable::parse($dates['to'], 'UTC')->addDay()->startOfDay());

        return response()->json([
            'by_status' => (clone $query)->selectRaw('status, count(*) as total')->groupBy('status')->pluck('total', 'status'),
            'divergences' => $query->orderBy('id')->paginate(50)->withQueryString(),
        ]);
    }

    public function history(Divergence $divergence): JsonResponse
    {
        return response()->json(DB::table('divergence_decisions')
            ->where('divergence_id', $divergence->id)->orderBy('id')->get());
    }

    public function decide(Request $request, Divergence $divergence): JsonResponse
    {
        $decision = $request->validate([
            'status' => ['required', Rule::in(['investigating', 'resolved'])],
            'operator_reference' => ['required', 'string', 'max:100'],
            'reason' => ['required', 'string', 'max:2000'],
        ]);
        $previous = $decision['status'] === 'investigating' ? 'open' : 'investigating';

        return DB::transaction(function () use ($divergence, $decision, $previous) {
            $changed = Divergence::whereKey($divergence->id)->where('status', $previous)
                ->update(['status' => $decision['status']]);
            abort_unless($changed === 1, 409, 'Invalid divergence transition.');
            $id = DB::table('divergence_decisions')->insertGetId([
                ...$decision,
                'divergence_id' => $divergence->id,
                'created_at' => now(),
            ]);

            return response()->json(['id' => $id, 'status' => $decision['status']], 201);
        });
    }
}
