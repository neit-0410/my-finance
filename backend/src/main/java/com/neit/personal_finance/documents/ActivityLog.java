package com.neit.personal_finance.documents;

import lombok.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    private String id;

    private String userId;
    private String action;

    private Map<String, Object> details;

    private LocalDateTime timestamp = LocalDateTime.now();
}
