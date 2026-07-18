package com.swp391.api.modules.reservation.service;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTableStatusService {

    private static final Logger log = LoggerFactory.getLogger(ReservationTableStatusService.class);

    public static final int BUFFER_BEFORE_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    public ReservationTableStatusService(ReservationRepository reservationRepository,
                                         TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    @Transactional
    public int reserveTablesForUpcomingConfirmedReservations() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime bufferLimit = now.plusMinutes(BUFFER_BEFORE_MINUTES);

        List<Reservation> reservations =
                reservationRepository.findConfirmedReservationsWithTablesInWindow(today, now, bufferLimit);

        List<RestaurantTable> availableTables = tableRepository.findAvailableActiveTablesOrderByCapacityAsc();
        Set<RestaurantTable> tablesToReserve = new LinkedHashSet<>();
        int updatedReservations = 0;

        for (Reservation reservation : reservations) {
            List<RestaurantTable> assignedTables = reservation.getTables();
            if (assignedTables == null || assignedTables.isEmpty()) {
                assignedTables = selectTablesForReservation(reservation, availableTables);
                if (assignedTables.isEmpty()) {
                    log.warn("Cannot auto-reserve tables for reservation {} at {} {}: no available capacity for {} guest(s)",
                            reservation.getReservationId(),
                            reservation.getReservationDate(),
                            reservation.getReservationTime(),
                            reservation.getNumberOfGuests());
                    continue;
                }
                reservation.setTables(assignedTables);
                if (assignedTables.size() == 1) {
                    reservation.setTableId(assignedTables.get(0).getId());
                }
                updatedReservations++;
            }

            for (RestaurantTable table : assignedTables) {
                if (table.getStatus() == RestaurantTable.TableStatus.AVAILABLE) {
                    table.setStatus(RestaurantTable.TableStatus.RESERVED);
                    tablesToReserve.add(table);
                    availableTables.remove(table);
                }
            }
        }

        if (tablesToReserve.isEmpty()) {
            return 0;
        }

        tableRepository.saveAll(tablesToReserve);
        if (updatedReservations > 0) {
            reservationRepository.saveAll(reservations);
        }
        log.info("Auto-reserved {} table(s) for {} confirmed reservation(s) between {} and {}",
                tablesToReserve.size(), reservations.size(), now, bufferLimit);
        return tablesToReserve.size();
    }

    private List<RestaurantTable> selectTablesForReservation(Reservation reservation,
                                                             List<RestaurantTable> availableTables) {
        Integer requestedGuests = reservation.getNumberOfGuests();
        if (requestedGuests == null || requestedGuests <= 0) {
            return List.of();
        }

        for (RestaurantTable table : availableTables) {
            if (table.getCapacity() != null && table.getCapacity() >= requestedGuests) {
                return List.of(table);
            }
        }

        List<RestaurantTable> selectedTables = new ArrayList<>();
        int selectedCapacity = 0;
        for (RestaurantTable table : availableTables) {
            if (table.getCapacity() == null) {
                continue;
            }
            selectedTables.add(table);
            selectedCapacity += table.getCapacity();
            if (selectedCapacity >= requestedGuests) {
                return selectedTables;
            }
        }

        return List.of();
    }
}
