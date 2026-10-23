package com.neit.personal_finance.investments;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.*;
import com.neit.personal_finance.sercurity.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import static com.neit.personal_finance.investments.InvestmentDto.*;

@Service
@RequiredArgsConstructor
public class InvestmentService {
    private final StockTradeRepository trades;
    private final StockQuoteRepository quotes;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Transactional(readOnly = true)
    public Portfolio portfolio() { return snapshot(currentUser.get().getId()); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Portfolio trade(TradeRequest request) {
        User owner = lockedOwner();
        StockTrade trade = new StockTrade();
        trade.setUser(owner);
        trade.setSymbol(normalize(request.symbol()));
        trade.setSide(request.side());
        trade.setQuantity(request.quantity());
        trade.setPrice(request.price());
        trade.setFee(request.fee());
        trade.setTradeDate(request.tradeDate());
        trades.saveAndFlush(trade);
        // Replay validates all dates, including backdated trades. Any invalid sale rolls back this insert.
        return snapshot(owner.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Portfolio price(PriceRequest request) {
        User owner = lockedOwner();
        String symbol = normalize(request.symbol());
        if (trades.findByUserIdOrderByTradeDateAscIdAsc(owner.getId()).stream().noneMatch(t -> t.getSymbol().equals(symbol))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã cổ phiếu chưa có trong danh mục.");
        }
        StockQuote quote = quotes.findByUserIdAndSymbol(owner.getId(), symbol).orElseGet(StockQuote::new);
        quote.setUser(owner);
        quote.setSymbol(symbol);
        quote.setPrice(request.price());
        quote.setUpdatedAt(Instant.now());
        quotes.saveAndFlush(quote);
        return snapshot(owner.getId());
    }

    private User lockedOwner() {
        return users.lockById(currentUser.get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
    private String normalize(String symbol) { return symbol.toUpperCase(Locale.ROOT); }
    private BigDecimal rounded(BigDecimal amount) { return amount.setScale(4, RoundingMode.HALF_UP); }

    private static class Holding {
        long quantity;
        BigDecimal cost = ZERO;
        BigDecimal realized = ZERO;
    }

    private Portfolio snapshot(long userId) {
        Map<String, Holding> holdings = new TreeMap<>();
        List<TradeView> history = new ArrayList<>();
        for (StockTrade trade : trades.findByUserIdOrderByTradeDateAscIdAsc(userId)) {
            Holding h = holdings.computeIfAbsent(trade.getSymbol(), key -> new Holding());
            BigDecimal value = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            BigDecimal realized = ZERO;
            if (trade.getSide() == StockTrade.Side.BUY) {
                h.quantity = Math.addExact(h.quantity, trade.getQuantity());
                h.cost = h.cost.add(value).add(trade.getFee());
            } else {
                if (trade.getQuantity() > h.quantity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Không đủ cổ phiếu " + trade.getSymbol() + " tại ngày giao dịch. Kiểm tra số lượng và thứ tự ngày mua/bán.");
                }
                BigDecimal soldCost = trade.getQuantity() == h.quantity ? h.cost :
                        h.cost.multiply(BigDecimal.valueOf(trade.getQuantity())).divide(BigDecimal.valueOf(h.quantity), 12, RoundingMode.HALF_UP);
                realized = value.subtract(trade.getFee()).subtract(soldCost);
                h.cost = h.cost.subtract(soldCost);
                h.quantity -= trade.getQuantity();
                h.realized = h.realized.add(realized);
            }
            history.add(new TradeView(trade.getId(), trade.getSymbol(), trade.getSide(), trade.getQuantity(),
                    trade.getPrice(), trade.getFee(), trade.getTradeDate(), rounded(realized)));
        }
        Map<String, StockQuote> prices = new HashMap<>();
        quotes.findByUserId(userId).forEach(q -> prices.put(q.getSymbol(), q));
        List<Position> positions = new ArrayList<>();
        BigDecimal cost = ZERO, market = ZERO, unrealized = ZERO, realized = ZERO;
        int missing = 0;
        for (var entry : holdings.entrySet()) {
            String symbol = entry.getKey(); Holding h = entry.getValue(); StockQuote quote = prices.get(symbol);
            BigDecimal price = quote == null ? null : quote.getPrice();
            BigDecimal value = h.quantity == 0 ? ZERO : price == null ? null : price.multiply(BigDecimal.valueOf(h.quantity));
            BigDecimal pnl = value == null ? null : value.subtract(h.cost);
            if (value == null) missing++;
            else { market = market.add(value); unrealized = unrealized.add(pnl); }
            cost = cost.add(h.cost); realized = realized.add(h.realized);
            positions.add(new Position(symbol, h.quantity,
                    h.quantity == 0 ? ZERO : h.cost.divide(BigDecimal.valueOf(h.quantity), 4, RoundingMode.HALF_UP),
                    rounded(h.cost), price, quote == null ? null : quote.getUpdatedAt(),
                    value == null ? null : rounded(value), pnl == null ? null : rounded(pnl), rounded(h.realized)));
        }
        Collections.reverse(history);
        return new Portfolio(rounded(cost), missing == 0 ? rounded(market) : null,
                missing == 0 ? rounded(unrealized) : null, rounded(realized), missing, positions, history);
    }
}
