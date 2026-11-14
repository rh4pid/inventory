package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.InvitationStatus;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.BusinessMembershipDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Invitation;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.InvitationRepository;
import com.ultimate.inventory.Repository.ShopUsersRepository;
import com.ultimate.inventory.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TeamServiceTest {

    private static final String ME = "me@x.com";

    private BusinessAccessHelper access;
    private BusinessUserRepo memberRepo;
    private UserAccounts accounts;
    private UserRepository userRepo;
    private InvitationRepository invitationRepo;
    private TeamService team;

    private Business company;
    private final List<BusinessUser> members = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        access = mock(BusinessAccessHelper.class);
        memberRepo = mock(BusinessUserRepo.class);
        ShopUsersRepository shopUsers = mock(ShopUsersRepository.class);
        accounts = mock(UserAccounts.class);
        userRepo = mock(UserRepository.class);
        invitationRepo = mock(InvitationRepository.class);
        team = new TeamService(access, memberRepo, shopUsers, accounts, userRepo, invitationRepo);

        company = new Business();
        company.setId(10L);
        company.setName("Acme");
        company.setCode("BIZ00010");
        when(access.requireBusiness(10L)).thenReturn(company);
        when(memberRepo.findAllByBusiness(company)).thenReturn(members);
        when(memberRepo.findById(anyLong())).thenAnswer(inv -> members.stream()
                .filter(m -> m.getId().equals(inv.getArgument(0))).findFirst());
        when(memberRepo.save(any())).thenAnswer(inv -> {
            BusinessUser bu = inv.getArgument(0);
            if (bu.getId() == null) bu.setId(nextId++);
            return bu;
        });
        when(invitationRepo.save(any())).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getId() == null) i.setId(nextId++);
            return i;
        });
        when(shopUsers.findActiveByBusinessUser(any())).thenReturn(List.of());
    }

    private User user(String username) {
        User u = new User();
        u.setId(nextId++);
        u.setUsername(username);
        u.setFirstName(username);
        u.setLastName("Test");
        u.setEmail(username + "@x.com");
        return u;
    }

    private BusinessUser member(String username, UserRoles role) {
        BusinessUser bu = new BusinessUser();
        bu.setId(nextId++);
        bu.setUser(user(username));
        bu.setBusiness(company);
        bu.setRoles(role);
        bu.setActive(true);
        members.add(bu);
        return bu;
    }

    private void actAs(BusinessUser actor) {
        when(access.requireManager(10L, ME)).thenReturn(actor);
        when(access.requireUser(ME)).thenReturn(actor.getUser());
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(status));
    }

    // ── Removing people ──────────────────────────────────────────────────────

    @Test
    void managersCannotRemovePeople() {
        BusinessUser manager = member("mgr", UserRoles.MANAGER);
        BusinessUser cashier = member("cash", UserRoles.CASHIER);
        actAs(manager);
        assertStatus(() -> team.remove(10L, cashier.getId(), ME), HttpStatus.FORBIDDEN);
        assertThat(cashier.isActiveMember()).isTrue();
    }

    @Test
    void adminsCannotRemoveOwners() {
        member("boss", UserRoles.OWNER);
        BusinessUser admin = member("adm", UserRoles.ADMIN);
        actAs(admin);
        BusinessUser owner = members.get(0);
        assertStatus(() -> team.remove(10L, owner.getId(), ME), HttpStatus.FORBIDDEN);
    }

    @Test
    void ownersCanRemoveAnotherOwner() {
        BusinessUser me = member("boss", UserRoles.OWNER);
        BusinessUser other = member("boss2", UserRoles.OWNER);
        actAs(me);
        team.remove(10L, other.getId(), ME);
        assertThat(other.isActiveMember()).isFalse();
    }

    @Test
    void nobodyRemovesThemselves() {
        BusinessUser me = member("boss", UserRoles.OWNER);
        actAs(me);
        assertStatus(() -> team.remove(10L, me.getId(), ME), HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyOwnersCanMakeOwners() {
        member("boss", UserRoles.OWNER);
        BusinessUser admin = member("adm", UserRoles.ADMIN);
        BusinessUser cashier = member("cash", UserRoles.CASHIER);
        actAs(admin);
        assertStatus(() -> team.update(10L, cashier.getId(), new TeamService.Change(UserRoles.OWNER, null), ME), HttpStatus.FORBIDDEN);
    }

    // ── Adding and inviting ──────────────────────────────────────────────────

    @Test
    void addingSomeoneWithAnAccountSendsAnInvitationInstead() {
        BusinessUser me = member("boss", UserRoles.OWNER);
        actAs(me);
        User existing = user("kofi");
        when(accounts.findExisting("kofi@x.com")).thenReturn(existing);

        TeamService.Added r = team.add(10L, new TeamService.NewMember("Kofi", "Test", "kofi@x.com", null, UserRoles.MANAGER, null), ME);

        assertThat(r.member()).isNull();
        assertThat(r.invitation()).isNotNull();
        assertThat(r.invitation().username()).isEqualTo("kofi");
        assertThat(r.invitation().role()).isEqualTo(UserRoles.MANAGER);
        verify(accounts, never()).findOrCreate(any(), any(), any(), any(), any());
        verify(memberRepo, never()).save(argThat(bu -> bu.getUser() == existing));
    }

    @Test
    void cannotInviteYourself() {
        BusinessUser me = member("boss", UserRoles.OWNER);
        actAs(me);
        when(userRepo.findByUsername("boss")).thenReturn(me.getUser());
        assertStatus(() -> team.invite(10L, new TeamService.Invite("@Boss", UserRoles.ADMIN, null), ME), HttpStatus.BAD_REQUEST);
    }

    @Test
    void managersCannotInviteAdmins() {
        BusinessUser manager = member("mgr", UserRoles.MANAGER);
        actAs(manager);
        assertStatus(() -> team.invite(10L, new TeamService.Invite("kofi", UserRoles.ADMIN, null), ME), HttpStatus.FORBIDDEN);
    }

    // ── Answering ────────────────────────────────────────────────────────────

    private Invitation invitationFor(User invitee, Date expiresAt) {
        Invitation inv = new Invitation();
        inv.setId(nextId++);
        inv.setBusiness(company);
        inv.setInvitee(invitee);
        inv.setInvitedBy(user("boss"));
        inv.setRole(UserRoles.CASHIER);
        inv.setStatus(InvitationStatus.PENDING);
        inv.setCreatedAt(new Date());
        inv.setExpiresAt(expiresAt);
        when(invitationRepo.findById(inv.getId())).thenReturn(Optional.of(inv));
        return inv;
    }

    @Test
    void acceptingJoinsTheCompanyWithTheInvitedRole() {
        User kofi = user("kofi");
        when(access.requireUser(eq("kofi@x.com"))).thenReturn(kofi);
        Invitation inv = invitationFor(kofi, new Date(System.currentTimeMillis() + 86_400_000));

        BusinessMembershipDTO m = team.acceptInvitation(inv.getId(), "kofi@x.com");

        assertThat(m.getBusinessId()).isEqualTo(10L);
        assertThat(m.getRole()).isEqualTo(UserRoles.CASHIER);
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void expiredInvitationsCannotBeAccepted() {
        User kofi = user("kofi");
        when(access.requireUser(eq("kofi@x.com"))).thenReturn(kofi);
        Invitation inv = invitationFor(kofi, new Date(System.currentTimeMillis() - 1000));

        assertStatus(() -> team.acceptInvitation(inv.getId(), "kofi@x.com"), HttpStatus.CONFLICT);
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(memberRepo, never()).save(any());
    }

    @Test
    void someoneElsesInvitationIsNotFound() {
        User kofi = user("kofi");
        User ama = user("ama");
        when(access.requireUser(anyString())).thenReturn(ama);
        Invitation inv = invitationFor(kofi, new Date(System.currentTimeMillis() + 86_400_000));
        assertStatus(() -> team.acceptInvitation(inv.getId(), "ama@x.com"), HttpStatus.NOT_FOUND);
    }
}
