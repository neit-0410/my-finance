package com.neit.personal_finance;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.*;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class IdempotencyIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired StockTradeRepository trades;
    @Autowired StockQuoteRepository quotes;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean IdempotencyRepository records;
    @Autowired ActivityLogRepository logs;
    @Autowired TransactionTemplate tx;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<User> created = new ArrayList<>();
    record Account(User user, String token) {}

    private HttpResponse<String> req(String path, String token, String key, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private Account account() throws Exception {
        String name = "idem_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(200, req("/auth/register", null, null,
                Map.of("username", name, "password", "Password-123!", "email", name + "@example.com")).statusCode());
        var user = users.findByUsername(name).orElseThrow();
        created.add(user);
        var result = req("/auth/login", null, null, Map.of("username", name, "password", "Password-123!"));
        return new Account(user, json.readTree(result.body()).get("token").asText());
    }
    private Map<String, Object> category(String name) { return Map.of("name", name, "type", "EXPENSE"); }
    private Map<String, Object> trade(String side, long quantity) {
        return Map.of("symbol", "FPT", "side", side, "quantity", quantity,
                "price", 100, "fee", 0, "tradeDate", LocalDate.now().toString());
    }
    @AfterEach void cleanup() {
        for (var user : created) {
            tx.executeWithoutResult(status -> records.deleteByUser_Id(user.getId()));
            transactions.deleteAll(transactions.findByUserId(user.getId()));
            categories.deleteAll(categories.findByUserId(user.getId()));
            trades.deleteAll(trades.findByUserIdOrderByTradeDateAscIdAsc(user.getId()));
            quotes.deleteAll(quotes.findByUserId(user.getId()));
            logs.deleteAll(logs.findByUserId(Long.toString(user.getId())));
            users.deleteById(user.getId());
        }
    }
    @Test void concurrentRequestsCreateOneRowAndReplayExactResponse() throws Exception {
        var owner = account();
        var pool = Executors.newFixedThreadPool(6);
        try {
            var gate = new CountDownLatch(1);
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> {
                gate.await();
                return req("/categories", owner.token(), "concurrent-key", category("Food"));
            }));
            gate.countDown();
            var responses = new ArrayList<HttpResponse<String>>();
            for (var future : futures) responses.add(future.get(20, TimeUnit.SECONDS));
            for (var result : responses) {
                assertEquals(200, result.statusCode(), result.body());
                assertEquals(responses.get(0).body(), result.body());
                assertFalse(result.body().contains("password"));
            }
            assertEquals(1, responses.stream().filter(r -> r.headers().firstValue("Idempotency-Replayed").orElseThrow().equals("false")).count());
            assertEquals(1, categories.findByUserId(owner.user().getId()).size());
            assertEquals(1, users.findById(owner.user().getId()).orElseThrow().getCacheRevision());
        } finally { pool.shutdownNow(); }
    }
    @Test void keysAreScopedToAccountAndRejectChangedPayloadOrEndpoint() throws Exception {
        var owner = account(); var other = account();
        var first = req("/categories", owner.token(), "shared-key", category("Food"));
        assertEquals(200, first.statusCode());
        assertEquals(409, req("/categories", owner.token(), "shared-key", category("Travel")).statusCode());
        assertEquals(409, req("/investments/prices", owner.token(), "shared-key", Map.of("symbol", "FPT", "price", 100)).statusCode());
        var second = req("/categories", other.token(), "shared-key", category("Food"));
        assertEquals(200, second.statusCode());
        assertNotEquals(first.body(), second.body());
        assertEquals(401, req("/categories", null, "shared-key", category("Food")).statusCode());
        assertEquals(400, req("/categories", owner.token(), "bad key", category("Food")).statusCode());
        // DTO serialization ignores input JSON property order.
        var replay = req("/categories", owner.token(), "shared-key", new LinkedHashMap<>(Map.of("type", "EXPENSE", "name", "Food")));
        assertEquals(first.body(), replay.body());
    }
    @Test void transactionReplayDoesNotRepeatSqlCacheInvalidationOrActivityLog() throws Exception {
        var owner = account();
        var cat = req("/categories", owner.token(), null, category("Food"));
        long categoryId = json.readTree(cat.body()).get("id").asLong();
        var body = Map.of("amount", 10, "type", "EXPENSE", "categoryId", categoryId, "transactionDate", "2026-10-01");
        var first = req("/transactions", owner.token(), "cash-key", body);
        assertEquals(200, first.statusCode(), first.body());
        var second = req("/transactions", owner.token(), "cash-key", body);
        assertEquals(first.body(), second.body());
        assertEquals("true", second.headers().firstValue("Idempotency-Replayed").orElseThrow());
        assertEquals(1, transactions.findByUserId(owner.user().getId()).size());
        assertEquals(2, users.findById(owner.user().getId()).orElseThrow().getCacheRevision());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, logs.findByUserId(Long.toString(owner.user().getId())).size()));
    }
    @Test void failedTradesRollBackKeyAndSuccessfulTradesAndPricesAreReplayed() throws Exception {
        var owner = account();
        assertEquals(400, req("/investments/trades", owner.token(), "sell-key", trade("SELL", 1)).statusCode());
        assertEquals(0L, (long) tx.execute(status -> records.findAll().stream().filter(r -> r.getUserId() == owner.user().getId()).count()));
        var buy = req("/investments/trades", owner.token(), "buy-key", trade("BUY", 5));
        assertEquals(200, buy.statusCode(), buy.body());
        assertEquals(buy.body(), req("/investments/trades", owner.token(), "buy-key", trade("BUY", 5)).body());
        var sell = req("/investments/trades", owner.token(), "sell-key", trade("SELL", 1));
        assertEquals(200, sell.statusCode(), sell.body());
        var price = req("/investments/prices", owner.token(), "price-key", Map.of("symbol", "FPT", "price", 120));
        assertEquals(200, price.statusCode(), price.body());
        assertEquals(price.body(), req("/investments/prices", owner.token(), "price-key", Map.of("symbol", "FPT", "price", 120)).body());
        assertEquals(2, trades.findByUserIdOrderByTradeDateAscIdAsc(owner.user().getId()).size());
        // A replay returns the historical response, even after the portfolio changes.
        assertEquals(buy.body(), req("/investments/trades", owner.token(), "buy-key", trade("BUY", 5)).body());
    }
    @Test void failureToSaveKeyRollsBackBusinessDataAndAllowsSafeRetry() throws Exception {
        var owner = account();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated record storage failure"))
                .when(records).save(org.mockito.ArgumentMatchers.any());
        try {
            assertEquals(500, req("/categories", owner.token(), "rollback-key", category("Food")).statusCode());
            assertEquals(0, categories.findByUserId(owner.user().getId()).size());
            assertEquals(0, users.findById(owner.user().getId()).orElseThrow().getCacheRevision());
        } finally {
            org.mockito.Mockito.reset(records);
        }
        var retry = req("/categories", owner.token(), "rollback-key", category("Food"));
        assertEquals(200, retry.statusCode(), retry.body());
        assertEquals("false", retry.headers().firstValue("Idempotency-Replayed").orElseThrow());
        assertEquals(1, categories.findByUserId(owner.user().getId()).size());
    }

    @Test void keyLengthAndCharacterBoundariesAreValidatedWithoutCreatingData() throws Exception {
        var owner = account();
        for (String key : List.of("", "bad key", "bad/key", "x".repeat(129))) {
            assertEquals(400, req("/categories", owner.token(), key, category("Boundary")).statusCode());
        }
        assertTrue(categories.findByUserId(owner.user().getId()).isEmpty());
        for (String key : List.of("x", "x".repeat(128), "valid._:-123")) {
            var first = req("/categories", owner.token(), key, category("Boundary"));
            var replay = req("/categories", owner.token(), key, category("Boundary"));
            assertEquals(200, first.statusCode(), first.body());
            assertEquals(first.body(), replay.body());
            assertEquals("true", replay.headers().firstValue("Idempotency-Replayed").orElseThrow());
        }
        assertEquals(3, categories.findByUserId(owner.user().getId()).size());
    }

    @Test void missingKeysAndDistinctCaseSensitiveKeysAllowIntentionalDuplicatePayloads() throws Exception {
        var owner = account();
        var ids = new HashSet<Long>();
        for (String key : Arrays.asList(null, null, "Case-Key", "case-key")) {
            var result = req("/categories", owner.token(), key, category("Same payload"));
            assertEquals(200, result.statusCode(), result.body());
            assertTrue(ids.add(json.readTree(result.body()).get("id").asLong()));
        }
        assertEquals(4, categories.findByUserId(owner.user().getId()).size());
    }

    @Test void forbiddenWriteDoesNotReserveKeyAndValidationFailureCanBeCorrected() throws Exception {
        var owner = account(); var other = account();
        var foreignCategory = req("/categories", other.token(), null, category("Other category"));
        var foreignId = json.readTree(foreignCategory.body()).get("id").asLong();
        var body = new HashMap<String, Object>(Map.of("amount", 10, "type", "EXPENSE",
                "categoryId", foreignId, "transactionDate", LocalDate.now().toString()));
        assertEquals(403, req("/transactions", owner.token(), "permission-key", body).statusCode());
        assertTrue(transactions.findByUserId(owner.user().getId()).isEmpty());
        var ownCategory = req("/categories", owner.token(), null, category("Own category"));
        body.put("categoryId", json.readTree(ownCategory.body()).get("id").asLong());
        var retry = req("/transactions", owner.token(), "permission-key", body);
        assertEquals(200, retry.statusCode(), retry.body());
        assertEquals("false", retry.headers().firstValue("Idempotency-Replayed").orElseThrow());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1, logs.findByUserId(Long.toString(owner.user().getId())).size()));
        assertEquals(400, req("/categories", owner.token(), "validation-key", category("")).statusCode());
        assertEquals(200, req("/categories", owner.token(), "validation-key", category("Corrected")).statusCode());
    }

}
