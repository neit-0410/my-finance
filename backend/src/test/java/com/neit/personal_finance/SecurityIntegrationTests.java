package com.neit.personal_finance;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.CategoryRepository;
import com.neit.personal_finance.repositories.jpa.TransactionRepository;
import com.neit.personal_finance.repositories.jpa.UserRepository;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class SecurityIntegrationTests {
    // Public fixture key for isolated tests only; never use this key in a running environment.
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String PASSWORD = "Test-password-123";
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired ActivityLogRepository logs;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<User> created = new ArrayList<>();

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private User register() throws Exception {
        String username = "security_" + UUID.randomUUID().toString().replace("-", "");
        var result = request("POST", "/api/auth/register", null,
                Map.of("username", username, "password", PASSWORD, "email", username + "@example.com", "role", "ROLE_ADMIN"));
        assertEquals(200, result.statusCode(), result.body());
        User user = users.findByUsername(username).orElseThrow();
        created.add(user);
        return user;
    }

    private String login(User user) throws Exception {
        var result = request("POST", "/api/auth/login", null,
                Map.of("username", user.getUsername(), "password", PASSWORD));
        assertEquals(200, result.statusCode(), result.body());
        return json.readTree(result.body()).get("token").asText();
    }

    @AfterEach
    void cleanUp() {
        for (User user : created) {
            transactions.deleteAll(transactions.findByUserId(user.getId()));
            categories.deleteAll(categories.findByUserId(user.getId()));
            logs.deleteAll(logs.findAll().stream().filter(log -> Long.toString(user.getId()).equals(log.getUserId())).toList());
            users.deleteById(user.getId());
        }
    }

    @Test
    void registrationHashesPasswordsAndLoginChecksCredentials() throws Exception {
        User user = register();
        assertNotEquals(PASSWORD, user.getPassword());
        assertTrue(encoder.matches(PASSWORD, user.getPassword()));
        assertEquals("ROLE_USER", user.getRole());
        assertNotNull(user.getCreateDt());
        String token = login(user);
        assertEquals(200, request("GET", "/api/categories/user/" + user.getId(), token, null).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("username", user.getUsername(), "password", "wrong-password")).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("username", "missing_" + UUID.randomUUID(), "password", PASSWORD)).statusCode());
        assertEquals(409, request("POST", "/api/auth/register", null,
                Map.of("username", user.getUsername(), "password", PASSWORD, "email", user.getEmail())).statusCode());
        assertEquals(400, request("POST", "/api/auth/register", null,
                Map.of("username", "invalid", "password", "short", "email", "bad")).statusCode());
        assertEquals(400, request("POST", "/api/auth/register", null,
                Map.of("username", "invalid", "password", "é".repeat(40), "email", "valid@example.com")).statusCode());
        assertEquals(400, request("POST", "/api/auth/login", null, Map.of("username", "", "password", "")).statusCode());
    }

    @Test
    void protectedApisRejectMissingMalformedExpiredAndForgedTokens() throws Exception {
        User user = register();
        String path = "/api/categories/user/" + user.getId();
        assertEquals(401, request("GET", path, null, null).statusCode());
        assertEquals(401, request("GET", path, "not-a-token", null).statusCode());
        assertEquals(401, request("POST", "/api/categories", null, Map.of("name", "x", "type", "EXPENSE")).statusCode());
        assertEquals(401, request("GET", "/", null, null).statusCode());
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY));
        String expired = Jwts.builder().subject(user.getUsername()).expiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(key).compact();
        assertEquals(401, request("GET", path, expired, null).statusCode());
        String forged = Jwts.builder().subject(user.getUsername()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(new byte[64])).compact();
        assertEquals(401, request("GET", path, forged, null).statusCode());
        String noExpiration = Jwts.builder().subject(user.getUsername()).signWith(key).compact();
        assertEquals(401, request("GET", path, noExpiration, null).statusCode());
        String deletedUser = Jwts.builder().subject("deleted_" + UUID.randomUUID())
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();
        assertEquals(401, request("GET", path, deletedUser, null).statusCode());
        var response = request("GET", path, login(user), null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues("set-cookie").isEmpty());
    }

    @Test
    void usersCannotReadOrCreateOtherUsersFinancialData() throws Exception {
        User owner = register();
        User other = register();
        String token = login(owner);
        String otherToken = login(other);
        var categoryResult = request("POST", "/api/categories", token,
                Map.of("id", 999999, "name", "Food", "type", "EXPENSE", "user", Map.of("id", other.getId())));
        assertEquals(200, categoryResult.statusCode(), categoryResult.body());
        JsonNode category = json.readTree(categoryResult.body());
        long categoryId = category.get("id").asLong();
        assertEquals(owner.getId(), category.get("user").get("id").asLong());
        assertFalse(categoryResult.body().contains("password"));
        assertEquals(403, request("GET", "/api/categories/user/" + owner.getId(), otherToken, null).statusCode());
        assertEquals(403, request("GET", "/api/transactions/user/" + owner.getId(), otherToken, null).statusCode());
        assertEquals(403, request("GET", "/api/transactions/user/" + owner.getId()
                + "/range?start=2026-01-01&end=2026-12-31", otherToken, null).statusCode());
        var body = Map.of("amount", 10, "type", "EXPENSE", "categoryId", categoryId,
                "transactionDate", "2026-10-13", "user", Map.of("id", other.getId()));
        assertEquals(403, request("POST", "/api/transactions", otherToken, body).statusCode());
        var saved = request("POST", "/api/transactions", token, body);
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(owner.getId(), json.readTree(saved.body()).get("user").get("id").asLong());
        assertFalse(saved.body().contains("password"));
        assertEquals(1, transactions.findByUserId(owner.getId()).size());
        assertEquals(0, transactions.findByUserId(other.getId()).size());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertTrue(logs.findByUserId(Long.toString(owner.getId())).stream()
                        .anyMatch(log -> log.getAction().equals("CREATE_TRANSACTION") && log.getTimestamp() != null)));
        var range = request("GET", "/api/transactions/user/" + owner.getId()
                + "/range?start=2026-01-01&end=2026-12-31", token, null);
        assertEquals(200, range.statusCode());
        assertEquals(1, json.readTree(range.body()).size());
        assertEquals(400, request("GET", "/api/transactions/user/" + owner.getId()
                + "/range?start=2026-12-31&end=2026-01-01", token, null).statusCode());
        assertEquals(400, request("POST", "/api/transactions", token,
                Map.of("amount", -1, "type", "EXPENSE", "categoryId", categoryId, "transactionDate", "2026-10-13")).statusCode());
    }
}
