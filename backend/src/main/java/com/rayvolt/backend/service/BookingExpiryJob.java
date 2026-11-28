package com.rayvolt.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "rayvolt.bookings.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryJob {
    private static final Logger LOG = LoggerFactory.getLogger(BookingExpiryJob.class);
    private final BookingService bookings;

    public BookingExpiryJob(BookingService bookings) { this.bookings = bookings; }

    @Scheduled(fixedDelayString = "${rayvolt.bookings.expiry-interval:1000}")
    public void expireBookings() {
        for (Long scooterId : bookings.expiredScooters()) {
            try {
                bookings.expire(scooterId);
            } catch (RuntimeException exception) {
                LOG.warn("Could not expire booking for scooter {}: {}", scooterId, exception.getMessage());
            }
        }
    }
}
