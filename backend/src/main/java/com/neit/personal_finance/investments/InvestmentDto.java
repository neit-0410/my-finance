package com.neit.personal_finance.investments;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InvestmentDto {
    private InvestmentDto() {}
    public record TradeRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9.\\-]{0,11}") String symbol,
            @NotNull StockTrade.Side side,
            @NotNull @Positive @Max(1000000000) Long quantity,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal price,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 4) BigDecimal fee,
            @NotNull @PastOrPresent LocalDate tradeDate) {}
    public record PriceRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9.\\-]{0,11}") String symbol,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal price) {}
    public record TradeView(Long id, String symbol, StockTrade.Side side, long quantity,
                            BigDecimal price, BigDecimal fee, LocalDate tradeDate, BigDecimal realizedPnl) {}
    public record Position(String symbol, long quantity, BigDecimal averageCost, BigDecimal costBasis,
                           BigDecimal currentPrice, Instant priceUpdatedAt, BigDecimal marketValue,
                           BigDecimal unrealizedPnl, BigDecimal realizedPnl) {}
    public record Portfolio(BigDecimal costBasis, BigDecimal marketValue, BigDecimal unrealizedPnl,
                            BigDecimal realizedPnl, int missingPrices,
                            List<Position> positions, List<TradeView> trades) {}
}
