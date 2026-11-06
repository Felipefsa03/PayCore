package com.paycore.financialcore.app.api.accounts;

import java.util.List;

/** Página de lançamentos, mais recentes primeiro (contrato PostingPage). */
public record PostingPageResponse(List<PostingResponse> items, String nextCursor) {}
