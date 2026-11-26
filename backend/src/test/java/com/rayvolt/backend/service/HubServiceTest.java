package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("PT-33")
class HubServiceTest {
    @Mock HubRepository hubs;
    @Mock ScooterRepository scooters;
    @InjectMocks HubService service;

    private HubDetails details(int capacity) { return new HubDetails("Gate", "Security", capacity, 40.6, -8.7); }

    @Test
    void addsAllFieldsAndTrimsNames() {
        when(hubs.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        Hub saved = service.add(new HubDetails(" Gate ", " Security ", 1, 90.0, -180.0));
        assertThat(saved.getName()).isEqualTo("Gate");
        assertThat(saved.getDepartment()).isEqualTo("Security");
        assertThat(saved.getCapacity()).isEqualTo(1);
        assertThat(saved.getLat()).isEqualTo(90.0);
        assertThat(saved.getLon()).isEqualTo(-180.0);
    }

    @Test
    void rejectsDuplicateNameBeforeSaving() {
        when(hubs.existsByName("Gate")).thenReturn(true);
        assertThatThrownBy(() -> service.add(details(2))).isInstanceOf(ConflictException.class);
        verify(hubs, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateNameReturnsConflict() {
        when(hubs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("hubs_name_unique"));
        assertThatThrownBy(() -> service.add(details(2))).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void editChecksBelowEqualAndAboveOccupancy(int capacity) {
        Hub hub = new Hub(1L, "Gate", 4, 0.0, 0.0);
        when(hubs.findByIdForUpdate(1L)).thenReturn(Optional.of(hub));
        when(scooters.countByHubIdAndStatusNot(1L, ScooterStatus.RETIRED)).thenReturn(2L);
        if (capacity < 2) {
            assertThatThrownBy(() -> service.edit(1L, details(capacity))).isInstanceOf(ConflictException.class);
            assertThat(hub.getCapacity()).isEqualTo(4);
            verify(hubs, never()).saveAndFlush(any());
        } else {
            when(hubs.saveAndFlush(hub)).thenReturn(hub);
            assertThat(service.edit(1L, details(capacity)).getCapacity()).isEqualTo(capacity);
        }
    }

    @Test
    void editCannotTakeAnotherHubsName() {
        when(hubs.findByIdForUpdate(1L)).thenReturn(Optional.of(new Hub(details(2))));
        when(hubs.existsByNameAndIdNot("Gate", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.edit(1L, details(2))).isInstanceOf(ConflictException.class);
        verifyNoInteractions(scooters);
    }

    @Test
    void missingHubCannotBeReadOrEdited() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.edit(99L, details(2))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsRepositoryResults() {
        var expected = java.util.List.of(new Hub(details(2)));
        when(hubs.findAllByOrderByNameAsc()).thenReturn(expected);
        assertThat(service.list()).containsExactlyElementsOf(expected);
    }
}
