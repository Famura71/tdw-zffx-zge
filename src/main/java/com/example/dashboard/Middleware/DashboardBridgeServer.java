package com.example.dashboard.Middleware;

import com.example.dashboard.Messaging.InMemoryTopicBus;
import com.example.dashboard.Messaging.TopicSubscription;
import com.example.dashboard.Models.Brand;
import com.example.dashboard.Models.Car;
import com.example.dashboard.Models.CarStatistics;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class DashboardBridgeServer {
    private static final String TOPIC_STATS = "stats";

    private final HttpServer server;
    private final CarRepository carRepository = new CarRepository();
    private final InMemoryTopicBus bus = new InMemoryTopicBus();

    public DashboardBridgeServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", new StaticResourceHandler("/static/index.html", "text/html; charset=utf-8"));
        server.createContext("/index.html", new StaticResourceHandler("/static/index.html", "text/html; charset=utf-8"));
        server.createContext("/style.css", new ClasspathFileHandler("/static/style.css"));
        server.createContext("/view.js", new ClasspathFileHandler("/static/view.js"));

        server.createContext("/api/stats/brands", new BrandStatsHandler());
        server.createContext("/api/cars", new CarsHandler());
        server.createContext("/api/events", new SseEventsHandler());
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void publishStatsUpdate() {
        bus.publishSse(TOPIC_STATS, "statsUpdated", Json.objectCountsByBrand(carRepository.countByBrand()));
    }

    private final class BrandStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    sendText(exchange, 405, "Method Not Allowed");
                    return;
                }
                sendJson(exchange, 200, Json.objectCountsByBrand(carRepository.countByBrand()));
            } finally {
                exchange.close();
            }
        }
    }

    private final class CarsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
                Map<String, String> query = Query.parse(exchange.getRequestURI());

                if (method.equals("POST")) {
                    String brandParam = query.get("brand");
                    if (brandParam == null || brandParam.isBlank()) {
                        sendText(exchange, 400, "Missing query param: brand");
                        return;
                    }

                    Brand brand;
                    try {
                        brand = Brand.valueOf(brandParam.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        sendText(exchange, 400, "Invalid brand: " + brandParam);
                        return;
                    }

                    Car car = carRepository.addCar(brand);
                    publishStatsUpdate();
                    sendJson(exchange, 201, Json.car(car));
                    return;
                }

                if (method.equals("DELETE")) {
                    String id = query.get("id");
                    String brandParam = query.get("brand");
                    boolean removed;

                    if (id != null && !id.isBlank()) {
                        removed = carRepository.removeById(id.trim());
                    } else if (brandParam != null && !brandParam.isBlank()) {
                        Brand brand;
                        try {
                            brand = Brand.valueOf(brandParam.trim().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            sendText(exchange, 400, "Invalid brand: " + brandParam);
                            return;
                        }
                        removed = carRepository.removeOneByBrand(brand);
                    } else {
                        sendText(exchange, 400, "Missing query param: id or brand");
                        return;
                    }

                    if (!removed) {
                        sendText(exchange, 404, "Not Found");
                        return;
                    }
                    publishStatsUpdate();
                    sendText(exchange, 204, "");
                    return;
                }

                sendText(exchange, 405, "Method Not Allowed");
            } finally {
                exchange.close();
            }
        }
    }

    private final class SseEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                try {
                    sendText(exchange, 405, "Method Not Allowed");
                } finally {
                    exchange.close();
                }
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/event-stream; charset=utf-8");
            headers.set("Cache-Control", "no-cache");
            headers.set("Connection", "keep-alive");

            exchange.sendResponseHeaders(200, 0);

            TopicSubscription subscription = bus.subscribe(TOPIC_STATS);
            try (subscription; OutputStream out = exchange.getResponseBody()) {
                out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                out.write(("event: statsUpdated\n" + "data: " + Json.objectCountsByBrand(carRepository.countByBrand()) + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();

                while (true) {
                    String msg = subscription.take();
                    out.write(msg.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Client disconnected.
            } finally {
                exchange.close();
            }
        }
    }

    private static final class StaticResourceHandler implements HttpHandler {
        private final String resourcePath;
        private final String contentType;

        private StaticResourceHandler(String resourcePath, String contentType) {
            this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
            this.contentType = Objects.requireNonNull(contentType, "contentType");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    sendText(exchange, 405, "Method Not Allowed");
                    return;
                }
                byte[] bytes = readClasspathResource(resourcePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } finally {
                exchange.close();
            }
        }
    }

    private static final class ClasspathFileHandler implements HttpHandler {
        private final String resourcePath;

        private ClasspathFileHandler(String resourcePath) {
            this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    sendText(exchange, 405, "Method Not Allowed");
                    return;
                }
                byte[] bytes = readClasspathResource(resourcePath);

                String guessed = URLConnection.guessContentTypeFromName(resourcePath);
                String contentType = guessed != null ? guessed : "application/octet-stream";
                if (contentType.startsWith("text/") || contentType.equals("application/javascript")) {
                    contentType += "; charset=utf-8";
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } finally {
                exchange.close();
            }
        }
    }

    private static byte[] readClasspathResource(String path) throws IOException {
        try (InputStream in = DashboardBridgeServer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing classpath resource: " + path);
            }
            return in.readAllBytes();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final class CarRepository {
        private final List<Car> cars = new CopyOnWriteArrayList<>();

        private CarRepository() {
            seed();
        }

        private void seed() {
            cars.add(new Car(UUID.randomUUID().toString(), Brand.BMW));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.BMW));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.BMW));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.BMW));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.MERCEDES));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.MERCEDES));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.MERCEDES));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.TOYOTA));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.TOYOTA));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.TESLA));
            cars.add(new Car(UUID.randomUUID().toString(), Brand.HONDA));
        }

        public Car addCar(Brand brand) {
            Car car = new Car(UUID.randomUUID().toString(), brand);
            cars.add(car);
            return car;
        }

        public boolean removeById(String id) {
            return cars.removeIf(car -> Objects.equals(car.getId(), id));
        }

        public boolean removeOneByBrand(Brand brand) {
            for (Car car : cars) {
                if (car.getBrand() == brand) {
                    return cars.remove(car);
                }
            }
            return false;
        }

        public Map<Brand, Integer> countByBrand() {
            Map<Brand, Integer> counts = new EnumMap<>(Brand.class);
            counts.putAll(CarStatistics.countByBrand(cars));
            for (Brand brand : Brand.values()) {
                counts.putIfAbsent(brand, 0);
            }
            return counts;
        }
    }

    private static final class Query {
        private Query() {}

        public static Map<String, String> parse(URI uri) {
            String raw = uri.getRawQuery();
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            Map<String, String> params = new HashMap<>();
            for (String pair : raw.split("&")) {
                int idx = pair.indexOf('=');
                if (idx < 0) {
                    params.put(urlDecode(pair), "");
                } else {
                    String key = urlDecode(pair.substring(0, idx));
                    String value = urlDecode(pair.substring(idx + 1));
                    params.put(key, value);
                }
            }
            return params;
        }

        private static String urlDecode(String s) {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        }
    }

    private static final class Json {
        private Json() {}

        public static String objectCountsByBrand(Map<Brand, Integer> counts) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Brand brand : Brand.values()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(brand.name())).append('"').append(':').append(counts.getOrDefault(brand, 0));
            }
            sb.append('}');
            return sb.toString();
        }

        public static String car(Car car) {
            return "{"
                    + "\"id\":\"" + escape(car.getId()) + "\","
                    + "\"brand\":\"" + escape(car.getBrand().name()) + "\""
                    + "}";
        }

        private static String escape(String s) {
            StringBuilder out = new StringBuilder(s.length() + 8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(c);
                }
            }
            return out.toString();
        }
    }
}
