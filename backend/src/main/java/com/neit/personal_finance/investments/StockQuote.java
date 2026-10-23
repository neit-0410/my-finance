package com.neit.personal_finance.investments;

import com.neit.personal_finance.entities.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_quotes", uniqueConstraints = @UniqueConstraint(name = "uk_stock_quote_owner_symbol", columnNames = {"user_id", "symbol"}))
@Getter @Setter @NoArgsConstructor
public class StockQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 12)
    private String symbol;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    @Column(nullable = false)
    private Instant updatedAt;
}
