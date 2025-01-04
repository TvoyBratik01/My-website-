import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Random;

public class SimpleKeyStore {

    private static final String KEY_FILE = "1.txt"; // Файл с ключами
    private static final List<String> availableKeys = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        // Загружаем ключи в память
        loadKeys();

        // Создаём HTTP-сервер
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new HtmlHandler("index.html"));
        server.createContext("/product.html", new HtmlHandler("product.html"));
        server.createContext("/contacts.html", new HtmlHandler("contacts.html"));
        server.createContext("/style.css", new StaticFileHandler("style.css", "text/css"));
        server.createContext("/product.jpg", new StaticFileHandler("product.jpg", "image/jpeg"));
        server.createContext("/buy-key", new BuyKeyHandler());
        server.setExecutor(null); // Создаём простой исполнитель
        server.start();

        System.out.println("Сервер запущен на http://localhost:8080");
    }

    private static void loadKeys() throws IOException {
        availableKeys.addAll(Files.readAllLines(Paths.get(KEY_FILE)));
    }

    // Обработчик для статических файлов
    private static class StaticFileHandler implements HttpHandler {
        private final String filePath;
        private final String contentType;

        public StaticFileHandler(String filePath, String contentType) {
            this.filePath = filePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                // Чтение файла и отправка его содержимого
                byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, fileBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(fileBytes);
                os.close();
            }
        }
    }

    // Обработчик для HTML страниц
    private static class HtmlHandler implements HttpHandler {
        private final String page;

        public HtmlHandler(String page) {
            this.page = page;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                // Чтение содержимого HTML файла
                String response = new String(Files.readAllBytes(Paths.get(page)));

                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    // Обработчик для получения ключа
    private static class BuyKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String response;
                String keyResult;

                synchronized (availableKeys) {
                    if (availableKeys.isEmpty()) {
                        keyResult = "Ключи закончились!";
                    } else {
                        // Получаем случайный ключ
                        String key = availableKeys.remove(new Random().nextInt(availableKeys.size()));

                        // Сохраняем оставшиеся ключи обратно в файл
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(KEY_FILE))) {
                            for (String remainingKey : availableKeys) {
                                writer.write(remainingKey);
                                writer.newLine();
                            }
                        }

                        keyResult = "Ваш ключ: " + key;
                    }
                }

                // Чтение HTML страницы и замена ключа на результат
                String htmlResponse = new String(Files.readAllBytes(Paths.get("product.html")));
                htmlResponse = htmlResponse.replace("${keyResult}", keyResult); // Заменяем в шаблоне

                // Отправка ответа клиенту
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, htmlResponse.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(htmlResponse.getBytes());
                os.close();
            }
        }
    }
}
