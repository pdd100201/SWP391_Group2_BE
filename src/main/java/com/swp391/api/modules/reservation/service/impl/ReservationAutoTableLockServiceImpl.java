package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationAutoTableLockService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationAutoTableLockServiceImpl implements ReservationAutoTableLockService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAutoTableLockServiceImpl.class);
    private static final int AUTO_LOCK_BEFORE_MINUTES = 45;
    private static final int AUTO_LOCK_AFTER_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    public ReservationAutoTableLockServiceImpl(ReservationRepository reservationRepository,
                                               TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    @Override
    @Transactional
    public void lockTablesForUpcomingReservations() {
        LocalTime now = LocalTime.now();
        LocalTime fromTime = now.minusMinutes(AUTO_LOCK_AFTER_MINUTES);
        LocalTime toTime = now.plusMinutes(AUTO_LOCK_BEFORE_MINUTES);
        if (toTime.isBefore(now)) {
            toTime = LocalTime.MAX;
        }

        List<Reservation> reservations = reservationRepository.findConfirmedReservationsWithTablesInWindow(
                LocalDate.now(),
                fromTime,
                toTime
        );

        List<RestaurantTable> availableTables = tableRepository.findAvailableActiveTablesOrderByCapacityAsc();
        boolean changed = false;

        for (Reservation reservation : reservations) {
            if (hasAssignedTables(reservation)) {
                continue;
            }

            List<RestaurantTable> selectedTables = findBestTableCombination(
                    availableTables,
                    reservation.getNumberOfGuests()
            );

            if (selectedTables.isEmpty()) {
                log.warn("No available table combination can fit reservation {} for {} guests",
                        reservation.getReservationId(),
                        reservation.getNumberOfGuests());
                continue;
            }

            reservation.setTables(selectedTables);
            reservation.setTableId(selectedTables.get(0).getId());
            selectedTables.forEach(table -> table.setStatus(RestaurantTable.TableStatus.RESERVED));
            availableTables.removeAll(selectedTables);
            changed = true;
        }

        if (changed) {
            reservationRepository.saveAll(reservations);
            tableRepository.saveAll(tableRepository.findAllById(
                    reservations.stream()
                            .flatMap(reservation -> reservation.getTables().stream())
                            .map(RestaurantTable::getId)
                            .distinct()
                            .toList()
            ));
        }
    }

    private boolean hasAssignedTables(Reservation reservation) {
        return reservation.getTableId() != null
                || (reservation.getTables() != null && !reservation.getTables().isEmpty());
    }

    private List<RestaurantTable> findBestTableCombination(List<RestaurantTable> availableTables,
                                                           Integer requestedGuests) {
        if (requestedGuests == null || requestedGuests <= 0 || availableTables.isEmpty()) {
            return List.of();
        }

        List<RestaurantTable> best = new ArrayList<>();
        findBestTableCombination(availableTables, requestedGuests, 0, new ArrayList<>(), best);
        return best;
    }

    private void findBestTableCombination(List<RestaurantTable> availableTables,
                                          int requestedGuests,
                                          int startIndex,
                                          List<RestaurantTable> current,
                                          List<RestaurantTable> best) {
        int currentCapacity = totalCapacity(current);
        if (currentCapacity >= requestedGuests) {
            if (isBetterCombination(current, best, requestedGuests)) {
                best.clear();
                best.addAll(current);
            }
            return;
        }

        for (int index = startIndex; index < availableTables.size(); index++) {
            current.add(availableTables.get(index));
            findBestTableCombination(availableTables, requestedGuests, index + 1, current, best);
            current.remove(current.size() - 1);
        }
    }

    private boolean isBetterCombination(List<RestaurantTable> current,
                                        List<RestaurantTable> best,
                                        int requestedGuests) {
        if (best.isEmpty()) {
            return true;
        }

        int currentCapacity = totalCapacity(current);
        int bestCapacity = totalCapacity(best);
        if (currentCapacity != bestCapacity) {
            return currentCapacity < bestCapacity;
        }

        if (current.size() != best.size()) {
            return current.size() < best.size();
        }

        Long currentFirstId = current.stream().map(RestaurantTable::getId).min(Comparator.naturalOrder()).orElse(Long.MAX_VALUE);
        Long bestFirstId = best.stream().map(RestaurantTable::getId).min(Comparator.naturalOrder()).orElse(Long.MAX_VALUE);
        return currentFirstId < bestFirstId;
    }

    private int totalCapacity(List<RestaurantTable> tables) {
        return tables.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
    }
}
