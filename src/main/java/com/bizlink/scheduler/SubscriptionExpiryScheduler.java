package com.bizlink.scheduler;

import com.bizlink.model.Subscription;
import com.bizlink.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionExpiryScheduler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Marks ACTIVE subscriptions past end_date as EXPIRED. Runs hourly by default. */
    @Scheduled(cron = "${app.subscription-expiry-cron:0 0 * * * *}")
    @Transactional
    public void expireSubscriptions() {
        LocalDate today = LocalDate.now();
        List<Subscription> expired = subscriptionRepository.findByStatusAndEndDateBefore("ACTIVE", today);
        if (expired.isEmpty()) {
            return;
        }
        for (Subscription sub : expired) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            log.info("Auto-expired subscription {} for business {} (ended {})",
                    sub.getId(), sub.getBusinessId(), sub.getEndDate());
        }
        log.info("Expired {} subscription(s)", expired.size());
    }
}
