package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.TeamService;
import com.ultimate.inventory.Service.UserAccounts;
import com.ultimate.inventory.Service.Usernames;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Team members. A row with a username invites that existing account (they join when they accept).
 * A row with a name and email creates the account and adds them straight away, unless the email
 * already has an account, in which case they're invited instead.
 */
@Component
public class PeopleImporter implements Importer {

    /** Roles as people write them. */
    private static final Map<UserRoles, String> ROLE_NAMES = new LinkedHashMap<>();
    static {
        ROLE_NAMES.put(UserRoles.OWNER, "Owner");
        ROLE_NAMES.put(UserRoles.ADMIN, "Admin");
        ROLE_NAMES.put(UserRoles.MANAGER, "Manager");
        ROLE_NAMES.put(UserRoles.CASHIER, "Cashier");
        ROLE_NAMES.put(UserRoles.STAFF, "Staff");
        ROLE_NAMES.put(UserRoles.DRIVER, "Driver");
        ROLE_NAMES.put(UserRoles.SHOPKEEP, "Shopkeeper");
    }
    private static final Set<UserRoles> EVERYWHERE = EnumSet.of(UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.optional("username", "Username", "For people who already have an account: they get an invitation. Leave blank for new people.", "kofi_owusu"),
            ImportColumn.optional("firstName", "First name", "New people: required.", "Ama"),
            ImportColumn.optional("lastName", "Last name", "New people: required.", "Mensah"),
            ImportColumn.optional("email", "Email", "New people: required; it's their sign-in.", "ama@example.com"),
            ImportColumn.optional("phone", "Phone", "", "0244123456"),
            ImportColumn.required("role", "Role", "What they do. Owners, admins and managers work at every location.", "Cashier")
                    .withOptions(List.of("Admin", "Manager", "Cashier", "Staff", "Driver")),
            ImportColumn.optional("locations", "Locations", "Where they work: location names separated by commas. Required for cashiers, staff and drivers.", "Osu branch, Madina branch"));

    private final BusinessAccessHelper access;
    private final TeamService teamService;
    private final UserRepository userRepo;
    private final BusinessUserRepo memberRepo;
    private final ShopRepository shopRepo;

    public PeopleImporter(BusinessAccessHelper access, TeamService teamService, UserRepository userRepo,
                          BusinessUserRepo memberRepo, ShopRepository shopRepo) {
        this.access = access;
        this.teamService = teamService;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.shopRepo = shopRepo;
    }

    @Override public ImportKind kind() { return ImportKind.PEOPLE; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of(
                "New people get an account, a username and a temporary password, shown once after the upload. They choose their own password at first sign-in.",
                "People who already have an account (by username or email) get an invitation and join when they accept.");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireManager(businessId, email);
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        List<UserRoles> assignable = teamService.list(businessId, email).rolesYouCanAssign();
        Map<String, Shop> locations = LocationImporter.locationsByName(shopRepo, business);
        Set<String> fileUsernames = new HashSet<>();
        Set<String> fileEmails = new HashSet<>();
        Plan plan = new Plan();

        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String username = c.text("username");
            String first = c.text("firstName", 100);
            String last = c.text("lastName", 100);
            String mail = c.text("email", 150);
            String phone = c.text("phone", 30);
            UserRoles role = c.choice("role", UserRoles.class, ROLE_NAMES::get);
            List<String> locationNames = c.list("locations");

            if (role != null && !assignable.contains(role)) {
                c.error("role", "You can't give the " + ROLE_NAMES.get(role).toLowerCase() + " role");
            }
            List<Long> shopIds = new ArrayList<>();
            for (String ln : locationNames) {
                Shop s = locations.get(ln.toLowerCase(Locale.ROOT));
                if (s == null) c.error("locations", "No location called \"" + ln + "\"");
                else if (!shopIds.contains(s.getId())) shopIds.add(s.getId());
            }
            if (role != null && !EVERYWHERE.contains(role) && locationNames.isEmpty()) {
                c.error("locations", ROLE_NAMES.get(role) + "s need at least one location");
            }
            List<Long> where = role != null && EVERYWHERE.contains(role) ? List.of() : shopIds;

            if (username != null) {
                // An existing account: invite by username.
                String u = Usernames.clean(username);
                User invitee = userRepo.findByUsername(u);
                if (invitee == null || Boolean.TRUE.equals(invitee.getDeleted()) || Boolean.TRUE.equals(invitee.getSuperAdmin())) {
                    c.error("username", "No one has the username @" + u);
                } else if (!fileUsernames.add(u)) {
                    c.error("username", "@" + u + " appears more than once in this file");
                } else {
                    alreadyOnTeam(c, "username", business, invitee);
                    fileEmails.add(invitee.getEmail().toLowerCase(Locale.ROOT));
                }
                if (!c.ok()) continue;
                plan.add(row.number(), r -> {
                    teamService.invite(businessId, new TeamService.Invite(u, role, where), email);
                    r.addInvited();
                });
                continue;
            }

            // A new person (or an existing account found by email).
            if (first == null) c.error("firstName", "Required for new people (or fill in their username)");
            if (last == null) c.error("lastName", "Required for new people (or fill in their username)");
            if (mail == null) {
                c.error("email", "Required for new people (or fill in their username)");
            } else {
                String m = mail.toLowerCase(Locale.ROOT);
                try {
                    User existing = userRepo.findFirstByEmailIgnoreCaseOrderByIdAsc(UserAccounts.normalizeEmail(m));
                    if (existing != null) alreadyOnTeam(c, "email", business, existing);
                } catch (AppException e) {
                    c.error("email", e.getMessage());
                }
                if (!fileEmails.add(m)) c.error("email", mail + " appears more than once in this file");
            }
            if (!c.ok()) continue;
            TeamService.NewMember person = new TeamService.NewMember(first, last, mail, phone, role, where);
            plan.add(row.number(), r -> {
                TeamService.Added a = teamService.add(businessId, person, email);
                if (a.invitation() != null) {
                    r.addInvited();
                } else {
                    r.addCreated();
                    if (a.temporaryPassword() != null && a.member() != null) {
                        r.addCredential(new ImportResult.Credential(a.member().name(), a.member().email(),
                                a.member().username(), a.temporaryPassword()));
                    }
                }
            });
        }
        return plan;
    }

    private void alreadyOnTeam(Cells c, String column, Business business, User user) {
        BusinessUser bu = memberRepo.findByBusinessAndUser(business, user);
        if (bu != null && bu.isActiveMember()) c.error(column, UserAccounts.displayName(user) + " is already on the team");
    }
}
