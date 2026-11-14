package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.LocationType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.ShopDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.ShopService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;

/** Branches, warehouses and other stock locations. */
@Component
public class LocationImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "Location name", "The branch or store name. Must be new.", "Osu branch"),
            ImportColumn.optional("type", "Type", "What kind of location it is. Leave blank for a shop.", "Shop")
                    .withOptions(Arrays.stream(LocationType.values()).map(Cells::pretty).toList()),
            ImportColumn.optional("address", "Address", "Where it is.", "Oxford Street, Accra"));

    private final BusinessAccessHelper access;
    private final ShopRepository shopRepo;
    private final ShopService shopService;

    public LocationImporter(BusinessAccessHelper access, ShopRepository shopRepo, ShopService shopService) {
        this.access = access;
        this.shopRepo = shopRepo;
        this.shopService = shopService;
    }

    @Override public ImportKind kind() { return ImportKind.LOCATIONS; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireRole(businessId, email, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
    }

    /** Lowercased names of the company's locations. */
    static Map<String, Shop> locationsByName(ShopRepository repo, Business business) {
        Map<String, Shop> m = new HashMap<>();
        repo.findByBusiness(business, Pageable.unpaged()).forEach(s -> {
            if (!Boolean.TRUE.equals(s.getDeleted())) {
                m.put(s.getShopName().trim().toLowerCase(Locale.ROOT), s);
                m.putIfAbsent(s.getShopCode().trim().toLowerCase(Locale.ROOT), s);
            }
        });
        return m;
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        Map<String, Shop> existing = locationsByName(shopRepo, business);
        Set<String> seen = new HashSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String name = c.text("name", 120);
            LocationType type = c.choice("type", LocationType.class, Cells::pretty);
            String address = c.text("address", 255);
            if (name != null) {
                String k = name.toLowerCase(Locale.ROOT);
                if (existing.containsKey(k)) c.error("name", "You already have a location called " + name);
                else if (!seen.add(k)) c.error("name", name + " appears more than once in this file");
            }
            if (!c.ok()) continue;
            ShopDTO dto = new ShopDTO();
            dto.setBusinessId(businessId.intValue());
            dto.setShopName(name);
            dto.setShopLocation(address);
            dto.setLocationType(type != null ? type : LocationType.SHOP);
            dto.setActive(true);
            plan.add(row.number(), r -> {
                shopService.createShop(dto, email);
                r.addCreated();
            });
        }
        return plan;
    }
}
