package lk.finora.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * T60.4 / D63 contract candidate. Run only after the promotion endpoint exists.
 * Uses the repository's rollback-only test fixture and isolated test datasource.
 * No external HTTP, providers, real financial records or real account deletion.
 */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UserPromotionPrivacyTest extends IntegrationTest {
    private static final String API = "/api/v1";
    private static final String MONTH = "2026-11";
    private static final String PASSWORD = "SyntheticPromotion2026";
    private static final String CONFIRM = "DELETE_FINANCIAL_DATA";

    // Directly owned financial and derived-data families admitted by V1–V24.
    private static final List<String> OWNED = List.of(
            "accounts", "categories", "transactions", "budgets", "bills", "goals",
            "score_inputs", "insights", "health_scores", "monthly_history",
            "history_recovery_retry", "advice", "advice_ratings",
            "advice_generation_leases", "advice_verification_events",
            "alert_events", "device_tokens");

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired PasswordEncoder passwords;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private record Session(String access, String refresh) {}
    private record Fixture(long id, String email, long bill, long goal, long advice,
                           Session first, Session second) {}

    private Session login(String email, String expectedRole) throws Exception {
        var response = mockMvc.perform(post(API + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn().getResponse();
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        var session = new Session(body.get("accessToken").asString(), body.get("refreshToken").asString());
        assertThat(session.access()).isNotBlank();
        assertThat(session.refresh()).isNotBlank();
        return session;
    }

    private long ownId(String table, long id) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE user_id=?", Long.class, id);
    }

    private Fixture seed(String label, long income, long spend, int score) throws Exception {
        var user = createUser(Role.USER);
        user.setPasswordHash(passwords.encode(PASSWORD));
        user.setFullName(label + " Synthetic");
        userRepository.saveAndFlush(user);
        long id = user.getId();
        jdbc.update("INSERT INTO consents(user_id,version,over18) VALUES (?,'1.0',TRUE)", id);

        jdbc.update("INSERT INTO accounts(user_id,name,type,opening_balance) VALUES (?,?,'BANK',25000.00)",
                id, label + " wallet");
        jdbc.update("INSERT INTO categories(user_id,name,kind,need_or_want) VALUES (?,?,'EXPENSE','NEED')",
                id, label + " groceries");
        long account = ownId("accounts", id), category = ownId("categories", id);
        jdbc.update("INSERT INTO transactions(user_id,account_id,type,amount,txn_date,merchant) VALUES (?,?,'INCOME',?,'2026-11-01',?)",
                id, account, BigDecimal.valueOf(income), label + " salary");
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,merchant) VALUES (?,?,?,'EXPENSE',?,'2026-11-03',?)",
                id, account, category, BigDecimal.valueOf(spend).add(new BigDecimal("0.75")), label + " supermarket");
        jdbc.update("INSERT INTO budgets(user_id,category_id,month,limit_amount) VALUES (?,?,?,25000.00)",
                id, category, MONTH);
        jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id) VALUES (?,? ,7500.00,15,?)",
                id, label + " utility bill", category);
        jdbc.update("INSERT INTO goals(user_id,name,target_amount,target_date) VALUES (?,?,150000.00,'2027-06-30')",
                id, label + " education goal");
        long bill = ownId("bills", id), goal = ownId("goals", id), budget = ownId("budgets", id);
        jdbc.update("INSERT INTO bill_payments(bill_id,month) VALUES (?,?)", bill, MONTH);
        jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,12500.00,'2026-11-05')", goal);
        jdbc.update("INSERT INTO score_inputs(user_id,month,emergency_reserve,reserve_as_of,confirmed_at) VALUES (?,?,45000.00,'2026-11-05',UTC_TIMESTAMP(6))",
                id, MONTH);
        jdbc.update("INSERT INTO insights(user_id,month,type,facts) VALUES (?,?,'HIGH_WANTS',JSON_OBJECT('fixture',?))",
                id, MONTH, label);
        jdbc.update("INSERT INTO health_scores(user_id,month,total,parts) VALUES (?,?,?,'[]')", id, MONTH, score);
        jdbc.update("INSERT INTO monthly_history(user_id,month,income,expense,score,insights) VALUES (?,?,?,?,?,'[]')",
                id, MONTH, BigDecimal.valueOf(income), BigDecimal.valueOf(spend).add(new BigDecimal("0.75")), score);
        jdbc.update("INSERT INTO history_recovery_retry(user_id,month,retry_at) VALUES (?,?,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",
                id, MONTH);
        jdbc.update("INSERT INTO advice(user_id,month,source,mode,body,verified,monthly_delivery,focus_goal_id) VALUES (?,?,'RULE','V1','[]',FALSE,TRUE,?)",
                id, MONTH, goal);
        long advice = ownId("advice", id);
        jdbc.update("INSERT INTO advice_ratings(advice_id,user_id,rating) VALUES (?,?,4)", advice, id);
        jdbc.update("INSERT INTO advice_generation_leases(user_id,month,lease_token,expires_at,focus_key) VALUES (?,?,?,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,?)",
                id, MONTH, UUID.randomUUID().toString(), goal);
        jdbc.update("INSERT INTO advice_verification_events(user_id,month,provider,verified,reason) VALUES (?,?,'GROQ',FALSE,'SYNTHETIC_FIXTURE')",
                id, MONTH);
        jdbc.update("INSERT INTO alert_events(user_id,type,ref_id,period) VALUES (?,'BUDGET_NEAR',?,?)", id, budget, MONTH);
        jdbc.update("INSERT INTO device_tokens(user_id,token) VALUES (?,?)", id, "synthetic-device-" + UUID.randomUUID());

        Session first = login(user.getEmail(), "USER"), second = login(user.getEmail(), "USER");
        entities.flush();
        return new Fixture(id, user.getEmail(), bill, goal, advice, first, second);
    }

    private Map<String, List<Map<String, Object>>> finance(Fixture f) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : OWNED) {
            String order = table.equals("transactions") ? " ORDER BY id" : "";
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE user_id=?" + order, f.id()));
        }
        // Use the original parent IDs even after parent deletion, not joins that
        // might hide orphaned children.
        result.put("bill_payments", jdbc.queryForList("SELECT * FROM bill_payments WHERE bill_id=?", f.bill()));
        result.put("goal_contributions", jdbc.queryForList("SELECT * FROM goal_contributions WHERE goal_id=?", f.goal()));
        result.put("advice_ratings_by_advice", jdbc.queryForList("SELECT * FROM advice_ratings WHERE advice_id=?", f.advice()));
        return result;
    }

    private Map<String,Object> identity(long id) {
        return jdbc.queryForMap("SELECT id,email,full_name,password_hash FROM users WHERE id=?", id);
    }

    private List<Map<String,Object>> sessions(long id) {
        return jdbc.queryForList("SELECT * FROM refresh_tokens WHERE user_id=? ORDER BY id", id);
    }

    private List<Map<String,Object>> defaults() {
        return jdbc.queryForList("SELECT * FROM categories WHERE user_id IS NULL ORDER BY id");
    }

    private void assertStaffCannotReachUserRoutes(String access) throws Exception {
        var routes = new ArrayList<String>();
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith(API + "/") || List.of("/auth/", "/admin/", "/staff/", "/test/", "/_probe")
                        .stream().anyMatch(p -> path.startsWith(API + p))) continue;
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    String target = path.replaceAll("\\{[^}]+}", "1");
                    mockMvc.perform(request(method.asHttpMethod(), target)
                                    .header("Authorization", "Bearer " + access)
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                    routes.add(method + " " + path);
                }
            }
        }
        assertThat(routes.size()).isGreaterThanOrEqualTo(40);
        assertThat(routes).contains("GET /api/v1/accounts", "GET /api/v1/me/export");
    }

    @Test
    void promotionErasesOnlyTargetFinanceAndRevokesSessionsWithoutLeakingData() throws Exception {
        var admin = createUser(Role.ADMIN);
        Fixture target = seed("PromotionTarget", 95000, 18350, 72);
        Fixture other = seed("PromotionControl", 128000, 21500, 83);
        var targetBefore = finance(target);
        var otherBefore = finance(other);
        targetBefore.forEach((table, rows) -> assertThat(rows).as("target fixture %s", table).isNotEmpty());
        otherBefore.forEach((table, rows) -> assertThat(rows).as("control fixture %s", table).isNotEmpty());
        var targetIdentity = identity(target.id());
        var otherIdentity = identity(other.id());
        var otherSessions = sessions(other.id());
        assertThat(sessions(target.id())).hasSize(2);
        assertThat(otherSessions).hasSize(2);
        var sharedCategories = defaults();
        assertThat(sharedCategories).isNotEmpty();
        String actor = "Bearer " + tokenFor(admin);
        String route = API + "/admin/users/" + target.id() + "/promote-to-staff";
        mockMvc.perform(post(route).header("Authorization", actor).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("confirm", "NOT_CONFIRMED")))).andExpect(status().isBadRequest());
        assertThat(finance(target)).isEqualTo(targetBefore);
        assertThat(finance(other)).isEqualTo(otherBefore);
        assertThat(identity(target.id())).isEqualTo(targetIdentity);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, target.id())).isEqualTo("USER");

        var result = mockMvc.perform(post(route).header("Authorization", actor).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("confirm", CONFIRM)))).andExpect(status().isOk()).andReturn().getResponse();
        JsonNode summary = jsonMapper.readTree(result.getContentAsString());
        assertThat(summary.isObject()).isTrue();
        // D63 calls for a staff summary; the established staff summary has these fields.
        assertThat(summary.propertyNames()).containsOnly("id", "email", "fullName", "status", "pending");
        assertThat(summary.get("id").asLong()).isEqualTo(target.id());
        assertThat(summary.toString()).doesNotContain(PASSWORD, "PromotionTarget supermarket", "18350.75");
        entities.flush();
        entities.clear();

        assertThat(identity(target.id())).isEqualTo(targetIdentity);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, target.id())).isEqualTo("STAFF");
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, target.id())).isEqualTo("ACTIVE");
        finance(target).forEach((table, rows) -> assertThat(rows).as("erased %s", table).isEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",
                Long.class, target.id())).isZero();
        var audits = jdbc.queryForList("SELECT actor_id,target_id,reason FROM audit_events WHERE action='USER_PROMOTED_TO_STAFF' AND target_id=?",
                target.id());
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0)).containsEntry("actor_id", admin.getId()).containsEntry("target_id", target.id())
                .containsEntry("reason", null);

        for (Session old : List.of(target.first(), target.second())) {
            mockMvc.perform(get(API + "/accounts").header("Authorization", "Bearer " + old.access()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(API + "/staff/stats").header("Authorization", "Bearer " + old.access()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(API + "/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("refreshToken", old.refresh())))).andExpect(status().isUnauthorized());
        }
        var fresh = login(target.email(), "STAFF");
        mockMvc.perform(get(API + "/staff/stats").header("Authorization", "Bearer " + fresh.access()))
                .andExpect(status().isOk());
        assertStaffCannotReachUserRoutes(fresh.access());
        mockMvc.perform(get(API + "/accounts").header("Authorization", "Bearer " + other.first().access()))
                .andExpect(status().isOk());
        entities.flush();
        finance(target).forEach((table, rows) -> assertThat(rows).as("still erased %s", table).isEmpty());
        assertThat(finance(other)).isEqualTo(otherBefore);
        assertThat(identity(other.id())).isEqualTo(otherIdentity);
        assertThat(sessions(other.id())).isEqualTo(otherSessions);
        assertThat(defaults()).isEqualTo(sharedCategories);
    }
}
