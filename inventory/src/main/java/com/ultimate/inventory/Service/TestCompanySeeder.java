package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.DTO.*;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import com.ultimate.inventory.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LOCAL TESTING ONLY. Rebuilds the test company described in TEST_COMPANY_LOGINS.txt and the super
 * admin in SUPER_ADMIN_CREDENTIALS.txt, with the passwords written in those files, so the files
 * keep working against a fresh local database.
 *
 * Off unless {@code app.seed.test-company=true} (set it in the git-ignored local-secrets.properties;
 * never on Render). Runs once: skipped when the company's first person already has an account.
 *
 * Everything goes through the real services (as the people in the file), so permissions, stock,
 * batches and accounting entries are exactly what the app would produce. The company part runs in
 * one transaction: if any step fails nothing of the company is kept, and the app still starts.
 */
@Component
@Order(5) // after the demo seeder, BEFORE SuperAdminBootstrap (10), which would otherwise issue a new password
public class TestCompanySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestCompanySeeder.class);

    private static final List<String> LOGIN_FILES = List.of(
            "TEST_COMPANY_LOGINS.txt", "../TEST_COMPANY_LOGINS.txt",
            "inventory-front/TEST_COMPANY_LOGINS.txt", "../inventory-front/TEST_COMPANY_LOGINS.txt");
    private static final List<String> ADMIN_FILES = List.of(
            "SUPER_ADMIN_CREDENTIALS.txt", "inventory/SUPER_ADMIN_CREDENTIALS.txt");

    @Value("${app.seed.test-company:false}") private boolean enabled;

    private final UserRepository userRepo;
    private final BusinessUserRepo businessUserRepo;
    private final InventoryRepository inventoryRepo;
    private final Security security;
    private final AdminService adminService;
    private final TeamService teamService;
    private final ShopService shopService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final UnitService unitService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final PurchaseService purchaseService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final SaleService saleService;
    private final EmployeeService employeeService;
    private final BusinessAccessHelper accessHelper;
    private final AccountingService accountingService;
    private final TransactionTemplate tx;

    public TestCompanySeeder(UserRepository userRepo, BusinessUserRepo businessUserRepo, InventoryRepository inventoryRepo,
                             Security security, AdminService adminService, TeamService teamService,
                             ShopService shopService, CategoryService categoryService, ProductService productService,
                             UnitService unitService, SupplierService supplierService, CustomerService customerService,
                             PurchaseService purchaseService, PaymentService paymentService,
                             InventoryService inventoryService, SaleService saleService,
                             EmployeeService employeeService, BusinessAccessHelper accessHelper,
                             AccountingService accountingService, PlatformTransactionManager txManager) {
        this.userRepo = userRepo;
        this.businessUserRepo = businessUserRepo;
        this.inventoryRepo = inventoryRepo;
        this.security = security;
        this.adminService = adminService;
        this.teamService = teamService;
        this.shopService = shopService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.unitService = unitService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.purchaseService = purchaseService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.saleService = saleService;
        this.employeeService = employeeService;
        this.accessHelper = accessHelper;
        this.accountingService = accountingService;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── What the files say ───────────────────────────────────────────────────

    record Person(String first, String last, UserRoles role, String email, String password, String position, List<String> worksAt) {}
    record Location(String name, LocationType type, String address) {}
    record Company(String name, BusinessType type, List<Person> people, List<Location> locations) {}

    @Override
    public void run(String... args) {
        if (!enabled) return;
        String superEmail = seedSuperAdmin();
        if (superEmail == null) return;

        Path file = find(LOGIN_FILES);
        if (file == null) { log.warn("Test company seed: TEST_COMPANY_LOGINS.txt not found, skipping"); return; }
        Company company;
        try {
            company = parseCompany(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Test company seed: couldn't read {}: {}", file, e.getMessage());
            return;
        }
        if (company.people().isEmpty()) { log.warn("Test company seed: no people found in {}", file); return; }
        if (userRepo.findByEmail(company.people().getFirst().email()) != null) {
            log.info("Test company seed: {} already exists, skipping", company.name());
            return;
        }
        try {
            tx.executeWithoutResult(s -> seedCompany(company, superEmail));
            log.warn("Test company seed: created {} with {} sign-ins from {}", company.name(), company.people().size(), file.toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            log.error("Test company seed failed, nothing of the company was kept: {}", e.getMessage(), e);
        }
    }

    /** Creates the super admin with the password in the file (so SuperAdminBootstrap leaves it alone). */
    private String seedSuperAdmin() {
        User existing = userRepo.findBySuperAdminTrue().stream().findFirst().orElse(null);
        if (existing != null) return existing.getEmail();

        Path file = find(ADMIN_FILES);
        if (file == null) { log.warn("Test company seed: SUPER_ADMIN_CREDENTIALS.txt not found, skipping"); return null; }
        String text;
        try { text = Files.readString(file, StandardCharsets.UTF_8); }
        catch (Exception e) { log.error("Test company seed: couldn't read {}: {}", file, e.getMessage()); return null; }
        String email = field(text, "Email");
        String password = field(text, "Password");
        if (email == null || password == null) { log.error("Test company seed: no Email/Password in {}", file); return null; }
        email = email.toLowerCase(Locale.ROOT);
        if (userRepo.findByEmail(email) != null) {
            log.error("Test company seed: {} already belongs to a non-admin account", email);
            return null;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setSuperAdmin(true);
        admin.setLocked(false);
        admin.setSuspended(false);
        admin.setDeleted(false);
        admin.setMustChangePassword(false);
        admin.setPassword(security.hashPassword(password));
        admin.setCreatedBy("test-seed");
        admin.setCreatedAt(new Date());
        userRepo.save(admin);
        log.warn("Test company seed: super admin {} created with the password from {}", email, file.toAbsolutePath().normalize());
        return email;
    }

    private static Path find(List<String> candidates) {
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static String field(String text, String label) {
        Matcher m = Pattern.compile("(?m)^\\s*" + label + "\\s*:\\s*(\\S+)\\s*$").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern PERSON = Pattern.compile(
            "^(\\S+(?: \\S+)*?)\\s{2,}(OWNER|ADMIN|MANAGER|CASHIER|STAFF|DRIVER)\\s+(\\S+@\\S+)\\s+(\\S+)\\s+(.+)$");
    private static final Pattern LOCATION = Pattern.compile("^-\\s+(.+?)\\s+\\((\\w+)\\)\\s*(?:-\\s+(.+))?$");

    static Company parseCompany(String text) {
        String name = Optional.ofNullable(lineValue(text, "Company")).map(v -> v.replaceAll("\\s*\\(.*$", "").trim())
                .orElse("Test Company");
        String typeLabel = Optional.ofNullable(lineValue(text, "Type")).map(v -> v.replaceAll("\\s*\\(.*$", "").trim()).orElse("");
        BusinessType type = Arrays.stream(BusinessType.values())
                .filter(t -> t.getLabel().equalsIgnoreCase(typeLabel) || t.name().equalsIgnoreCase(typeLabel.replace(' ', '_')))
                .findFirst().orElse(BusinessType.GENERAL_RETAIL);

        List<Person> people = new ArrayList<>();
        List<Location> locations = new ArrayList<>();
        boolean inLocations = false;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.equals("LOCATIONS")) { inLocations = true; continue; }
            if (inLocations) {
                if (line.isEmpty()) { if (!locations.isEmpty()) inLocations = false; continue; }
                Matcher m = LOCATION.matcher(line);
                if (m.matches()) {
                    LocationType lt;
                    try { lt = LocationType.valueOf(m.group(2).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { lt = LocationType.SHOP; }
                    locations.add(new Location(m.group(1).trim(), lt, m.group(3) != null ? m.group(3).trim() : null));
                }
                continue;
            }
            Matcher m = PERSON.matcher(line);
            if (!m.matches()) continue;
            String[] names = m.group(1).trim().split("\\s+", 2);
            String job = m.group(5).trim();
            int dash = job.indexOf(" - ");
            String position = dash >= 0 ? job.substring(0, dash).trim() : job;
            String where = dash >= 0 ? job.substring(dash + 3).trim() : "";
            List<String> worksAt = where.isEmpty() || where.toLowerCase(Locale.ROOT).startsWith("all locations")
                    ? List.of() : Arrays.stream(where.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            people.add(new Person(names[0], names.length > 1 ? names[1] : "", UserRoles.valueOf(m.group(2)),
                    m.group(3).toLowerCase(Locale.ROOT), m.group(4), position, worksAt));
        }
        return new Company(name, type, people, locations);
    }

    private static String lineValue(String text, String label) {
        Matcher m = Pattern.compile("(?m)^\\s*" + label + "\\s*:\\s*(.+?)\\s*$").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // ── Building it ──────────────────────────────────────────────────────────

    /** Product: name, sku, category, base unit, cost, price, reorder level, pack unit (or null), pack size, pack price. */
    record P(String name, String sku, String category, String unit, double cost, double price, int reorder,
             String pack, double packSize, double packPrice) {}

    private static final List<String> CATEGORIES = List.of(
            "Rice & Grains", "Cooking Oil", "Tinned Foods", "Drinks", "Household", "Dairy & Eggs", "Bakery");

    private static final List<P> PRODUCTS = List.of(
            new P("Perfumed Rice 5kg", "RIC-5KG", "Rice & Grains", "Bag", 95, 120, 10, null, 0, 0),
            new P("Long Grain Rice 1kg", "RIC-1KG", "Rice & Grains", "Bag", 18, 24, 20, "Carton", 10, 230),
            new P("Spaghetti 500g", "SPA-500", "Rice & Grains", "Pack", 8, 11, 30, "Carton", 20, 210),
            new P("Vegetable Oil 1L", "OIL-1L", "Cooking Oil", "Bottle", 32, 40, 15, "Carton", 12, 470),
            new P("Vegetable Oil 5L", "OIL-5L", "Cooking Oil", "Bottle", 150, 185, 5, null, 0, 0),
            new P("Palm Oil 1L", "PALM-1L", "Cooking Oil", "Bottle", 28, 36, 10, "Carton", 12, 420),
            new P("Tomato Paste 400g", "TOM-400", "Tinned Foods", "Piece", 9, 12, 24, "Carton", 24, 280),
            new P("Sardines in Oil 125g", "SAR-125", "Tinned Foods", "Piece", 10, 14, 24, "Carton", 50, 680),
            new P("Evaporated Milk 160g", "MLK-160", "Tinned Foods", "Piece", 7, 9.5, 48, "Carton", 48, 440),
            new P("Baked Beans 415g", "BNS-415", "Tinned Foods", "Piece", 14, 19, 12, "Carton", 24, 440),
            new P("Cola 500ml", "COLA-500", "Drinks", "Bottle", 6, 8, 48, "Carton", 12, 92),
            new P("Mineral Water 1.5L", "WAT-1500", "Drinks", "Bottle", 4, 6, 48, "Pack", 6, 34),
            new P("Malt Drink 330ml", "MALT-330", "Drinks", "Bottle", 7, 10, 48, "Carton", 24, 230),
            new P("Chocolate Drink Powder 400g", "CHOC-400", "Drinks", "Piece", 38, 48, 12, "Carton", 12, 560),
            new P("Washing Powder 900g", "WASH-900", "Household", "Pack", 30, 38, 12, "Carton", 12, 440),
            new P("Bar Soap", "SOAP-BAR", "Household", "Piece", 6, 8, 30, "Carton", 40, 300),
            new P("Toilet Roll (10 pack)", "TOIL-10", "Household", "Pack", 35, 45, 10, null, 0, 0),
            new P("Dishwashing Liquid 750ml", "DISH-750", "Household", "Bottle", 18, 24, 12, "Carton", 12, 275),
            new P("Fresh Eggs (crate of 30)", "EGG-30", "Dairy & Eggs", "Box", 55, 70, 10, null, 0, 0),
            new P("Yoghurt 500ml", "YOG-500", "Dairy & Eggs", "Bottle", 12, 16, 20, null, 0, 0),
            new P("Butter Bread Large", "BRD-BUT", "Bakery", "Piece", 15, 20, 15, null, 0, 0),
            new P("Sugar Bread Small", "BRD-SUG", "Bakery", "Piece", 7, 10, 20, null, 0, 0));

    private static final Map<UserRoles, String> DEPARTMENT = Map.of(
            UserRoles.OWNER, "Management", UserRoles.ADMIN, "Management", UserRoles.MANAGER, "Operations",
            UserRoles.CASHIER, "Sales", UserRoles.STAFF, "Stores", UserRoles.DRIVER, "Logistics");
    private static final Map<UserRoles, Double> SALARY = Map.of(
            UserRoles.OWNER, 12000.0, UserRoles.ADMIN, 9000.0, UserRoles.MANAGER, 5500.0,
            UserRoles.CASHIER, 2500.0, UserRoles.STAFF, 2200.0, UserRoles.DRIVER, 2400.0);

    private void seedCompany(Company c, String superEmail) {
        Person first = c.people().getFirst();
        String domain = first.email().substring(first.email().indexOf('@') + 1);

        // Company + its first admin, the way the super admin does it in the Admin console.
        AdminService.Result r = adminService.create(new AdminService.CreateCompany(c.name(), "info@" + domain,
                "+233 30 200 0000", c.type(), new AdminService.NewPerson(first.first(), first.last(), first.email(), phone(0))),
                superEmail);
        Long bid = r.company().id();
        String admin = first.email();
        setPassword(first);

        // Locations
        Map<String, Shop> shops = new LinkedHashMap<>();
        for (Location l : c.locations()) {
            ShopDTO d = new ShopDTO();
            d.setBusinessId(bid.intValue());
            d.setShopName(l.name());
            d.setShopLocation(l.address() != null ? l.address() : l.name());
            d.setLocationType(l.type());
            d.setActive(true);
            shops.put(l.name(), shopService.createShop(d, admin));
        }

        // Team: the people in the file, with their own passwords (no first-sign-in password change).
        Map<String, Person> byEmail = new LinkedHashMap<>();
        for (int i = 1; i < c.people().size(); i++) {
            Person p = c.people().get(i);
            List<Long> shopIds = p.worksAt().stream().map(n -> {
                Shop s = shops.get(n);
                if (s == null) throw new IllegalStateException(p.email() + " works at unknown location '" + n + "'");
                return s.getId();
            }).toList();
            teamService.add(bid, new TeamService.NewMember(p.first(), p.last(), p.email(), phone(i), p.role(), shopIds), admin);
            setPassword(p);
        }
        c.people().forEach(p -> byEmail.put(p.email(), p));

        // HR records for everyone
        Business business = accessHelper.requireBusiness(bid);
        for (Person p : c.people()) {
            BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, userRepo.findByEmail(p.email()));
            EmployeeDTO e = new EmployeeDTO();
            e.setBusinessUserId(bu.getId());
            e.setPosition(p.position());
            e.setDepartment(DEPARTMENT.getOrDefault(p.role(), "Operations"));
            e.setSalary(SALARY.getOrDefault(p.role(), 2500.0));
            e.setSalaryType(SalaryType.MONTHLY);
            e.setStartDate(new Date());
            employeeService.createEmployee(bid, e, admin);
        }

        seedStock(bid, admin, c, shops);
    }

    /** Catalogue, suppliers, customers, purchases, transfers, sales and one pending adjustment. */
    private void seedStock(Long bid, String admin, Company c, Map<String, Shop> shops) {
        Shop warehouse = firstOf(shops, LocationType.WAREHOUSE);
        List<Shop> branches = shops.values().stream().filter(s -> s.getLocationType() == LocationType.SHOP).toList();
        Shop van = firstOf(shops, LocationType.VEHICLE);
        if (warehouse == null || branches.isEmpty()) {
            log.warn("Test company seed: need a warehouse and a shop for stock; skipping stock");
            return;
        }
        Shop branchA = branches.getFirst();                       // e.g. East Legon
        Shop branchB = branches.size() > 1 ? branches.get(1) : branchA; // e.g. Madina
        String warehouseManager = personAt(c, UserRoles.MANAGER, warehouse, admin);

        // Units and categories
        Map<String, Long> units = new HashMap<>();
        unitService.listUnits(bid, admin).forEach(u -> units.put(u.getName(), u.getId()));
        Map<String, Long> categories = new HashMap<>();
        for (String name : CATEGORIES) {
            CategoryDTO d = new CategoryDTO();
            d.setCategoryName(name);
            categories.put(name, categoryService.createCategory(bid, d, admin).getId());
        }

        // Products, most also sold by the carton/pack
        Map<String, Product> products = new LinkedHashMap<>();
        for (P p : PRODUCTS) {
            ProductDTO d = new ProductDTO();
            d.setProductName(p.name());
            d.setSku(p.sku());
            d.setCostPrice(p.cost());
            d.setSellingPrice(p.price());
            d.setReorderLevel(p.reorder());
            d.setCategoryId(categories.get(p.category()));
            d.setUnitId(units.get(p.unit()));
            Product saved = productService.createProduct(bid, d, admin);
            if (p.pack() != null) {
                ProductUnitDTO pu = new ProductUnitDTO();
                pu.setUnitId(units.get(p.pack()));
                pu.setConversionFactor(p.packSize());
                pu.setSellingPrice(p.packPrice());
                unitService.setProductUnits(bid, saved.getId(), List.of(pu), admin);
            }
            products.put(p.sku(), saved);
        }

        // Suppliers and customers
        Long temaFoods = supplier(bid, admin, "Tema Foods Ltd", "Joseph Tetteh", "sales@temafoods.test", "+233 30 221 4400");
        Long beverages = supplier(bid, admin, "Accra Beverages Distributors", "Linda Quaye", "orders@accrabev.test", "+233 30 255 7710");
        Long household = supplier(bid, admin, "Kasoa Household Supplies", "Ibrahim Mohammed", "info@kasoahousehold.test", "+233 24 410 2233");
        Long farms = supplier(bid, admin, "Golden Farms Eggs & Bakery", "Comfort Agyei", "hello@goldenfarms.test", "+233 20 733 9081");

        Long goldenSpoon = customer(bid, admin, "Golden Spoon", "Restaurant", "accounts@goldenspoon.test", "+233 30 290 1122", "Oxford Street, Osu");
        List<Long> customers = List.of(
                customer(bid, admin, "Esi", "Mensah", "esi.mensah@example.test", "+233 24 555 0101", null),
                customer(bid, admin, "Kofi", "Badu", null, "+233 24 555 0102", null),
                customer(bid, admin, "Afia", "Owusu", "afia.owusu@example.test", "+233 24 555 0103", null),
                customer(bid, admin, "Yaw", "Darko", null, "+233 24 555 0104", null));

        LocalDate today = LocalDate.now();

        // The owner's starting money, so the tills and bank never go below zero when stock is bought.
        capital(bid, admin, PaymentMethod.BANK_TRANSFER, "30000", "Opening bank balance", today);
        capital(bid, admin, PaymentMethod.MOBILE_MONEY, "10000", "Opening mobile money float", today);
        capital(bid, admin, PaymentMethod.CASH, "3000", "Opening cash float", today);

        // Opening purchases into the warehouse, with batch numbers and expiry dates.
        Invoice tema = purchase(bid, warehouseManager, warehouse.getId(), temaFoods, PaymentMethod.CREDIT, "Opening stock from Tema Foods (on credit)",
                line(products, "RIC-5KG", 60, 95, "TF-RIC5-2611", today.plusMonths(14)),
                line(products, "RIC-1KG", 200, 18, "TF-RIC1-2611", today.plusMonths(12)),
                line(products, "SPA-500", 240, 8, "TF-SPA-2611", today.plusMonths(10)),
                line(products, "OIL-1L", 144, 32, "TF-OIL1-2611", today.plusMonths(9)),
                line(products, "OIL-5L", 30, 150, "TF-OIL5-2611", today.plusMonths(9)),
                line(products, "PALM-1L", 96, 28, "TF-PALM-2611", today.plusMonths(6)),
                line(products, "TOM-400", 240, 9, "TF-TOM-2611", today.plusMonths(18)),
                line(products, "SAR-125", 200, 10, "TF-SAR-2611", today.plusMonths(24)),
                line(products, "MLK-160", 240, 7, "TF-MLK-2611", today.plusMonths(8)),
                line(products, "BNS-415", 120, 14, "TF-BNS-2611", today.plusMonths(16)));
        purchase(bid, warehouseManager, warehouse.getId(), beverages, PaymentMethod.BANK_TRANSFER, "Drinks from Accra Beverages",
                line(products, "COLA-500", 240, 6, "AB-COLA-2611", today.plusMonths(6)),
                line(products, "WAT-1500", 240, 4, "AB-WAT-2611", today.plusMonths(12)),
                line(products, "MALT-330", 192, 7, "AB-MALT-2611", today.plusMonths(7)),
                line(products, "CHOC-400", 60, 38, "AB-CHOC-2611", today.plusMonths(15)));
        purchase(bid, warehouseManager, warehouse.getId(), household, PaymentMethod.MOBILE_MONEY, "Household goods from Kasoa",
                line(products, "WASH-900", 96, 30, "KH-WASH-2611", null),
                line(products, "SOAP-BAR", 200, 6, "KH-SOAP-2611", null),
                line(products, "TOIL-10", 60, 35, "KH-TOIL-2611", null),
                line(products, "DISH-750", 72, 18, "KH-DISH-2611", today.plusMonths(20)));
        purchase(bid, warehouseManager, warehouse.getId(), farms, PaymentMethod.CASH, "Eggs and yoghurt from Golden Farms",
                line(products, "EGG-30", 40, 55, "GF-EGG-" + today.getDayOfMonth(), today.plusDays(12)),
                line(products, "YOG-500", 60, 12, "GF-YOG-" + today.getDayOfMonth(), today.plusDays(20)));
        // Bread is delivered straight to the branches; the one at branch B is close to its date.
        purchase(bid, personAt(c, UserRoles.MANAGER, branchA, admin), branchA.getId(), farms, PaymentMethod.CASH, "Bread delivery",
                line(products, "BRD-BUT", 30, 15, "GF-BRD-A" + today.getDayOfMonth(), today.plusDays(5)),
                line(products, "BRD-SUG", 40, 7, "GF-BRS-A" + today.getDayOfMonth(), today.plusDays(5)));
        purchase(bid, personAt(c, UserRoles.MANAGER, branchB, admin), branchB.getId(), farms, PaymentMethod.CASH, "Bread delivery",
                line(products, "BRD-BUT", 25, 15, "GF-BRD-B" + today.getDayOfMonth(), today.plusDays(3)),
                line(products, "BRD-SUG", 30, 7, "GF-BRS-B" + today.getDayOfMonth(), today.plusDays(3)));

        // Part-payment of the Tema Foods credit purchase.
        PaymentDTO pay = new PaymentDTO();
        pay.setAmount(new BigDecimal("5000"));
        pay.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        pay.setPaymentReference("TT-" + today.getYear() + "-0001");
        pay.setNotes("Part payment");
        paymentService.recordPayment(bid, tema.getId(), InvoiceType.PURCHASE, pay, admin);

        // Transfers: every warehouse item to both branches (shares of what came in), a few onto the van.
        int transfers = 0;
        for (P p : PRODUCTS) {
            if (p.category().equals("Bakery")) continue;
            double inWarehouse = inventoryRepo.findByProductAndShop(products.get(p.sku()), warehouse).map(Inventory::getQuantity).orElse(0.0);
            double share = Math.floor(inWarehouse * 0.3);
            if (share < 1) continue;
            transfer(bid, warehouseManager, products.get(p.sku()), warehouse, branchA, share);
            transfers++;
            if (branchB != branchA) { transfer(bid, warehouseManager, products.get(p.sku()), warehouse, branchB, share); transfers++; }
        }
        if (van != null) {
            for (String sku : List.of("WAT-1500", "COLA-500", "RIC-5KG")) {
                transfer(bid, warehouseManager, products.get(sku), warehouse, van, sku.equals("RIC-5KG") ? 5 : 24);
                transfers++;
            }
        }

        // Sales by the cashiers at their own branch (fixed seed: the same data every time).
        Random rnd = new Random(2026);
        PaymentMethod[] methods = {PaymentMethod.CASH, PaymentMethod.CASH, PaymentMethod.MOBILE_MONEY, PaymentMethod.BANK_TRANSFER};
        List<Person> cashiers = c.people().stream().filter(p -> p.role() == UserRoles.CASHIER && !p.worksAt().isEmpty()).toList();
        int sales = 0;
        for (Person cashier : cashiers) {
            Shop shop = shops.get(cashier.worksAt().getFirst());
            List<Product> onShelf = products.values().stream()
                    .filter(pr -> inventoryRepo.findByProductAndShop(pr, shop).map(Inventory::getQuantity).orElse(0.0) >= 6)
                    .toList();
            for (int i = 0; i < 12 && !onShelf.isEmpty(); i++) {
                SaleDTO s = new SaleDTO();
                s.setShopId(shop.getId());
                s.setPaymentMethod(methods[rnd.nextInt(methods.length)]);
                if (rnd.nextInt(3) == 0) s.setCustomerId(customers.get(rnd.nextInt(customers.size())));
                List<SaleItemDTO> items = new ArrayList<>();
                Set<Long> used = new HashSet<>();
                int lines = 1 + rnd.nextInt(3);
                for (int k = 0; k < lines; k++) {
                    Product pr = onShelf.get(rnd.nextInt(onShelf.size()));
                    if (!used.add(pr.getId())) continue;
                    SaleItemDTO it = new SaleItemDTO();
                    it.setProductId(pr.getId());
                    it.setQuantity((double) (1 + rnd.nextInt(2)));
                    items.add(it);
                }
                s.setItems(items);
                saleService.createSale(bid, s, cashier.email());
                sales++;
            }
        }

        // One credit sale to the restaurant, with a GHS 200 deposit.
        Person creditCashier = cashiers.stream().filter(p -> shops.get(p.worksAt().getFirst()) == branchA).findFirst()
                .orElse(cashiers.isEmpty() ? null : cashiers.getFirst());
        if (creditCashier != null) {
            Shop shop = shops.get(creditCashier.worksAt().getFirst());
            SaleDTO s = new SaleDTO();
            s.setShopId(shop.getId());
            s.setCustomerId(goldenSpoon);
            s.setPaymentMethod(PaymentMethod.CREDIT);
            s.setNotes("Weekly supplies, on account");
            s.setItems(List.of(saleItem(products, "RIC-5KG", 4), saleItem(products, "OIL-5L", 2), saleItem(products, "TOM-400", 12)));
            Invoice credit = saleService.createSale(bid, s, creditCashier.email());
            PaymentDTO dep = new PaymentDTO();
            dep.setAmount(new BigDecimal("200"));
            dep.setPaymentMethod(PaymentMethod.CASH);
            dep.setNotes("Deposit");
            paymentService.recordPayment(bid, credit.getId(), InvoiceType.SALES, dep, creditCashier.email());
            sales++;
        }

        // A pending adjustment from a staff member (approvals are on for supermarkets).
        Person staff = c.people().stream().filter(p -> p.role() == UserRoles.STAFF && p.worksAt().contains(branchA.getShopName()))
                .findFirst().orElse(null);
        if (staff != null) {
            Product cola = products.get("COLA-500");
            double now = inventoryRepo.findByProductAndShop(cola, branchA).map(Inventory::getQuantity).orElse(0.0);
            if (now >= 3) {
                StockAdjustmentDTO a = new StockAdjustmentDTO();
                a.setProductId(cola.getId());
                a.setShopId(branchA.getId());
                a.setNewQuantity(now - 3);
                a.setReason("Damaged");
                a.setNotes("3 bottles broken while restocking the chiller");
                inventoryService.adjust(bid, a, staff.email());
            }
        }

        log.info("Test company seed: {} products, {} transfers, {} sales", products.size(), transfers, sales);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void capital(Long bid, String by, PaymentMethod method, String amount, String note, LocalDate date) {
        accountingService.recordCapital(bid, new AccountingService.OwnerMoney(new BigDecimal(amount), null, method, date, note), by);
    }

    private void setPassword(Person p) {
        User u = userRepo.findByEmail(p.email());
        u.setPassword(security.hashPassword(p.password()));
        u.setMustChangePassword(false);
        userRepo.save(u);
    }

    private static String phone(int i) {
        return String.format("+233 24 300 %04d", 100 + i);
    }

    private static Shop firstOf(Map<String, Shop> shops, LocationType type) {
        return shops.values().stream().filter(s -> s.getLocationType() == type).findFirst().orElse(null);
    }

    /** The first person with this role who works at the location; falls back to the admin. */
    private static String personAt(Company c, UserRoles role, Shop shop, String fallback) {
        return c.people().stream().filter(p -> p.role() == role && p.worksAt().contains(shop.getShopName()))
                .map(Person::email).findFirst().orElse(fallback);
    }

    private Long supplier(Long bid, String by, String name, String officer, String email, String phone) {
        SupplierDTO d = new SupplierDTO();
        d.setCompanyName(name);
        d.setRelationshipOfficer(officer);
        d.setCompanyEmail(email);
        d.setTelephonePrimary(phone);
        return supplierService.createSupplier(bid, d, by).getId();
    }

    private Long customer(Long bid, String by, String first, String last, String email, String phone, String address) {
        CustomerDTO d = new CustomerDTO();
        d.setFirstName(first);
        d.setLastName(last);
        d.setEmail(email);
        d.setPhone(phone);
        d.setAddress(address);
        return customerService.createCustomer(bid, d, by).getId();
    }

    private static PurchaseItemDTO line(Map<String, Product> products, String sku, double qty, double unitCost, String batch, LocalDate expiry) {
        PurchaseItemDTO it = new PurchaseItemDTO();
        it.setProductId(products.get(sku).getId());
        it.setQuantity(qty);
        it.setUnitPrice(BigDecimal.valueOf(unitCost));
        it.setBatchNumber(batch);
        it.setExpiryDate(expiry);
        return it;
    }

    private Invoice purchase(Long bid, String by, Long shopId, Long supplierId, PaymentMethod method, String notes, PurchaseItemDTO... items) {
        PurchaseDTO d = new PurchaseDTO();
        d.setShopId(shopId);
        d.setSupplierId(supplierId);
        d.setPaymentMethod(method);
        d.setNotes(notes);
        d.setItems(List.of(items));
        return purchaseService.createPurchase(bid, d, by);
    }

    private void transfer(Long bid, String by, Product product, Shop from, Shop to, double qty) {
        StockTransferDTO d = new StockTransferDTO();
        d.setProductId(product.getId());
        d.setFromShopId(from.getId());
        d.setToShopId(to.getId());
        d.setQuantity(qty);
        inventoryService.transfer(bid, d, by);
    }

    private static SaleItemDTO saleItem(Map<String, Product> products, String sku, double qty) {
        SaleItemDTO it = new SaleItemDTO();
        it.setProductId(products.get(sku).getId());
        it.setQuantity(qty);
        return it;
    }
}
