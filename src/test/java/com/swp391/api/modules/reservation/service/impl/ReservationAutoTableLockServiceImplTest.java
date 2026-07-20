package com.swp391.api.modules.reservation.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swp391.api.modules.table.entity.RestaurantTable;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationAutoTableLockServiceImplTest {

    @Test
    void findBestTableCombinationPrefersSingleTableThatFitsGuests() throws Exception {
        ReservationAutoTableLockServiceImpl service = new ReservationAutoTableLockServiceImpl(null, null);
        List<RestaurantTable> availableTables = List.of(
                table(1L, 2),
                table(2L, 4),
                table(3L, 8)
        );

        Method method = ReservationAutoTableLockServiceImpl.class.getDeclaredMethod(
                "findBestTableCombination",
                List.class,
                Integer.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RestaurantTable> selectedTables = (List<RestaurantTable>) method.invoke(
                service,
                availableTables,
                5
        );

        assertEquals(List.of(3L), selectedTables.stream().map(RestaurantTable::getId).toList());
    }

    private RestaurantTable table(Long id, Integer capacity) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setCapacity(capacity);
        return table;
    }
}
