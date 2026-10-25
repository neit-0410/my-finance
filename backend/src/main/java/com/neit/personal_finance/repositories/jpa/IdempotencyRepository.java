package com.neit.personal_finance.repositories.jpa;

import com.neit.personal_finance.entities.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByUser_IdAndKeyHash(long userId, String keyHash);
    void deleteByUser_Id(long userId);
}
