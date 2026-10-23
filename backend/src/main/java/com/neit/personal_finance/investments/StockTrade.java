package com.neit.personal_finance.investments;

import com.neit.personal_finance.entities.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "stock_trades", indexes = @Index(name = "idx_stock_trade_owner_date", columnList = "user_id,trade_date,id"))
@Getter @Setter @NoArgsConstructor
public class StockTrade {
    public enum Side { BUY, SELL }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 12)
    private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 4)
    private Side side;
    @Column(nullable = false)
    private long quantity;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee;
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
}
