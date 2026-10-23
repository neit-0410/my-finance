package com.neit.personal_finance;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class InvestmentIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StockTradeRepository trades;
    @Autowired StockQuoteRepository quotes;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<User> created = new ArrayList<>();
    record Account(User user, String token) {}
    private HttpResponse<String> request(String path, String token, Object body) throws Exception {
        var r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        if (token != null) r.header("Authorization", "Bearer " + token);
        if (body == null) r.GET();
        else r.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }
    private Account account() throws Exception {
        String name = "stock_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(200, request("/auth/register", null, Map.of("username", name, "email", name+"@example.com", "password", "Test-password-123")).statusCode());
        User user = users.findByUsername(name).orElseThrow(); created.add(user);
        var login = request("/auth/login", null, Map.of("username", name, "password", "Test-password-123"));
        assertEquals(200, login.statusCode());
        return new Account(user, json.readTree(login.body()).get("token").asText());
    }
    private Map<String,Object> trade(String symbol, String side, int quantity, int price, int fee, LocalDate date) {
        return new HashMap<>(Map.of("symbol",symbol,"side",side,"quantity",quantity,"price",price,"fee",fee,"tradeDate",date.toString()));
    }
    private JsonNode post(Account a, Map<String,Object> body) throws Exception {
        var result = request("/investments/trades", a.token(), body);
        assertEquals(200,result.statusCode(),result.body()); return json.readTree(result.body());
    }
    private void amount(JsonNode node, String field, String expected) {
        assertEquals(0,new BigDecimal(expected).compareTo(node.get(field).decimalValue()),field+": "+node);
    }
    @AfterEach void clean() {
        for (User u:created) {
            quotes.deleteAll(quotes.findByUserId(u.getId()));
            trades.deleteAll(trades.findByUserIdOrderByTradeDateAscIdAsc(u.getId()));
            users.deleteById(u.getId());
        }
    }
    @Test void weightedAverageFeesQuotesFullSaleAndReopening() throws Exception {
        Account a=account(); LocalDate date=LocalDate.now();
        post(a,trade("fpt","BUY",100,1000,100,date));
        var p=post(a,trade("FPT","BUY",100,2000,100,date));
        amount(p,"costBasis","300200"); assertTrue(p.get("marketValue").isNull());
        assertEquals(1,p.get("missingPrices").asInt());
        amount(p.get("positions").get(0),"averageCost","1501");
        p=post(a,trade("FPT","SELL",50,2500,50,date));
        amount(p,"realizedPnl","49900"); amount(p,"costBasis","225150");
        var result=request("/investments/prices",a.token(),Map.of("symbol","fpt","price",2200));
        assertEquals(200,result.statusCode());p=json.readTree(result.body());
        amount(p,"marketValue","330000");amount(p,"unrealizedPnl","104850");
        p=post(a,trade("FPT","SELL",150,1600,0,date));
        amount(p,"realizedPnl","64750"); amount(p,"costBasis","0");amount(p,"marketValue","0");
        assertEquals(0,p.get("positions").get(0).get("quantity").asLong());
        p=post(a,trade("FPT","BUY",10,900,0,date));
        amount(p.get("positions").get(0),"averageCost","900");amount(p,"realizedPnl","64750");
        assertFalse(p.toString().contains("password"));assertFalse(p.toString().contains("user"));
    }
    @Test void invalidSalesRollbackAndBackdatedTradesRecalculate() throws Exception {
        Account a=account();LocalDate date=LocalDate.now();
        assertEquals(400,request("/investments/trades",a.token(),trade("VNM","SELL",10,100,0,date)).statusCode());
        assertTrue(trades.findByUserIdOrderByTradeDateAscIdAsc(a.user().getId()).isEmpty());
        post(a,trade("VNM","BUY",100,1000,0,date));
        assertEquals(400,request("/investments/trades",a.token(),trade("VNM","SELL",1,2000,0,date.minusDays(1))).statusCode());
        post(a,trade("VNM","SELL",100,2000,0,date));
        var p=post(a,trade("VNM","BUY",100,500,0,date.minusDays(1)));
        amount(p,"realizedPnl","125000");amount(p,"costBasis","75000");
        assertEquals(3,p.get("trades").size());
    }
    @Test void accountsAreIsolatedAndPayloadCannotAssignOwner() throws Exception {
        Account a=account(),b=account();
        var body=trade("FPT","BUY",10,1000,0,LocalDate.now());body.put("userId",b.user().getId());
        post(a,body);
        assertEquals(0,json.readTree(request("/investments",b.token(),null).body()).get("positions").size());
        assertEquals(404,request("/investments/prices",b.token(),Map.of("symbol","FPT","price",5000)).statusCode());
        assertEquals(401,request("/investments",null,null).statusCode());
        assertEquals(401,request("/investments/trades",null,body).statusCode());
        assertEquals(1,trades.findByUserIdOrderByTradeDateAscIdAsc(a.user().getId()).size());
        assertEquals(0,trades.findByUserIdOrderByTradeDateAscIdAsc(b.user().getId()).size());
    }
    @Test void validatesQuantitiesFeesSymbolsAndFutureDates() throws Exception {
        Account a=account();
        for (var body:List.of(trade("FPT","BUY",0,1000,0,LocalDate.now()),trade("FPT","BUY",1,0,0,LocalDate.now()),
                trade("FPT","BUY",1,1000,-1,LocalDate.now()),trade("BAD SYMBOL","BUY",1,1000,0,LocalDate.now()),
                trade("FPT","BUY",1,1000,0,LocalDate.now().plusDays(1)))) {
            assertEquals(400,request("/investments/trades",a.token(),body).statusCode());
        }
        var body=trade("FPT","BUY",1,1000,0,LocalDate.now());body.put("price",new BigDecimal("1.00001"));
        assertEquals(400,request("/investments/trades",a.token(),body).statusCode());
        body=trade("FPT","BUY",1,1000,0,LocalDate.now());body.put("quantity",new BigDecimal("1.5"));
        assertEquals(400,request("/investments/trades",a.token(),body).statusCode());
        assertTrue(trades.findByUserIdOrderByTradeDateAscIdAsc(a.user().getId()).isEmpty());
    }
    @Test void concurrentSalesCannotOversell() throws Exception {
        Account a=account();post(a,trade("FPT","BUY",100,1000,0,LocalDate.now()));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> sell=()->{start.await();return request("/investments/trades",a.token(),trade("FPT","SELL",70,1100,0,LocalDate.now())).statusCode();};
            var first=pool.submit(sell);var second=pool.submit(sell);start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(200,400),statuses);
        } finally {pool.shutdownNow();}
        var p=json.readTree(request("/investments",a.token(),null).body());
        assertEquals(30,p.get("positions").get(0).get("quantity").asLong());assertEquals(2,p.get("trades").size());
    }
}
