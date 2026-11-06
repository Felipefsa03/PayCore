import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BaselinePerformance {
  static final String SECRET = "paycore-lab-dev-only-secret-change-me-0123456789abcdef";
  static final String BASE = "http://localhost:8090";

  static String b64(byte[] b){ return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  static String token(String owner) throws Exception {
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload = b64(("{\"sub\":\""+owner+"\",\"iss\":\"paycore-lab\",\"exp\":"+ (System.currentTimeMillis()/1000+3600) +"}").getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String sig = b64(mac.doFinal((header+"."+payload).getBytes(StandardCharsets.UTF_8)));
    return header+"."+payload+"."+sig;
  }

  static long percentile(List<Long> sorted, double p) {
    if (sorted.isEmpty()) return 0;
    int idx = (int) Math.ceil(p/100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(sorted.size()-1, idx));
    return sorted.get(idx);
  }

  public static void main(String[] args) throws Exception {
    String owner = "11111111-1111-1111-1111-111111111111";
    String jwt = token(owner);
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // create one account for GET tests
    HttpRequest createReq = HttpRequest.newBuilder(URI.create(BASE+"/accounts"))
        .header("Authorization","Bearer "+jwt)
        .header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"currency\":\"BRL\"}")).build();
    HttpResponse<String> createRes = client.send(createReq, HttpResponse.BodyHandlers.ofString());
    String accountId = createRes.body().split("\"id\":\"")[1].split("\"")[0];

    int threads = 16;
    int requests = 1000;
    // warmup
    for (int i=0;i<100;i++) {
      client.send(HttpRequest.newBuilder(URI.create(BASE+"/accounts/"+accountId)).header("Authorization","Bearer "+jwt).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger errors = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(requests);
    long start = System.nanoTime();
    for (int i=0;i<requests;i++) {
      pool.submit(() -> {
        long t0 = System.nanoTime();
        try {
          HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(BASE+"/accounts/"+accountId)).header("Authorization","Bearer "+jwt).GET().build(), HttpResponse.BodyHandlers.ofString());
          if (r.statusCode()!=200) errors.incrementAndGet();
        } catch (Exception e) { errors.incrementAndGet(); }
        latencies.add(System.nanoTime()-t0);
        latch.countDown();
      });
    }
    latch.await();
    long elapsed = System.nanoTime()-start;
    pool.shutdown();
    Collections.sort(latencies);
    double throughput = requests / (elapsed/1e9);
    System.out.println("SCENARIO=GET /accounts/{id}");
    System.out.println("THREADS="+threads);
    System.out.println("REQUESTS="+requests);
    System.out.println("ERRORS="+errors.get());
    System.out.println("THROUGHPUT_RPS="+String.format("%.2f", throughput));
    System.out.println("P50_MS="+String.format("%.3f", latencies.get(latencies.size()/2)/1e6));
    System.out.println("P95_MS="+String.format("%.3f", percentile(latencies,95)/1e6));
    System.out.println("P99_MS="+String.format("%.3f", percentile(latencies,99)/1e6));
    System.out.println("MAX_MS="+String.format("%.3f", latencies.get(latencies.size()-1)/1e6));
    System.out.println("MIN_MS="+String.format("%.3f", latencies.get(0)/1e6));

    // POST load
    int postThreads = 8;
    int postRequests = 200;
    List<Long> postLat = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger postErrors = new AtomicInteger();
    ExecutorService p2 = Executors.newFixedThreadPool(postThreads);
    CountDownLatch l2 = new CountDownLatch(postRequests);
    long pstart = System.nanoTime();
    for (int i=0;i<postRequests;i++) {
      p2.submit(() -> {
        long t0 = System.nanoTime();
        try {
          HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(BASE+"/accounts")).header("Authorization","Bearer "+jwt).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"currency\":\"BRL\"}")).build(), HttpResponse.BodyHandlers.ofString());
          if (r.statusCode()!=201) postErrors.incrementAndGet();
        } catch (Exception e) { postErrors.incrementAndGet(); }
        postLat.add(System.nanoTime()-t0);
        l2.countDown();
      });
    }
    l2.await();
    long pelapsed = System.nanoTime()-pstart;
    p2.shutdown();
    Collections.sort(postLat);
    System.out.println("SCENARIO=POST /accounts");
    System.out.println("THREADS="+postThreads);
    System.out.println("REQUESTS="+postRequests);
    System.out.println("ERRORS="+postErrors.get());
    System.out.println("THROUGHPUT_RPS="+String.format("%.2f", postRequests/(pelapsed/1e9)));
    System.out.println("P50_MS="+String.format("%.3f", postLat.get(postLat.size()/2)/1e6));
    System.out.println("P95_MS="+String.format("%.3f", percentile(postLat,95)/1e6));
    System.out.println("P99_MS="+String.format("%.3f", percentile(postLat,99)/1e6));
    System.out.println("MAX_MS="+String.format("%.3f", postLat.get(postLat.size()-1)/1e6));
    System.out.println("MIN_MS="+String.format("%.3f", postLat.get(0)/1e6));
  }
}
