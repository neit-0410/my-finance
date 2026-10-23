package com.neit.personal_finance;

import com.neit.personal_finance.cache.UserReadCache;
import com.neit.personal_finance.entities.*;
import com.neit.personal_finance.repositories.jpa.*;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RedisCacheIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @MockitoSpyBean CategoryRepository categories;
    @MockitoSpyBean TransactionRepository transactions;
    @MockitoSpyBean StockTradeRepository trades;
    @Autowired StockQuoteRepository quotes;
    @Autowired ActivityLogRepository logs;
    @Autowired StringRedisTemplate redis;
    @Autowired UserReadCache cache;
    private final HttpClient http=HttpClient.newHttpClient();
    private final List<User> created=new ArrayList<>();
    record Account(User user,String token) {}
    private HttpResponse<String> req(String path,String token,Object body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private Account account() throws Exception {
        String name="redis_"+UUID.randomUUID().toString().replace("-","");
        assertEquals(200,req("/auth/register",null,Map.of("username",name,"email",name+"@example.com","password","Redis-test-password-123")).statusCode());
        User user=users.findByUsername(name).orElseThrow();created.add(user);
        var login=req("/auth/login",null,Map.of("username",name,"password","Redis-test-password-123"));
        assertEquals(200,login.statusCode());return new Account(user,json.readTree(login.body()).get("token").asText());
    }
    private User fresh(Account a){return users.findById(a.user().getId()).orElseThrow();}
    private JsonNode ok(String path,Account a,Object body) throws Exception {
        var response=req(path,a.token(),body);assertEquals(200,response.statusCode(),response.body());return json.readTree(response.body());
    }
    private String key(Account a,String resource){return UserReadCache.key(fresh(a),resource);}
    @AfterEach void clean() {
        for(User u:created){
            var keys=new ArrayList<String>();
            try(var cursor=redis.scan(ScanOptions.scanOptions().match("finance:cache:v1:user:"+u.getId()+":*").count(100).build())){cursor.forEachRemaining(keys::add);}
            if(!keys.isEmpty())redis.delete(keys);
            quotes.deleteAll(quotes.findByUserId(u.getId()));
            trades.deleteAll(trades.findByUserIdOrderByTradeDateAscIdAsc(u.getId()));
            transactions.deleteAll(transactions.findByUserId(u.getId()));
            categories.deleteAll(categories.findByUserId(u.getId()));
            logs.deleteAll(logs.findAll().stream().filter(l->Long.toString(u.getId()).equals(l.getUserId())).toList());
            users.deleteById(u.getId());
        }
    }
    @Test void categoriesHitBypassesRepositoryHasTtlAndNoCredentials() throws Exception {
        var a=account();ok("/categories",a,Map.of("name","Food","type","EXPENSE"));clearInvocations(categories);
        String path="/categories/user/"+a.user().getId();
        var first=ok(path,a,null);assertEquals(first,ok(path,a,null));
        verify(categories,times(1)).findByUserId(a.user().getId());
        String payload=redis.opsForValue().get(key(a,"categories"));assertNotNull(payload);
        assertFalse(payload.contains("password"));assertFalse(payload.contains("Redis-test-password"));assertFalse(payload.contains("cacheRevision"));
        Long ttl=redis.getExpire(key(a,"categories"));assertNotNull(ttl);assertTrue(ttl>0&&ttl<=60);
        ok("/categories",a,Map.of("name","Salary","type","INCOME"));
        assertEquals(2,ok(path,a,null).size());
    }
    @Test void transactionAndDateRangeKeysAreDistinctAndRefreshedAfterWrites() throws Exception {
        var a=account();long category=ok("/categories",a,Map.of("name","Food","type","EXPENSE")).get("id").asLong();
        String path="/transactions/user/"+a.user().getId();LocalDate day=LocalDate.now();
        assertEquals(0,ok(path,a,null).size());
        ok("/transactions",a,Map.of("amount",50000,"type","EXPENSE","categoryId",category,"transactionDate",day.toString()));
        clearInvocations(transactions);assertEquals(1,ok(path,a,null).size());assertEquals(1,ok(path,a,null).size());
        verify(transactions,times(1)).findByUserId(a.user().getId());
        String range=path+"/range?start="+day+"&end="+day;
        assertEquals(1,ok(range,a,null).size());assertEquals(1,ok(range,a,null).size());
        verify(transactions,times(1)).findByUserIdAndTransactionDateBetween(a.user().getId(),day,day);
        assertEquals(0,ok(path+"/range?start="+day.minusDays(2)+"&end="+day.minusDays(1),a,null).size());
    }
    @Test void investmentReadHitsCacheButPricesTradesAndRollbacksStayCorrect() throws Exception {
        var a=account();var body=Map.of("symbol","FPT","side","BUY","quantity",100,"price",1000,"fee",0,"tradeDate",LocalDate.now().toString());
        ok("/investments/trades",a,body);clearInvocations(trades);
        assertEquals(ok("/investments",a,null),ok("/investments",a,null));
        verify(trades,times(1)).findByUserIdOrderByTradeDateAscIdAsc(a.user().getId());
        ok("/investments/prices",a,Map.of("symbol","FPT","price",1200));
        assertEquals(20000,ok("/investments",a,null).get("unrealizedPnl").asInt());
        long revision=fresh(a).getCacheRevision();String before=redis.opsForValue().get(key(a,"portfolio"));
        assertEquals(400,req("/investments/trades",a.token(),Map.of("symbol","FPT","side","SELL","quantity",101,"price",1500,"fee",0,"tradeDate",LocalDate.now().toString())).statusCode());
        assertEquals(revision,fresh(a).getCacheRevision());assertEquals(before,redis.opsForValue().get(key(a,"portfolio")));
        ok("/investments/trades",a,Map.of("symbol","FPT","side","SELL","quantity",40,"price",1500,"fee",0,"tradeDate",LocalDate.now().toString()));
        assertEquals(60,ok("/investments",a,null).get("positions").get(0).get("quantity").asInt());
    }
    @Test void warmCacheCannotBypassOwnershipAndExpirationReloadsDatabase() throws Exception {
        var a=account();var b=account();String path="/categories/user/"+a.user().getId();
        ok(path,a,null);assertEquals(403,req(path,b.token(),null).statusCode());assertEquals(401,req(path,null,null).statusCode());
        clearInvocations(categories);redis.expire(key(a,"categories"),Duration.ofMillis(1));
        for(int i=0;i<20&&Boolean.TRUE.equals(redis.hasKey(key(a,"categories")));i++)Thread.sleep(10);
        assertEquals(0,ok(path,a,null).size());verify(categories,times(1)).findByUserId(a.user().getId());
    }
    @Test void lateCacheFillCannotRestoreDataFromBeforeCommittedWrite() throws Exception {
        var a=account();User stale=fresh(a);var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try{
            var oldRead=pool.submit(()->cache.get(stale,"categories",new TypeReference<List<Category>>(){},()->{
                started.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}return List.of();
            }));
            assertTrue(started.await(5,TimeUnit.SECONDS));
            ok("/categories",a,Map.of("name","New data","type","EXPENSE"));
            assertEquals(1,ok("/categories/user/"+a.user().getId(),a,null).size());
            release.countDown();assertTrue(oldRead.get(5,TimeUnit.SECONDS).isEmpty());
            assertEquals(1,ok("/categories/user/"+a.user().getId(),a,null).size());
        }finally{release.countDown();pool.shutdownNow();}
    }
}
