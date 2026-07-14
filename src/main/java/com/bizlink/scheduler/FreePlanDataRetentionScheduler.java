package com.bizlink.scheduler;

import com.bizlink.service.FreePlanDataRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FreePlanDataRetentionScheduler {

    private final FreePlanDataRetentionService retentionService;

    public FreePlanDataRetentionScheduler(FreePlanDataRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    /** Daily 3:15 AM — purge free-plan data older than 7 days. */
    @Scheduled(cron = "${app.free-plan-retention-cron:0 15 3 * * *}")
    public void purgeOldFreePlanData() {
        log.info("Running free-plan data retention purge");
        int deletedOrders = retentionService.purgeAllFreeBusinesses();
        log.info("Free-plan retention finished — deleted {} orders", deletedOrders);
    }
}
