package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.DTO.CategoryDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.CategoryRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.CategoryService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Product categories, optionally inside a parent category. A parent can be an existing category
 * or one higher up in the same file.
 */
@Component
public class CategoryImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "Category name", "Must be new.", "Soft drinks"),
            ImportColumn.optional("parent", "Parent category",
                    "Leave empty for a main category. Otherwise an existing category, or one on an earlier row.", "Drinks"));

    private final BusinessAccessHelper access;
    private final CategoryRepository categoryRepo;
    private final CategoryService categoryService;

    public CategoryImporter(BusinessAccessHelper access, CategoryRepository categoryRepo, CategoryService categoryService) {
        this.access = access;
        this.categoryRepo = categoryRepo;
        this.categoryService = categoryService;
    }

    @Override public ImportKind kind() { return ImportKind.CATEGORIES; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of("Put main categories before the ones inside them (e.g. Drinks before Soft drinks).");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_CATALOG);
    }

    private static String key(String s) { return s.trim().toLowerCase(Locale.ROOT); }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        // Name -> id of what exists; ids of the file's categories are added as they get saved.
        Map<String, Long> ids = new HashMap<>();
        categoryRepo.findByBusinessAndDeletedFalse(business).forEach(cat -> ids.putIfAbsent(key(cat.getCategoryName()), cat.getId()));
        Set<String> existing = new HashSet<>(ids.keySet());
        Set<String> inFile = new HashSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String name = c.text("name", 120);
            String parent = c.text("parent", 120);
            if (name != null) {
                String k = key(name);
                if (existing.contains(k)) c.error("name", "You already have a category called " + name);
                else if (!inFile.add(k)) c.error("name", name + " appears more than once in this file");
                if (parent != null && key(parent).equals(k)) c.error("parent", "A category can't be inside itself");
            }
            if (parent != null && !existing.contains(key(parent)) && !inFile.contains(key(parent))) {
                c.error("parent", "No category called " + parent + " (add it on an earlier row, or create it first)");
            }
            if (!c.ok()) continue;
            String n = name.trim();
            String pk = parent == null ? null : key(parent);
            plan.add(row.number(), r -> {
                CategoryDTO dto = new CategoryDTO();
                dto.setCategoryName(n);
                dto.setParentId(pk == null ? null : ids.get(pk));
                ids.put(key(n), categoryService.createCategory(businessId, dto, email).getId());
                r.addCreated();
            });
        }
        return plan;
    }
}
