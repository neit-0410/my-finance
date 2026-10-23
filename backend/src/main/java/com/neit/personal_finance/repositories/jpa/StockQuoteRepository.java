package com.neit.personal_finance.repositories.jpa;
import com.neit.personal_finance.investments.StockQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface StockQuoteRepository extends JpaRepository<StockQuote, Long> {
    List<StockQuote> findByUserId(long userId);
    Optional<StockQuote> findByUserIdAndSymbol(long userId, String symbol);
}
