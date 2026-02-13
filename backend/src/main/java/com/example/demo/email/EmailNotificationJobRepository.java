package com.example.demo.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailNotificationJobRepository extends JpaRepository<EmailNotificationJob, Long> {

    @Query("""
            select j
            from EmailNotificationJob j
            where j.status in :statuses
              and (j.nextRetryAt is null or j.nextRetryAt <= :now)
            order by j.createdAt asc
            """)
    List<EmailNotificationJob> findDispatchableJobs(
            @Param("statuses") List<EmailJobStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
