package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.Hub;
import java.util.List;
import java.util.Optional;

// Locking and write methods require a service transaction.
// Lock the scooter first, then hubs in numeric ID order before checking capacity or saving.
// Booking/trip writers must follow the same protocol.
public interface FleetRepository {
    List<Scooter> findAll();
    List<Scooter> findAvailable(Long hubId, String type, int minimumBatteryPercent);
    List<Hub> findHubs();
    Optional<Scooter> findById(Long id);
    Optional<Scooter> findByIdForUpdate(Long id);
    void lockHubs(Long... hubIds);
    Scooter save(Scooter scooter);
    boolean hasSpace(Long hubId);
}
