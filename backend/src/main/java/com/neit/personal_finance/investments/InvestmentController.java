package com.neit.personal_finance.investments;

import com.neit.personal_finance.idempotency.IdempotencyService;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.neit.personal_finance.investments.InvestmentDto.*;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {
    private final InvestmentService service;
    private final IdempotencyService idempotency;
    @GetMapping public Portfolio portfolio() { return service.portfolio(); }
    @PostMapping("/trades") public ResponseEntity<String> trade(@Valid @RequestBody TradeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(key, "POST /api/investments/trades", request, () -> service.trade(request));
    }
    @PostMapping("/prices") public ResponseEntity<String> price(@Valid @RequestBody PriceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(key, "POST /api/investments/prices", request, () -> service.price(request));
    }
}
