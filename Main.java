import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Main {
    private static final Path DATA_DIR = resolveDataDir();
    private static final Path BOOKS_FILE = DATA_DIR.resolve("books.csv");
    private static final Path LOANS_FILE = DATA_DIR.resolve("loans.csv");
    private static final Path ACCOUNTS_FILE = DATA_DIR.resolve("accounts.csv");
    private static final List<Book> books = new ArrayList<>();
    private static final List<Loan> loans = new ArrayList<>();
    private static final Map<String, Account> accounts = new LinkedHashMap<>();
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final AtomicInteger nextBookId = new AtomicInteger(1);
    private static final AtomicInteger nextLoanId = new AtomicInteger(1);
    private static final int PASSWORD_ITERATIONS = 120_000;

    record Book(int id, String title, String author, String isbn, String category, int copies, int available) {}
    record Loan(int id, int bookId, String title, String student, String dueDate, boolean returned) {}
    record Account(String name, String email, String salt, String passwordHash) {}

    private static Path resolveDataDir() {
        String envDir = System.getenv("DATA_DIR");
        if (envDir != null && !envDir.isBlank()) {
            return Path.of(envDir);
        }
        if (System.getenv("VERCEL") != null || System.getenv("VERCEL_ENV") != null) {
            return Path.of("/tmp", "data");
        }
        Path dir = Path.of("data");
        try {
            Files.createDirectories(dir);
            Path testFile = dir.resolve(".write_test");
            Files.writeString(testFile, "test");
            Files.deleteIfExists(testFile);
            return dir;
        } catch (Exception e) {
            return Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "data");
        }
    }

    private static void seedIfMissing(Path target, Path... candidates) {
        if (Files.exists(target)) return;
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && !candidate.toAbsolutePath().equals(target.toAbsolutePath())) {
                try {
                    Files.copy(candidate, target);
                    return;
                } catch (IOException ignored) {}
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA_DIR);
        loadData();
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        } else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {}
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("I2IT Library is running at http://localhost:" + port);
    }

    private static synchronized void loadData() throws IOException {
        Files.createDirectories(DATA_DIR);
        seedIfMissing(BOOKS_FILE, Path.of("books.csv"), Path.of("data", "books.csv"));
        seedIfMissing(LOANS_FILE, Path.of("loans.csv"), Path.of("data", "loans.csv"));
        seedIfMissing(ACCOUNTS_FILE, Path.of("accounts.csv"), Path.of("data", "accounts.csv"));
        if (Files.exists(BOOKS_FILE)) {
            for (String line : Files.readAllLines(BOOKS_FILE, StandardCharsets.UTF_8)) {
                List<String> row = parseCsv(line);
                if (row.size() == 7) {
                    books.add(new Book(Integer.parseInt(row.get(0)), row.get(1), row.get(2), row.get(3),
                            row.get(4), Integer.parseInt(row.get(5)), Integer.parseInt(row.get(6))));
                    nextBookId.set(Math.max(nextBookId.get(), Integer.parseInt(row.get(0)) + 1));
                }
            }
        } else {
            books.addAll(List.of(
                    new Book(1, "The Pragmatic Programmer", "David Thomas & Andrew Hunt", "978-0135957059", "Technology", 5, 4),
                    new Book(2, "Clean Code", "Robert C. Martin", "978-0132350884", "Technology", 4, 3),
                    new Book(3, "Design of Everyday Things", "Don Norman", "978-0465050659", "Design", 3, 3),
                    new Book(4, "Wings of Fire", "A. P. J. Abdul Kalam", "978-8173711466", "Biography", 6, 5),
                    new Book(5, "The Alchemist", "Paulo Coelho", "978-0062315007", "Fiction", 4, 2),
                    new Book(6, "Introduction to Algorithms", "Thomas H. Cormen", "978-0262046305", "Technology", 3, 2),
                    new Book(7, "Atomic Habits", "James Clear", "978-0735211292", "Self development", 5, 5),
                    new Book(8, "Sapiens", "Yuval Noah Harari", "978-0062316097", "History", 3, 2)
            ));
            nextBookId.set(9);
            saveBooks();
        }
        if (Files.exists(LOANS_FILE)) {
            for (String line : Files.readAllLines(LOANS_FILE, StandardCharsets.UTF_8)) {
                List<String> row = parseCsv(line);
                if (row.size() == 6) {
                    loans.add(new Loan(Integer.parseInt(row.get(0)), Integer.parseInt(row.get(1)), row.get(2),
                            row.get(3), row.get(4), Boolean.parseBoolean(row.get(5))));
                    nextLoanId.set(Math.max(nextLoanId.get(), Integer.parseInt(row.get(0)) + 1));
                }
            }
        } else {
            loans.add(new Loan(1, 1, books.get(0).title(), "Aarav Patil · CSE-2041", LocalDate.now().plusDays(12).toString(), false));
            loans.add(new Loan(2, 4, books.get(3).title(), "Mira Shah · IT-2038", LocalDate.now().plusDays(8).toString(), false));
            loans.add(new Loan(3, 5, books.get(4).title(), "Ishaan Joshi · ENTC-2027", LocalDate.now().plusDays(4).toString(), false));
            nextLoanId.set(4);
            saveLoans();
        }
        if (Files.exists(ACCOUNTS_FILE)) {
            for (String line : Files.readAllLines(ACCOUNTS_FILE, StandardCharsets.UTF_8)) {
                List<String> row = parseCsv(line);
                if (row.size() == 4) {
                    accounts.put(row.get(1), new Account(row.get(0), row.get(1), row.get(2), row.get(3)));
                }
            }
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String email = getSessionEmail(exchange);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (path.equals("/register")) {
                    register(exchange, form);
                    return;
                }
                if (path.equals("/login")) {
                    login(exchange, form);
                    return;
                }
                if (path.equals("/logout")) {
                    logout(exchange);
                    return;
                }
                if (email == null) {
                    redirect(exchange, "/login?message=" + encode("Please sign in to continue."));
                    return;
                }
                String message;
                synchronized (Main.class) {
                    message = switch (path) {
                        case "/books/add" -> addBook(form);
                        case "/books/borrow" -> borrowBook(form);
                        case "/loans/return" -> returnBook(form);
                        case "/books/delete" -> deleteBook(form);
                        default -> "That action could not be found.";
                    };
                }
                redirect(exchange, "/?message=" + encode(message));
                return;
            }
            if (path.equals("/logout")) {
                redirect(exchange, "/");
                return;
            }
            if (path.equals("/login") || path.equals("/register")) {
                if (email != null) {
                    redirect(exchange, "/");
                    return;
                }
                Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery() == null
                        ? "" : exchange.getRequestURI().getRawQuery());
                send(exchange, 200, "text/html; charset=utf-8", renderAuth(path.equals("/register"), value(query, "message")));
                return;
            }
            if (!"/".equals(path)) {
                send(exchange, 404, "text/plain; charset=utf-8", "Page not found");
                return;
            }
            if (email == null) {
                redirect(exchange, "/login");
                return;
            }
            Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery() == null
                    ? "" : exchange.getRequestURI().getRawQuery());
            synchronized (Main.class) {
                send(exchange, 200, "text/html; charset=utf-8", render(query, accounts.get(email).name()));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            send(exchange, 500, "text/plain; charset=utf-8", "Something went wrong. Check the server console for details.");
        } finally {
            exchange.close();
        }
    }

    private static synchronized void register(HttpExchange exchange, Map<String, String> form) throws Exception {
        String name = value(form, "name").trim();
        String email = value(form, "email").trim().toLowerCase();
        String password = value(form, "password");
        if (name.isBlank() || name.length() > 80 || !email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")
                || password.length() < 8 || password.length() > 128) {
            redirect(exchange, "/register?message=" + encode("Enter your name, a valid email, and a password of at least 8 characters."));
            return;
        }
        if (accounts.containsKey(email)) {
            redirect(exchange, "/register?message=" + encode("An account already exists for that email. Please sign in."));
            return;
        }
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        Account account = new Account(name, email, salt, hashPassword(password, saltBytes));
        accounts.put(email, account);
        saveAccounts();
        startSession(exchange, email);
        redirect(exchange, "/");
    }

    private static synchronized void login(HttpExchange exchange, Map<String, String> form) throws Exception {
        String email = value(form, "email").trim().toLowerCase();
        Account account = accounts.get(email);
        if (account == null || !MessageDigest.isEqual(
                hashPassword(value(form, "password"), Base64.getDecoder().decode(account.salt()))
                        .getBytes(StandardCharsets.UTF_8),
                account.passwordHash().getBytes(StandardCharsets.UTF_8))) {
            redirect(exchange, "/login?message=" + encode("Email or password is incorrect."));
            return;
        }
        startSession(exchange, email);
        redirect(exchange, "/");
    }

    private static String hashPassword(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    private static void startSession(HttpExchange exchange, String email) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, email);
        exchange.getResponseHeaders().add("Set-Cookie",
                "I2IT_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200");
    }

    private static String getSessionEmail(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals("I2IT_SESSION")) return sessions.get(parts[1]);
        }
        return null;
    }

    private static void logout(HttpExchange exchange) throws IOException {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals("I2IT_SESSION")) sessions.remove(parts[1]);
            }
        }
        exchange.getResponseHeaders().add("Set-Cookie", "I2IT_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        redirect(exchange, "/login?message=" + encode("You have been signed out."));
    }

    private static void saveAccounts() throws IOException {
        List<String> rows = accounts.values().stream()
                .map(account -> csv(account.name(), account.email(), account.salt(), account.passwordHash())).toList();
        Files.write(ACCOUNTS_FILE, rows, StandardCharsets.UTF_8);
    }

    private static String addBook(Map<String, String> form) throws IOException {
        String title = value(form, "title").trim();
        String author = value(form, "author").trim();
        String isbn = value(form, "isbn").trim();
        String category = value(form, "category").trim();
        int copies;
        try {
            copies = Integer.parseInt(value(form, "copies"));
        } catch (NumberFormatException exception) {
            return "Enter a valid number of copies.";
        }
        if (title.isBlank() || author.isBlank() || isbn.isBlank() || category.isBlank() || copies < 1 || copies > 999) {
            return "Please complete every field and enter 1–999 copies.";
        }
        if (books.stream().anyMatch(book -> book.isbn().equalsIgnoreCase(isbn))) {
            return "A book with that ISBN is already in the catalog.";
        }
        books.add(new Book(nextBookId.getAndIncrement(), title, author, isbn, category, copies, copies));
        saveBooks();
        return "“" + title + "” was added to the catalog.";
    }

    private static String borrowBook(Map<String, String> form) throws IOException {
        Book book = findBook(form.get("bookId"));
        String student = value(form, "student").trim();
        if (book == null || student.isBlank()) return "Choose a book and enter the student's name or ID.";
        if (book.available() < 1) return "There are no available copies of that book.";
        books.set(books.indexOf(book), new Book(book.id(), book.title(), book.author(), book.isbn(),
                book.category(), book.copies(), book.available() - 1));
        loans.add(new Loan(nextLoanId.getAndIncrement(), book.id(), book.title(), student,
                LocalDate.now().plusDays(14).toString(), false));
        saveBooks();
        saveLoans();
        return "“" + book.title() + "” checked out to " + student + ".";
    }

    private static String returnBook(Map<String, String> form) throws IOException {
        int id = parseInt(value(form, "loanId"), -1);
        for (int i = 0; i < loans.size(); i++) {
            Loan loan = loans.get(i);
            if (loan.id() == id && !loan.returned()) {
                loans.set(i, new Loan(loan.id(), loan.bookId(), loan.title(), loan.student(), loan.dueDate(), true));
                Book book = findBook(Integer.toString(loan.bookId()));
                if (book != null) {
                    books.set(books.indexOf(book), new Book(book.id(), book.title(), book.author(), book.isbn(),
                            book.category(), book.copies(), Math.min(book.copies(), book.available() + 1)));
                    saveBooks();
                }
                saveLoans();
                return "“" + loan.title() + "” has been returned.";
            }
        }
        return "That active loan could not be found.";
    }

    private static String deleteBook(Map<String, String> form) throws IOException {
        Book book = findBook(value(form, "bookId"));
        if (book == null) return "That book could not be found.";
        if (book.available() != book.copies()) return "Return all copies before removing this book.";
        books.remove(book);
        saveBooks();
        return "“" + book.title() + "” was removed from the catalog.";
    }

    private static String renderAuth(boolean createAccount, String message) {
        String title = createAccount ? "Create your account" : "Welcome back";
        String description = createAccount
                ? "Join the I2IT campus library and keep knowledge moving."
                : "Sign in to continue to your campus library.";
        StringBuilder html = new StringBuilder(5000);
        html.append("""
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>""").append(title).append("""
                · I2IT Library</title><style>
                :root{--ink:#17201d;--muted:#75817a;--green:#226b50;--green-dark:#174b39;--paper:#f5f7f1;--lime:#d8f07d;--line:#e7ebe3}
                *{box-sizing:border-box}body{margin:0;min-height:100vh;background:var(--paper);color:var(--ink);font:14px/1.5 "Segoe UI",Arial,sans-serif;display:grid;place-items:center;padding:24px}
                .auth-layout{width:min(920px,100%);min-height:540px;display:grid;grid-template-columns:1fr 1fr;background:white;border:1px solid var(--line);border-radius:20px;overflow:hidden;box-shadow:0 18px 55px #18352712}
                .auth-brand{background:#173d32;color:#eef5ed;padding:42px;display:flex;flex-direction:column;justify-content:space-between;position:relative;overflow:hidden}
                .auth-brand:after{content:"";position:absolute;width:330px;height:330px;border:1px solid #ffffff20;border-radius:50%;right:-130px;bottom:-115px;box-shadow:0 0 0 38px #ffffff08,0 0 0 78px #ffffff06}
                .brand{display:flex;align-items:center;gap:11px;position:relative;z-index:1}.brand-mark{width:39px;height:39px;border-radius:12px;background:var(--lime);color:#163c30;display:grid;place-items:center;font-size:21px;font-weight:800}
                .brand strong{font-size:15px}.brand small{display:block;color:#a8c2b5;font-size:11px;margin-top:1px}
                .brand-copy{position:relative;z-index:1;max-width:330px;margin:55px 0}.brand-copy .eyebrow{font-size:10px;text-transform:uppercase;letter-spacing:.16em;color:#b8d29b;font-weight:700}
                .brand-copy h2{font-size:34px;line-height:1.16;letter-spacing:-.04em;margin:12px 0}.brand-copy p{color:#bed0c4;font-size:13px;max-width:290px}
                .brand-foot{position:relative;z-index:1;color:#a8c2b5;font-size:11px}.auth-form-wrap{padding:42px clamp(25px,6vw,65px);display:flex;flex-direction:column;justify-content:center}
                .form-eyebrow{font-size:10px;color:var(--green);font-weight:700;text-transform:uppercase;letter-spacing:.13em}.auth-form-wrap h1{font-size:27px;letter-spacing:-.04em;margin:7px 0 4px}
                .auth-form-wrap .description{color:var(--muted);font-size:12px;margin:0 0 24px}.auth-form{display:grid;gap:14px}
                .auth-form label{display:grid;gap:6px;color:#46524a;font-size:11px;font-weight:600}.auth-form input{width:100%;padding:11px 12px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink);font:13px "Segoe UI",Arial,sans-serif;outline:none}
                .auth-form input:focus{border-color:#82aa8c;box-shadow:0 0 0 3px #226b5012}.auth-form input::placeholder{color:#aab1ab}
                .primary{border:0;background:var(--green);color:white;border-radius:8px;padding:11px 14px;font-size:12px;font-weight:700;cursor:pointer;margin-top:3px}.primary:hover{background:var(--green-dark)}
                .switch{margin-top:20px;color:var(--muted);font-size:11px;text-align:center}.switch a{color:var(--green);font-weight:700;text-decoration:none}.switch a:hover{text-decoration:underline}
                .flash{padding:10px 12px;margin:0 0 15px;border-radius:8px;background:#fff1ed;color:#9c4438;font-size:11px}
                @media(max-width:680px){body{padding:12px}.auth-layout{grid-template-columns:1fr;min-height:0}.auth-brand{padding:20px 22px}.brand-copy,.brand-foot{display:none}.auth-form-wrap{padding:35px 25px 30px}.auth-form-wrap h1{font-size:24px}}
                </style></head><body><div class="auth-layout"><section class="auth-brand"><div class="brand"><div class="brand-mark">i</div><div><strong>I2IT Library</strong><small>Knowledge, organized.</small></div></div>
                <div class="brand-copy"><div class="eyebrow">Campus library · Hinjawadi, Pune</div><h2>A little curiosity goes a long way.</h2><p>Find your next great read, explore new ideas, and keep the I2IT community learning together.</p></div>
                <div class="brand-foot">International Institute of Information Technology · Est. 2003</div></section><main class="auth-form-wrap">
                <div class="form-eyebrow">I2IT campus library</div><h1>""").append(title).append("</h1><p class=\"description\">")
                .append(description).append("</p>");
        if (message != null && !message.isBlank()) html.append("<div class=\"flash\">").append(escape(message)).append("</div>");
        html.append("<form class=\"auth-form\" method=\"post\" action=\"").append(createAccount ? "/register" : "/login").append("\">");
        if (createAccount) html.append("<label>Your name<input name=\"name\" type=\"text\" autocomplete=\"name\" placeholder=\"e.g. Ananya Deshmukh\" maxlength=\"80\" required></label>");
        html.append("<label>Email address<input name=\"email\" type=\"email\" autocomplete=\"email\" placeholder=\"you@i2it.edu\" maxlength=\"254\" required></label>")
                .append("<label>Password<input name=\"password\" type=\"password\" autocomplete=\"")
                .append(createAccount ? "new-password" : "current-password")
                .append("\" placeholder=\"").append(createAccount ? "At least 8 characters" : "Enter your password")
                .append("\" minlength=\"").append(createAccount ? "8" : "1")
                .append("\" maxlength=\"128\" required></label><button class=\"primary\" type=\"submit\">")
                .append(createAccount ? "Create account" : "Sign in").append("</button></form><div class=\"switch\">")
                .append(createAccount ? "Already have an account? <a href=\"/login\">Sign in</a>"
                        : "New to the library? <a href=\"/register\">Create an account</a>")
                .append("</div></main></div></body></html>");
        return html.toString();
    }

    private static String render(Map<String, String> query, String accountName) {
        String search = value(query, "q").trim();
        String category = value(query, "category");
        List<Book> filtered = books.stream().filter(book ->
                (search.isBlank() || (book.title() + " " + book.author() + " " + book.isbn()).toLowerCase().contains(search.toLowerCase()))
                && (category.isBlank() || category.equals(book.category()))).toList();
        long availableTitles = books.stream().filter(book -> book.available() > 0).count();
        long activeLoans = loans.stream().filter(loan -> !loan.returned()).count();
        long overdue = loans.stream().filter(loan -> !loan.returned() && LocalDate.parse(loan.dueDate()).isBefore(LocalDate.now())).count();
        List<String> categories = books.stream().map(Book::category).distinct().sorted().toList();

        StringBuilder html = new StringBuilder(16000);
        html.append("""
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>I2IT Library · Library management</title>
                <style>
                :root{--ink:#17201d;--muted:#7a8580;--paper:#f6f7f3;--white:#fff;--green:#226b50;--green-dark:#174b39;--line:#e8ebe5;--lime:#d8f07d;--red:#b95143}
                *{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:14px/1.5 "Segoe UI",Arial,sans-serif}
                button,input,select{font:inherit}button,.button{cursor:pointer}.layout{min-height:100vh;display:grid;grid-template-columns:246px 1fr}
                aside{background:#173d32;color:#edf4ed;padding:27px 19px;display:flex;flex-direction:column;gap:32px}
                .brand{display:flex;align-items:center;gap:11px;padding:2px 6px}.brand-mark{width:39px;height:39px;border-radius:12px;background:var(--lime);color:#163c30;display:grid;place-items:center;font-size:21px;font-weight:800}
                .brand strong{font-size:15px;letter-spacing:.01em}.brand small{display:block;color:#a8c2b5;font-size:11px;margin-top:1px}
                .nav-label{font-size:10px;text-transform:uppercase;letter-spacing:.15em;color:#9bb6a9;padding:0 10px 9px}.nav-link{display:flex;align-items:center;gap:12px;color:#d9e7dd;text-decoration:none;padding:11px 12px;border-radius:9px;margin:2px 0;font-size:13px}
                .nav-link.active{background:#2c5948;color:white}.nav-icon{font-size:17px;width:19px;text-align:center}.side-bottom{margin-top:auto;border-top:1px solid #ffffff24;padding:20px 8px 0;color:#a8c2b5;font-size:11px}.side-bottom strong{display:block;color:#e5eee7;font-size:12px;margin-bottom:4px}
                main{min-width:0;padding:31px clamp(20px,4vw,55px) 48px;max-width:1600px;width:100%;margin:0 auto}.topline{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:28px}
                .crumb{color:var(--muted);font-size:12px}.crumb b{color:var(--ink);font-weight:600}.user{display:flex;align-items:center;gap:10px;font-size:12px}.avatar{width:35px;height:35px;border-radius:50%;background:#e8eedb;color:var(--green-dark);display:grid;place-items:center;font-weight:700}
                .welcome{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;margin-bottom:25px}.eyebrow{color:var(--green);font-size:11px;font-weight:700;letter-spacing:.12em;text-transform:uppercase}
                h1{font-size:30px;letter-spacing:-.04em;margin:4px 0 3px;line-height:1.2}.sub{color:var(--muted);margin:0;font-size:13px}.today{color:var(--muted);font-size:12px}
                .stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:15px;margin-bottom:24px}.stat{padding:18px 19px;background:var(--white);border:1px solid var(--line);border-radius:12px}
                .stat-top{display:flex;justify-content:space-between;align-items:center;color:var(--muted);font-size:12px}.stat-icon{width:30px;height:30px;border-radius:9px;background:#eff5e6;color:var(--green);display:grid;place-items:center;font-size:15px}
                .stat-number{font-size:27px;font-weight:700;letter-spacing:-.04em;margin:9px 0 2px}.stat-note{font-size:11px;color:var(--muted)}.stat-note.good{color:var(--green)}
                .grid{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(270px,.9fr);gap:17px;align-items:start}.panel{background:var(--white);border:1px solid var(--line);border-radius:13px;overflow:hidden}
                .panel-head{padding:18px 20px 14px;display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-head h2{margin:0;font-size:15px;letter-spacing:-.02em}.panel-head p{margin:3px 0 0;color:var(--muted);font-size:11px}
                .count{font-size:11px;color:var(--muted);background:#f2f4ef;padding:5px 9px;border-radius:20px}.toolbar{padding:0 20px 14px;display:flex;gap:8px}
                .search{position:relative;flex:1}.search input{padding-left:34px;width:100%}.search span{position:absolute;left:11px;top:7px;color:var(--muted);font-size:15px}
                input,select{border:1px solid var(--line);border-radius:7px;padding:8px 10px;color:var(--ink);background:#fff;outline:none;font-size:12px}input:focus,select:focus{border-color:#82aa8c;box-shadow:0 0 0 3px #226b5012}
                .filter{min-width:112px}.primary{border:0;background:var(--green);color:white;border-radius:7px;padding:8px 13px;font-size:12px;font-weight:600}.primary:hover{background:var(--green-dark)}
                .add-trigger{background:var(--lime);color:#1b3529}.add-trigger:hover{background:#c9e76b}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}th{font-size:10px;color:var(--muted);font-weight:600;text-transform:uppercase;letter-spacing:.07em;padding:10px 13px;background:#fafbf8;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
                td{padding:12px 13px;border-bottom:1px solid #eff0ec;font-size:12px}tr:last-child td{border-bottom:0}.book-name{font-weight:600;font-size:12px}.book-author{font-size:10px;color:var(--muted);margin-top:2px}.badge{display:inline-block;border-radius:20px;background:#f0f3eb;padding:4px 8px;font-size:10px;color:#58635a}
                .availability{font-size:11px}.availability b{color:var(--green)}.availability .none{color:var(--red)}.action{border:1px solid var(--line);background:#fff;color:var(--green-dark);border-radius:6px;padding:5px 9px;font-size:10px;font-weight:600}.action:hover{background:#f4f8ef}.row-action{position:relative}
                details.borrow{position:relative}details.borrow summary{list-style:none}details.borrow summary::-webkit-details-marker{display:none}details.borrow[open] .borrow-form{display:grid}
                .borrow-form{display:none;position:absolute;z-index:5;right:0;top:29px;width:210px;padding:12px;background:#fff;border:1px solid var(--line);border-radius:9px;box-shadow:0 8px 24px #17201d1c;gap:8px}.borrow-form label,.add-form label{font-size:11px;color:var(--muted);display:grid;gap:4px}
                .side-panel .panel-head{padding-bottom:10px}.loan-list{padding:0 18px}.loan{padding:12px 0;border-top:1px solid var(--line);display:grid;grid-template-columns:35px 1fr auto;align-items:center;gap:10px}
                .loan-art{width:34px;height:40px;border-radius:4px;background:#e6edda;color:var(--green-dark);display:grid;place-items:center;font-size:15px}.loan:nth-child(2) .loan-art{background:#f3e5d7;color:#9a6438}.loan:nth-child(3) .loan-art{background:#e3e6f4;color:#59639b}
                .loan-title{font-size:11px;font-weight:600;white-space:normal}.loan-meta{font-size:10px;color:var(--muted);margin-top:3px}.due{font-size:9px;color:#6b756d;margin-top:3px}.return-form button{background:none;border:0;color:var(--green);font-size:10px;padding:3px}
                .overdue-note{margin:7px 18px 16px;padding:10px 12px;background:#fff5ee;color:#8f5742;border-radius:8px;font-size:10px}.empty{padding:30px 16px;text-align:center;color:var(--muted);font-size:12px}
                .flash{padding:10px 14px;margin:0 0 17px;border-radius:8px;background:#edf6e7;color:#285d39;font-size:12px}.flash.error{background:#fff0eb;color:#9c4438}
                .modal{margin:0 20px 16px;padding:14px;background:#f7f9f4;border:1px solid var(--line);border-radius:10px}.modal summary{font-size:12px;font-weight:600;color:var(--green-dark);cursor:pointer}
                .add-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:13px}.add-form input{width:100%}.add-form .primary{grid-column:1/-1}
                .remove-form{display:inline}.remove-form button{border:0;background:none;color:#a65b4c;font-size:10px;cursor:pointer;padding:4px}
                footer{font-size:10px;color:#9aa39c;text-align:center;margin-top:23px}
                @media(max-width:1050px){.layout{grid-template-columns:205px 1fr}.grid{grid-template-columns:1fr}.side-panel{display:grid;grid-template-columns:1fr 1fr}.side-panel .panel-head{grid-column:1/-1}.overdue-note{align-self:start}}
                @media(max-width:760px){.layout{grid-template-columns:1fr}aside{padding:12px 15px;gap:12px;flex-direction:row;align-items:center;position:sticky;top:0;z-index:10}.brand{padding:0}.brand-mark{width:34px;height:34px}.nav-label,.nav-link:not(.active),.side-bottom{display:none}.nav-link.active{margin-left:auto;padding:8px 11px;font-size:11px}.nav-icon{display:none}main{padding:23px 15px 35px}.stats{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.stat{padding:14px}.stat-number{font-size:23px}.welcome{align-items:flex-start;flex-direction:column;gap:8px}.today{display:none}.topline{margin-bottom:20px}.side-panel{display:block}.toolbar{padding:0 12px 12px;flex-wrap:wrap}.search{min-width:100%}.filter{flex:1}.panel-head{padding-left:14px;padding-right:14px}td{padding:10px}th{padding:9px 10px}}
                </style></head><body><div class="layout"><aside>
                <div class="brand"><div class="brand-mark">i</div><div><strong>I2IT Library</strong><small>Knowledge, organized.</small></div></div>
                <nav><div class="nav-label">Workspace</div><a class="nav-link active" href="/"><span class="nav-icon">▦</span>Library dashboard</a></nav>
                <div class="side-bottom"><strong>International Institute of<br>Information Technology</strong>Hinjawadi, Pune · Est. 2003</div>
                </aside><main>
                <div class="topline"><div class="crumb">Workspace&nbsp; / &nbsp;<b>Library dashboard</b></div><div class="user"><span>""").append(escape(accountName)).append("""
                </span><span class="avatar">""").append(escape(accountName.substring(0, 1).toUpperCase())).append("""
                </span><form method="post" action="/logout"><button class="action" type="submit">Sign out</button></form></div></div>
                <section class="welcome"><div><div class="eyebrow">Campus library</div><h1>Good day, librarian.</h1><p class="sub">A little organization goes a long way. Here's what's happening today.</p></div><div class="today">▦ &nbsp;""").append(escape(LocalDate.now().toString())).append("""
                </div></section>
                """);
        String message = query.get("message");
        if (message != null && !message.isBlank()) {
            html.append("<div class=\"flash").append(message.contains("could not") || message.contains("no available")
                    || message.contains("Return all") || message.contains("valid") || message.contains("complete") || message.contains("already") ? " error" : "")
                    .append("\">").append(escape(message)).append("</div>");
        }
        html.append("<section class=\"stats\">");
        stat(html, "Catalog titles", Integer.toString(books.size()), "Across " + categories.size() + " collections", "▤");
        stat(html, "Copies available", Integer.toString(books.stream().mapToInt(Book::available).sum()), "Ready to find a new reader", "⌕");
        stat(html, "Currently borrowed", Long.toString(activeLoans), "Active student checkouts", "↗");
        stat(html, "Overdue returns", Long.toString(overdue), overdue == 0 ? "Everyone is on track" : "Need a friendly reminder", "◷");
        html.append("</section><section class=\"grid\"><div class=\"panel\"><div class=\"panel-head\"><div><h2>Book catalog</h2><p>Browse, manage, and lend your collection</p></div><span class=\"count\">")
                .append(filtered.size()).append(" titles</span></div>");
        html.append("<details class=\"modal\"><summary>＋ &nbsp; Add a book to the catalog</summary><form class=\"add-form\" method=\"post\" action=\"/books/add\">")
                .append(field("title", "Book title", "e.g. The Design of Everyday Things", "text"))
                .append(field("author", "Author", "Author name", "text"))
                .append(field("isbn", "ISBN", "ISBN-13", "text"))
                .append(field("category", "Category", "e.g. Technology", "text"))
                .append(field("copies", "Number of copies", "1", "number"))
                .append("<button class=\"primary\" type=\"submit\">Add book</button></form></details>");
        html.append("<form class=\"toolbar\" method=\"get\" action=\"/\"><div class=\"search\"><span>⌕</span><input name=\"q\" placeholder=\"Search title, author, or ISBN...\" value=\"")
                .append(escape(search)).append("\"></div><select class=\"filter\" name=\"category\"><option value=\"\">All categories</option>");
        for (String item : categories) html.append("<option").append(item.equals(category) ? " selected" : "").append(">")
                .append(escape(item)).append("</option>");
        html.append("</select><button class=\"primary\" type=\"submit\">Search</button></form><div class=\"table-wrap\"><table><thead><tr><th>Book</th><th>Category</th><th>Available</th><th>Action</th></tr></thead><tbody>");
        if (filtered.isEmpty()) html.append("<tr><td class=\"empty\" colspan=\"4\">No books match your search. Try another title or category.</td></tr>");
        for (Book book : filtered) {
            html.append("<tr><td><div class=\"book-name\">").append(escape(book.title())).append("</div><div class=\"book-author\">")
                    .append(escape(book.author())).append(" · ").append(escape(book.isbn())).append("</div></td><td><span class=\"badge\">")
                    .append(escape(book.category())).append("</span></td><td><span class=\"availability\"><b")
                    .append(book.available() == 0 ? " class=\"none\"" : "").append(">").append(book.available()).append("</b> / ")
                    .append(book.copies()).append("</span></td><td class=\"row-action\">");
            if (book.available() > 0) {
                html.append("<details class=\"borrow\"><summary class=\"action\">Check out</summary><form class=\"borrow-form\" method=\"post\" action=\"/books/borrow\"><label>Student name or ID<input name=\"student\" placeholder=\"e.g. CSE-2042\" required></label><input type=\"hidden\" name=\"bookId\" value=\"")
                        .append(book.id()).append("\"><button class=\"primary\" type=\"submit\">Confirm checkout</button></form></details>");
            } else html.append("<span class=\"badge\">All borrowed</span>");
            if (book.available() == book.copies()) {
                html.append("<form class=\"remove-form\" method=\"post\" action=\"/books/delete\" onsubmit=\"return confirm('Remove this book from the catalog?')\"><input type=\"hidden\" name=\"bookId\" value=\"")
                        .append(book.id()).append("\"><button type=\"submit\" aria-label=\"Remove book\">Remove</button></form>");
            }
            html.append("</td></tr>");
        }
        html.append("</tbody></table></div></div><div class=\"panel side-panel\"><div class=\"panel-head\"><div><h2>Recent checkouts</h2><p>Keep an eye on books out in the wild</p></div><span class=\"count\">")
                .append(activeLoans).append(" active</span></div><div class=\"loan-list\">");
        List<Loan> active = loans.stream().filter(loan -> !loan.returned()).sorted((a, b) -> b.id() - a.id()).limit(5).toList();
        if (active.isEmpty()) html.append("<div class=\"empty\">No active checkouts. It's a good day to borrow a book.</div>");
        for (Loan loan : active) {
            html.append("<div class=\"loan\"><div class=\"loan-art\">▤</div><div><div class=\"loan-title\">")
                    .append(escape(loan.title())).append("</div><div class=\"loan-meta\">").append(escape(loan.student()))
                    .append("</div><div class=\"due\">Due ").append(escape(loan.dueDate())).append("</div></div><form class=\"return-form\" method=\"post\" action=\"/loans/return\"><input type=\"hidden\" name=\"loanId\" value=\"")
                    .append(loan.id()).append("\"><button type=\"submit\">Return</button></form></div>");
        }
        html.append("</div>");
        if (overdue > 0) html.append("<div class=\"overdue-note\">◷ &nbsp; ").append(overdue).append(" book").append(overdue == 1 ? " is" : "s are")
                .append(" past the return date.</div>");
        html.append("</div></section><footer>I2IT Library · A better way to keep knowledge moving.</footer></main></div></body></html>");
        return html.toString();
    }

    private static void stat(StringBuilder html, String label, String number, String note, String icon) {
        html.append("<article class=\"stat\"><div class=\"stat-top\">").append(label).append("<span class=\"stat-icon\">")
                .append(icon).append("</span></div><div class=\"stat-number\">").append(number)
                .append("</div><div class=\"stat-note\">").append(note).append("</div></article>");
    }

    private static String field(String name, String label, String placeholder, String type) {
        return "<label>" + label + "<input name=\"" + name + "\" type=\"" + type + "\" placeholder=\"" + placeholder
                + "\" " + ("copies".equals(name) ? "min=\"1\" max=\"999\"" : "required") + " required></label>";
    }

    private static Book findBook(String id) {
        int bookId = parseInt(id, -1);
        return books.stream().filter(book -> book.id() == bookId).findFirst().orElse(null);
    }

    private static void saveBooks() throws IOException {
        List<String> rows = books.stream().map(book -> csv(book.id(), book.title(), book.author(), book.isbn(),
                book.category(), book.copies(), book.available())).toList();
        Files.write(BOOKS_FILE, rows, StandardCharsets.UTF_8);
    }

    private static void saveLoans() throws IOException {
        List<String> rows = loans.stream().map(loan -> csv(loan.id(), loan.bookId(), loan.title(), loan.student(),
                loan.dueDate(), loan.returned())).toList();
        Files.write(LOANS_FILE, rows, StandardCharsets.UTF_8);
    }

    private static String csv(Object... values) {
        List<String> cells = new ArrayList<>();
        for (Object value : values) {
            String text = value.toString();
            cells.add("\"" + text.replace("\"", "\"\"") + "\"");
        }
        return String.join(",", cells);
    }

    private static List<String> parseCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else cell.append(current);
        }
        cells.add(cell.toString());
        return cells;
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : "");
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
