package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Constants.LocationType;
import com.ultimate.inventory.Constants.UserRoles;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCompanySeederTest {

    private static final String SAMPLE = """
            ADOM FRESH SUPERMARKET - TEST COMPANY LOGINS
            Company    : Adom Fresh Supermarket (code BIZ00010, business id 10)
            Type       : Supermarket (batches, expiry dates, adjustment approvals switched on)

            NAME              ROLE      EMAIL                             PASSWORD      JOB / WORKS AT
            ------------------------------------------------------------------------------------------
            Kwame Asante      ADMIN     kwame.asante@adomfresh.test       Pw@1          Managing Director - All locations
            Kofi Mensah       MANAGER   kofi.mensah@adomfresh.test        Pw@2          Branch Manager, East Legon - East Legon Branch
            Nii Lartey        DRIVER    nii.lartey@adomfresh.test         Pw@3          Delivery Driver - Delivery Van GR 2451-24, Spintex Central Warehouse

            LOCATIONS
            - Spintex Central Warehouse (warehouse)  - Spintex Road, Accra
            - East Legon Branch (shop)               - Lagos Avenue, East Legon
            - Delivery Van GR 2451-24 (vehicle)

            WHAT'S ALREADY IN THE SYSTEM
            - 7 categories, 22 products
            """;

    @Test
    void readsCompanyPeopleAndLocations() {
        TestCompanySeeder.Company c = TestCompanySeeder.parseCompany(SAMPLE);
        assertEquals("Adom Fresh Supermarket", c.name());
        assertEquals(BusinessType.SUPERMARKET, c.type());

        assertEquals(3, c.people().size());
        TestCompanySeeder.Person kwame = c.people().getFirst();
        assertEquals("Kwame", kwame.first());
        assertEquals("Asante", kwame.last());
        assertEquals(UserRoles.ADMIN, kwame.role());
        assertEquals("Pw@1", kwame.password());
        assertEquals("Managing Director", kwame.position());
        assertTrue(kwame.worksAt().isEmpty(), "all locations = no specific assignment");

        TestCompanySeeder.Person kofi = c.people().get(1);
        assertEquals("Branch Manager, East Legon", kofi.position());
        assertEquals(List.of("East Legon Branch"), kofi.worksAt());

        assertEquals(List.of("Delivery Van GR 2451-24", "Spintex Central Warehouse"), c.people().get(2).worksAt());

        assertEquals(3, c.locations().size(), "the list after LOCATIONS ends at the blank line");
        assertEquals(LocationType.WAREHOUSE, c.locations().getFirst().type());
        assertEquals("Spintex Road, Accra", c.locations().getFirst().address());
        assertEquals(LocationType.VEHICLE, c.locations().get(2).type());
        assertNull(c.locations().get(2).address());
    }
}
