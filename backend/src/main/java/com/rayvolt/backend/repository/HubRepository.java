package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.Hub;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface HubRepository extends JpaRepository<Hub, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hub h WHERE h.id = :id")
    Optional<Hub> findByIdForUpdate(Long id);
}
