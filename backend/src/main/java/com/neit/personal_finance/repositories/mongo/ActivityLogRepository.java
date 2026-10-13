package com.neit.personal_finance.repositories.mongo;


import com.neit.personal_finance.documents.ActivityLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {

    List<ActivityLog> findByUserId(String userId);
}
