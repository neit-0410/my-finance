package com.neit.personal_finance.investments;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.neit.personal_finance.investments.InvestmentDto.*;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {
    private final InvestmentService service;
    @GetMapping public Portfolio portfolio() { return service.portfolio(); }
    @PostMapping("/trades") public Portfolio trade(@Valid @RequestBody TradeRequest request) { return service.trade(request); }
    @PostMapping("/prices") public Portfolio price(@Valid @RequestBody PriceRequest request) { return service.price(request); }
}
