package com.ultimate.inventory.Service;

import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsernamesTest {

    private final Map<String, User> taken = new HashMap<>();
    private Usernames usernames;

    @BeforeEach
    void setUp() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByUsername(anyString())).thenAnswer(inv -> taken.get(inv.<String>getArgument(0)));
        usernames = new Usernames(repo);
    }

    private void take(String username, long id) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        taken.put(username, u);
    }

    @Test
    void cleanDropsAtSpacesAndCapitals() {
        assertThat(Usernames.clean("  @Ama_Mensah ")).isEqualTo("ama_mensah");
        assertThat(Usernames.clean(null)).isEmpty();
    }

    @Test
    void rules() {
        assertThat(Usernames.problem("ama")).isNull();
        assertThat(Usernames.problem("ab")).isNotNull();
        assertThat(Usernames.problem("a".repeat(21))).isNotNull();
        assertThat(Usernames.problem("ama.mensah")).isNotNull();
        assertThat(Usernames.problem("admin")).isEqualTo("That username is reserved");
    }

    @Test
    void requireAvailableIsCaseInsensitiveAndAllowsYourOwn() {
        take("kofi", 1);
        assertThatThrownBy(() -> usernames.requireAvailable("@Kofi", 2L))
                .isInstanceOf(AppException.class).hasMessageContaining("taken");
        assertThat(usernames.requireAvailable("KOFI", 1L)).isEqualTo("kofi");
        assertThat(usernames.requireAvailable("Kofi_2", 2L)).isEqualTo("kofi_2");
    }

    @Test
    void suggestFromNameAddsNumberWhenTaken() {
        assertThat(usernames.suggest("Ámà", "O'Neil-Mensah")).isEqualTo("ama_oneil_mensah");
        take("kofi_owusu", 1);
        assertThat(usernames.suggest("Kofi", "Owusu")).isEqualTo("kofi_owusu2");
        take("kofi_owusu2", 2);
        assertThat(usernames.suggest("Kofi", "Owusu")).isEqualTo("kofi_owusu3");
    }

    @Test
    void suggestHandlesShortLongAndMissingNames() {
        assertThat(usernames.suggest(null, null)).isEqualTo("user");
        assertThat(usernames.suggest("Al", null)).isEqualTo("al_user");
        String longOne = usernames.suggest("Bartholomew", "Featherstonehaugh");
        assertThat(longOne.length()).isLessThanOrEqualTo(Usernames.MAX);
        assertThat(Usernames.problem(longOne)).isNull();
        assertThat(usernames.suggest("Admin", null)).isEqualTo("admin2"); // reserved, so numbered
    }
}
