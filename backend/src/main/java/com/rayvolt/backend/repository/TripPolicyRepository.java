package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.RiderCategory;
import com.rayvolt.backend.model.TripPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPolicyRepository extends JpaRepository<TripPolicy, RiderCategory> { }
