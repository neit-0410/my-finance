package com.neit.personal_finance.repositories.jpa;
import com.neit.personal_finance.investments.StockTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StockTradeRepository extends JpaRepository<StockTrade, Long> {
    List<StockTrade> findByUserIdOrderByTradeDateAscIdAsc(long userId);
}
