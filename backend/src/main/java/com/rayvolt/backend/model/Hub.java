package com.rayvolt.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hubs")
public class Hub {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int capacity;
    private Double lat;
    private Double lon;

    protected Hub() {
    }

    public Hub(Long id, String name, int capacity, Double lat, Double lon) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }
}
