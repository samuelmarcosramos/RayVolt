package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.Scooter;
import java.util.List;
import java.util.Optional;

// Locking and write methods require a service transaction.
// Lock the scooter first, then hubs in numeric ID order before checking capacity or saving.
// Booking/trip writers must follow the same protocol.
public interface FleetRepository {
    List<Scooter> findAll();
    Optional<Scooter> findById(Long id);
    Optional<Scooter> findByIdForUpdate(Long id);
    void lockHubs(Long... hubIds);
    Scooter save(Scooter scooter);
    boolean hasSpace(Long hubId);
}
