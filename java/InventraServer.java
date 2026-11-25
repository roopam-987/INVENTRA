
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class InventraServer {

    private static final int PORT = Integer.parseInt(
        System.getenv().getOrDefault("PORT", "8080")
    );

    private static final Path ROOT =
        Paths.get(".").toAbsolutePath().normalize();

    private static final Path WEB =
        ROOT.resolve("web").normalize();

    private static String SUPABASE_URL;
    private static String SUPABASE_KEY;

    private static final String FIREBASE_PROJECT =
        System.getenv().getOrDefault(
            "FIREBASE_PROJECT_ID",
            "inventra-2e43e"
        );

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static HttpServer server;

    // =====================================================
    // OOP PROJECT CLASSES
    // =====================================================

    static abstract class Asset {
        private final String id;
        private final String name;
        private String status;

        Asset(String id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getStatus() { return status; }

        public void setStatus(String status) {
            this.status = status;
        }

        public abstract String getType();
    }

    static class ComputerAsset extends Asset {
        private final String lab;

        ComputerAsset(String id, String name, String status, String lab) {
            super(id, name, status);
            this.lab = lab;
        }

        public String getLab() { return lab; }

        @Override
        public String getType() {
            return "Computer";
        }
    }

    interface Reportable {
        String createDescription();
    }

    static class MaintenanceTicket implements Reportable {
        String id;
        String lab;
        String pc;
        String fault;
        String priority;
        String status;

        MaintenanceTicket(
            String id,
            String lab,
            String pc,
            String fault,
            String priority
        ) {
            this.id = id;
            this.lab = lab;
            this.pc = pc;
            this.fault = fault;
            this.priority = priority;
            this.status = "Open";
        }

        @Override
        public String createDescription() {
            return fault + " problem on " + pc + " in " + lab;
        }
    }

    static class LabFileException extends Exception {
        LabFileException(String message) {
            super(message);
        }
    }

    // =====================================================
    // USER MODEL
    // =====================================================

    private static class User {
        String uid;
        String email;
        String name;
        String role;

        User(String uid, String email, String name, String role) {
            this.uid = uid;
            this.email = email;
            this.name = name;
            this.role = role;
        }
    }

    // =====================================================
    // CONFIGURATION
    // =====================================================

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing environment variable: " + name
            );
        }

        return value.trim();
    }

    // =====================================================
    // START SERVER
    // =====================================================

    public static void main(String[] args) throws Exception {

        SUPABASE_URL = requiredEnv("SUPABASE_URL")
            .replaceAll("/+$", "");

        SUPABASE_KEY = requiredEnv("SUPABASE_SECRET_KEY");

        if (!Files.isDirectory(WEB)) {
            throw new IllegalStateException(
                "The web folder was not found: " + WEB
            );
        }

        GoogleCredentials credentials;

        try {
            credentials = GoogleCredentials.getApplicationDefault();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Firebase credentials are missing. Configure "
                + "GOOGLE_APPLICATION_CREDENTIALS or provide "
                + "Firebase credentials through Render.",
                e
            );
        }

        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(FIREBASE_PROJECT)
            .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", PORT),
            0
        );

        server.setExecutor(Executors.newFixedThreadPool(12));

        server.createContext("/api/me", InventraServer::handleMe);
        server.createContext("/api/labs", InventraServer::handleLabs);
        server.createContext("/api/report", InventraServer::handleReport);
        server.createContext("/api/tickets", InventraServer::handleTickets);
        server.createContext("/api/assignments", InventraServer::handleAssignments);
        server.createContext("/api/workers", InventraServer::handleWorkers);
        server.createContext("/api/launch", InventraServer::handleLaunch);
        server.createContext("/", InventraServer::handlePages);

        server.start();

        System.out.println("====================================");
        System.out.println("       INVENTRA SERVER ONLINE");
        System.out.println("====================================");
        System.out.println("Port: " + PORT);
        System.out.println("Firebase: " + FIREBASE_PROJECT);
        System.out.println("Supabase: " + SUPABASE_URL);
    }

    // =====================================================
    // HTTP HELPERS
    // =====================================================

    private static void send(
        HttpExchange ex,
        int code,
        JsonNode data
    ) throws IOException {

        byte[] bytes = JSON.writeValueAsBytes(data);

        ex.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set(
            "X-Content-Type-Options",
            "nosniff"
        );

        ex.sendResponseHeaders(code, bytes.length);

        try {
            ex.getResponseBody().write(bytes);
        } finally {
            ex.close();
        }
    }

    private static void sendError(
        HttpExchange ex,
        int code,
        String message
    ) throws IOException {

        ObjectNode result = JSON.createObjectNode();
        result.put("error", message);

        send(ex, code, result);
    }

    private static boolean preflight(HttpExchange ex)
            throws IOException {

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {

            ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
            );

            ex.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
            );

            ex.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PATCH, OPTIONS"
            );

            ex.sendResponseHeaders(204, -1);
            ex.close();

            return true;
        }

        return false;
    }

    private static JsonNode body(HttpExchange ex)
            throws IOException {

        String raw = new String(
            ex.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        if (raw.isBlank()) {
            return JSON.createObjectNode();
        }

        String contentType =
            ex.getRequestHeaders().getFirst("Content-Type");

        if (contentType != null &&
            contentType.contains("application/x-www-form-urlencoded")) {

            ObjectNode result = JSON.createObjectNode();

            for (String pair : raw.split("&")) {

                String[] parts = pair.split("=", 2);

                String key = URLDecoder.decode(
                    parts[0],
                    StandardCharsets.UTF_8
                );

                String val = parts.length > 1
                    ? URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                    )
                    : "";

                result.put(key, val);
            }

            return result;
        }

        JsonNode parsed = JSON.readTree(raw);

        return parsed == null
            ? JSON.createObjectNode()
            : parsed;
    }

    private static String value(JsonNode node, String key) {
        if (node == null || node.isNull()) return "";

        JsonNode field = node.get(key);

        if (field == null || field.isNull()) return "";

        return field.asText();
    }

    private static String enc(String input) {
        return URLEncoder.encode(
            input,
            StandardCharsets.UTF_8
        );
    }

    // =====================================================
    // FIREBASE AUTHENTICATION
    // =====================================================

    private static User authenticate(HttpExchange ex)
            throws Exception {

        String header =
            ex.getRequestHeaders().getFirst("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new SecurityException("Please sign in first.");
        }

        String token = header.substring(7).trim();

        if (token.isBlank()) {
            throw new SecurityException("Invalid login token.");
        }

        FirebaseToken decoded;

        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(token);
        } catch (Exception e) {
            throw new SecurityException(
                "Your login session is invalid. Please sign in again."
            );
        }

        String uid = decoded.getUid();
        String email = decoded.getEmail();
        String name = decoded.getName();

        ObjectNode profile = JSON.createObjectNode();

        profile.put("firebase_uid", uid);
        profile.put("email", email == null ? "" : email);
        profile.put("display_name", name == null ? "" : name);

        // Preserve the existing role when updating a profile.
        supabase(
            "POST",
            "/profiles?on_conflict=firebase_uid",
            profile,
            "resolution=merge-duplicates,return=minimal"
        );

        JsonNode profiles = supabase(
            "GET",
            "/profiles?firebase_uid=eq." + enc(uid)
                + "&select=firebase_uid,email,display_name,role",
            null,
            null
        );

        if (!profiles.isArray() || profiles.isEmpty()) {
            throw new IOException(
                "User profile was not returned by Supabase."
            );
        }

        JsonNode row = profiles.get(0);

        String role = value(row, "role").toLowerCase();

        if (!List.of("student", "admin", "worker").contains(role)) {
            throw new SecurityException(
                "Your account does not have a valid role."
            );
        }

        String profileEmail = value(row, "email");
        String profileName = value(row, "display_name");

        return new User(
            uid,
            profileEmail.isBlank() ? email : profileEmail,
            profileName.isBlank() ? name : profileName,
            role
        );
    }

    private static User requireRole(
        HttpExchange ex,
        String... allowedRoles
    ) throws Exception {

        User user = authenticate(ex);

        if (!Arrays.asList(allowedRoles).contains(user.role)) {
            throw new SecurityException(
                "You are not allowed to perform this action."
            );
        }

        return user;
    }

    private static void handleMe(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendError(ex, 405, "GET required.");
                return;
            }

            User user = authenticate(ex);

            ObjectNode result = JSON.createObjectNode();

            result.put("uid", user.uid);
            result.put("email", user.email == null ? "" : user.email);
            result.put("name", user.name == null ? "" : user.name);
            result.put("role", user.role);

            send(ex, 200, result);

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Could not load user profile.");
        }
    }

    // =====================================================
    // SUPABASE REST CLIENT
    // =====================================================

    private static JsonNode supabase(
        String method,
        String path,
        JsonNode data,
        String prefer
    ) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(SUPABASE_URL + "/rest/v1" + path))
            .timeout(Duration.ofSeconds(25))
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer " + SUPABASE_KEY)
            .header("Accept", "application/json");

        if (prefer != null && !prefer.isBlank()) {
            builder.header("Prefer", prefer);
        }

        if (data != null) {

            builder.header(
                "Content-Type",
                "application/json"
            );

            builder.method(
                method,
                HttpRequest.BodyPublishers.ofString(
                    JSON.writeValueAsString(data)
                )
            );

        } else {

            builder.method(
                method,
                HttpRequest.BodyPublishers.noBody()
            );
        }

        HttpResponse<String> response = HTTP.send(
            builder.build(),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 ||
            response.statusCode() >= 300) {

            throw new IOException(
                "Supabase error " + response.statusCode()
                + ": " + response.body()
            );
        }

        String responseBody = response.body();

        if (responseBody == null || responseBody.isBlank()) {
            return JSON.createArrayNode();
        }

        return JSON.readTree(responseBody);
    }

    private static JsonNode getAssets() throws Exception {
        return supabase(
            "GET",
            "/assets?select=id,asset_code,lab_id,asset_type,status,created_at"
                + "&order=lab_id.asc,asset_code.asc",
            null,
            null
        );
    }

    // =====================================================
    // ASSET HELPERS
    // =====================================================

    private static String pcNumber(String assetCode) {

        if (assetCode == null || assetCode.isBlank()) {
            return "";
        }

        String[] parts = assetCode.split("-");

        try {
            int number = Integer.parseInt(
                parts[parts.length - 1]
            );

            return "PC" + String.format("%02d", number);

        } catch (Exception ignored) {
            return assetCode;
        }
    }

    private static String labNumber(String lab) {
        return lab == null
            ? ""
            : lab.replaceAll("[^0-9]", "");
    }

    private static JsonNode findAsset(
        String lab,
        String pc
    ) throws Exception {

        JsonNode assets = getAssets();

        String requestedLab = labNumber(lab);
        String requestedPc = pc.replaceAll("[^0-9]", "");

        for (JsonNode asset : assets) {

            String code = value(asset, "asset_code");
            String assetLab = value(asset, "lab_id");

            if (!labNumber(assetLab).equals(requestedLab)) {
                continue;
            }

            String existingPc =
                pcNumber(code).replaceAll("[^0-9]", "");

            if (existingPc.equals(requestedPc)) {
                return asset;
            }
        }

        return null;
    }

    // =====================================================
    // LAB INVENTORY API
    // =====================================================

    private static void handleLabs(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            authenticate(ex);

            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendError(ex, 405, "GET required.");
                return;
            }

            JsonNode assets = getAssets();

            Map<String, List<JsonNode>> grouped = new TreeMap<>();

            for (JsonNode asset : assets) {

                String lab = value(asset, "lab_id");

                grouped.computeIfAbsent(
                    lab,
                    key -> new ArrayList<>()
                ).add(asset);
            }

            ArrayNode labs = JSON.createArrayNode();

            for (Map.Entry<String, List<JsonNode>> entry : grouped.entrySet()) {

                ObjectNode lab = JSON.createObjectNode();
                ArrayNode pcs = JSON.createArrayNode();

                int working = 0;
                int faulty = 0;
                int inspection = 0;

                String labName = entry.getKey();

                lab.put("id", labName);
                lab.put(
                    "lab",
                    labName.toLowerCase().startsWith("lab")
                        ? labName
                        : "Lab " + labName
                );
                lab.put("lab_id", labName);

                for (JsonNode asset : entry.getValue()) {

                    String status = value(asset, "status");
                    String assetCode = value(asset, "asset_code");

                    if (status.equalsIgnoreCase("Faulty")) {
                        faulty++;
                    } else if (status.equalsIgnoreCase("Inspection")) {
                        inspection++;
                    } else {
                        working++;
                    }

                    ObjectNode pc = JSON.createObjectNode();

                    pc.put("pc", pcNumber(assetCode));
                    pc.put("assetId", assetCode);
                    pc.put("asset_code", assetCode);
                    pc.put("id", value(asset, "id"));
                    pc.put("status", status);

                    pcs.add(pc);
                }

                lab.put("working", working);
                lab.put("faulty", faulty);
                lab.put("inspection", inspection);
                lab.set("pcs", pcs);

                labs.add(lab);
            }

            ObjectNode result = JSON.createObjectNode();
            result.set("labs", labs);

            send(ex, 200, result);

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Could not load computer inventory.");
        }
    }

    // =====================================================
    // STUDENT REPORT API
    // =====================================================

    private static void handleReport(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "POST required.");
            return;
        }

        try {
            User user = requireRole(ex, "student");

            JsonNode input = body(ex);

            String lab = value(input, "lab").trim();
            String pc = value(input, "pc").trim().toUpperCase();
            String fault = value(input, "fault").trim();
            String description = value(input, "description").trim();

            if (lab.isBlank() || labNumber(lab).isBlank()) {
                sendError(ex, 400, "Please select a valid laboratory.");
                return;
            }

            if (!pc.matches("PC\\s*-?\\s*\\d{1,2}")) {
                sendError(ex, 400, "Please select a valid computer.");
                return;
            }

            List<String> allowedFaults = List.of(
                "Mouse",
                "Keyboard",
                "Display",
                "Network",
                "CPU / Power",
                "Software",
                "Other"
            );

            if (!allowedFaults.contains(fault)) {
                sendError(ex, 400, "Please select a valid fault type.");
                return;
            }

            if (description.length() > 1000) {
                sendError(
                    ex,
                    400,
                    "Description cannot exceed 1000 characters."
                );
                return;
            }

            JsonNode asset = findAsset(lab, pc);

            if (asset == null) {
                sendError(
                    ex,
                    404,
                    "Computer not found in the selected laboratory."
                );
                return;
            }

            if (!"Working".equalsIgnoreCase(value(asset, "status"))) {
                sendError(
                    ex,
                    409,
                    "This computer is already marked as faulty or unavailable."
                );
                return;
            }

            String ticketCode =
                "INV-" + System.currentTimeMillis()
                + "-" + (int)(Math.random() * 900 + 100);

            ObjectNode ticket = JSON.createObjectNode();

            ticket.put("ticket_code", ticketCode);
            ticket.put("asset_id", value(asset, "id"));
            ticket.put("reported_by", user.uid);
            ticket.put("fault_type", fault);
            ticket.put("description", description);
            ticket.put("priority", "Normal");
            ticket.put("status", "Open");

            JsonNode inserted = supabase(
                "POST",
                "/tickets",
                ticket,
                "return=representation"
            );

            if (!inserted.isArray() || inserted.isEmpty()) {
                throw new IOException(
                    "Supabase did not confirm ticket creation."
                );
            }

            ObjectNode assetPatch = JSON.createObjectNode();
            assetPatch.put("status", "Faulty");

            supabase(
                "PATCH",
                "/assets?id=eq." + enc(value(asset, "id")),
                assetPatch,
                "return=minimal"
            );

            auditBestEffort(
                user.uid,
                "Ticket reported",
                "ticket",
                ticketCode
            );

            ObjectNode result = JSON.createObjectNode();

            result.put("success", true);
            result.put("ticketId", ticketCode);
            result.put("ticket_code", ticketCode);
            result.put("message", "Report saved successfully.");

            send(ex, 201, result);

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(
                ex,
                500,
                "Report could not be saved. Please try again."
            );
        }
    }

    // =====================================================
    // TICKET DATA
    // =====================================================

    private static JsonNode ticketRows(User user)
            throws Exception {

        String path =
            "/tickets?select=*,assets(asset_code,lab_id)"
            + "&order=created_at.desc";

        if ("worker".equals(user.role)) {

            JsonNode assignments = supabase(
                "GET",
                "/assignments?worker_id=eq." + enc(user.uid)
                    + "&select=ticket_id",
                null,
                null
            );

            if (!assignments.isArray() || assignments.isEmpty()) {
                return JSON.createArrayNode();
            }

            List<String> ids = new ArrayList<>();

            for (JsonNode assignment : assignments) {

                String id = value(assignment, "ticket_id");

                if (!id.isBlank()) {
                    ids.add(id);
                }
            }

            if (ids.isEmpty()) {
                return JSON.createArrayNode();
            }

            path =
                "/tickets?select=*,assets(asset_code,lab_id)"
                + "&id=in.(" + String.join(",", ids) + ")"
                + "&order=created_at.desc";
        }

        return supabase("GET", path, null, null);
    }

    private static ObjectNode ticketForUi(JsonNode row) {

        ObjectNode ticket = JSON.createObjectNode();

        String code = value(row, "ticket_code");

        if (code.isBlank()) {
            code = value(row, "id");
        }

        JsonNode asset = row.path("assets");

        ticket.put("id", code);
        ticket.put("ticketCode", code);
        ticket.put("ticket_code", code);
        ticket.put("databaseId", value(row, "id"));

        ticket.put("assetCode", value(asset, "asset_code"));
        ticket.put("asset_code", value(asset, "asset_code"));

        ticket.put("lab", value(asset, "lab_id"));
        ticket.put("pc", pcNumber(value(asset, "asset_code")));

        ticket.put("fault", value(row, "fault_type"));
        ticket.put("fault_type", value(row, "fault_type"));

        ticket.put("description", value(row, "description"));
        ticket.put("priority", value(row, "priority"));
        ticket.put("status", value(row, "status"));

        ticket.put("reportedBy", value(row, "reported_by"));
        ticket.put("createdAt", value(row, "created_at"));

        return ticket;
    }

    // =====================================================
    // TICKETS API
    // =====================================================

    private static void handleTickets(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            User user = requireRole(ex, "admin", "worker");

            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {

                JsonNode rows = ticketRows(user);
                ArrayNode tickets = JSON.createArrayNode();

                for (JsonNode row : rows) {
                    tickets.add(ticketForUi(row));
                }

                ObjectNode result = JSON.createObjectNode();
                result.set("tickets", tickets);

                send(ex, 200, result);
                return;
            }

            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {

                JsonNode input = body(ex);

                String requestedId = value(input, "id");
                String status = value(input, "status");

                if (requestedId.isBlank()) {
                    sendError(ex, 400, "Ticket ID is required.");
                    return;
                }

                if (!List.of(
                    "Open",
                    "In Progress",
                    "Resolved"
                ).contains(status)) {

                    sendError(ex, 400, "Invalid ticket status.");
                    return;
                }

                JsonNode rows = ticketRows(user);
                JsonNode found = null;

                for (JsonNode row : rows) {

                    if (value(row, "id").equals(requestedId)
                        || value(row, "ticket_code").equals(requestedId)) {

                        found = row;
                        break;
                    }
                }

                if (found == null) {
                    sendError(
                        ex,
                        404,
                        "Ticket not found or not assigned to this worker."
                    );
                    return;
                }

                String databaseId = value(found, "id");
                String previousStatus = value(found, "status");
                String assetId = value(found, "asset_id");

                ObjectNode patch = JSON.createObjectNode();

                patch.put("status", status);
                patch.put("updated_at", Instant.now().toString());

                supabase(
                    "PATCH",
                    "/tickets?id=eq." + enc(databaseId),
                    patch,
                    "return=minimal"
                );

                if (!assetId.isBlank()) {

                    ObjectNode assetPatch = JSON.createObjectNode();

                    assetPatch.put(
                        "status",
                        "Resolved".equals(status)
                            ? "Working"
                            : "Faulty"
                    );

                    supabase(
                        "PATCH",
                        "/assets?id=eq." + enc(assetId),
                        assetPatch,
                        "return=minimal"
                    );
                }

                ObjectNode repair = JSON.createObjectNode();

                repair.put("ticket_id", databaseId);
                repair.put("worker_id", user.uid);
                repair.put(
                    "repair_notes",
                    "Status updated through INVENTRA."
                );
                repair.put("previous_status", previousStatus);
                repair.put("new_status", status);

                supabase(
                    "POST",
                    "/repairs",
                    repair,
                    "return=minimal"
                );

                auditBestEffort(
                    user.uid,
                    "Ticket status changed to " + status,
                    "ticket",
                    databaseId
                );

                ObjectNode result = JSON.createObjectNode();
                result.put("success", true);

                send(ex, 200, result);
                return;
            }

            sendError(ex, 405, "GET or POST required.");

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Ticket operation failed.");
        }
    }

    // =====================================================
    // WORKER LIST API
    // =====================================================

    private static void handleWorkers(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            requireRole(ex, "admin");

            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendError(ex, 405, "GET required.");
                return;
            }

            JsonNode workers = supabase(
                "GET",
                "/profiles?role=eq.worker"
                    + "&select=firebase_uid,email,display_name"
                    + "&order=display_name.asc",
                null,
                null
            );

            ObjectNode result = JSON.createObjectNode();
            result.set("workers", workers);

            send(ex, 200, result);

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Could not load workers.");
        }
    }

    // =====================================================
    // ADMIN ASSIGNMENT API
    // =====================================================

    private static void handleAssignments(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            User admin = requireRole(ex, "admin");

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendError(ex, 405, "POST required.");
                return;
            }

            JsonNode input = body(ex);

            String ticketId = value(input, "ticket_id");
            String workerId = value(input, "worker_id");

            if (ticketId.isBlank() || workerId.isBlank()) {
                sendError(
                    ex,
                    400,
                    "Ticket ID and worker are required."
                );
                return;
            }

            JsonNode workerRows = supabase(
                "GET",
                "/profiles?firebase_uid=eq." + enc(workerId)
                    + "&role=eq.worker&select=firebase_uid",
                null,
                null
            );

            if (!workerRows.isArray() || workerRows.isEmpty()) {
                sendError(
                    ex,
                    400,
                    "Selected account is not a registered worker."
                );
                return;
            }

            // Accept either the database UUID or displayed ticket code.
            JsonNode ticketRows = supabase(
                "GET",
                "/tickets?id=eq." + enc(ticketId) + "&select=id",
                null,
                null
            );

            if (!ticketRows.isArray() || ticketRows.isEmpty()) {

                ticketRows = supabase(
                    "GET",
                    "/tickets?ticket_code=eq." + enc(ticketId)
                        + "&select=id",
                    null,
                    null
                );
            }

            if (!ticketRows.isArray() || ticketRows.isEmpty()) {
                sendError(ex, 404, "Ticket does not exist.");
                return;
            }

            String databaseTicketId =
                value(ticketRows.get(0), "id");

            JsonNode existing = supabase(
                "GET",
                "/assignments?ticket_id=eq."
                    + enc(databaseTicketId)
                    + "&worker_id=eq." + enc(workerId)
                    + "&select=id",
                null,
                null
            );

            if (existing.isArray() && !existing.isEmpty()) {

                ObjectNode result = JSON.createObjectNode();

                result.put("success", true);
                result.put("message", "Worker is already assigned.");

                send(ex, 200, result);
                return;
            }

            ObjectNode assignment = JSON.createObjectNode();

            assignment.put("ticket_id", databaseTicketId);
            assignment.put("worker_id", workerId);
            assignment.put("assigned_by", admin.uid);

            supabase(
                "POST",
                "/assignments",
                assignment,
                "return=representation"
            );

            auditBestEffort(
                admin.uid,
                "Ticket assigned to worker",
                "ticket",
                databaseTicketId
            );

            ObjectNode result = JSON.createObjectNode();
            result.put("success", true);

            send(ex, 201, result);

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Assignment failed.");
        }
    }

    // =====================================================
    // AUDIT LOGGING
    // =====================================================

    private static void audit(
        String actor,
        String action,
        String entityType,
        String entityId
    ) throws Exception {

        ObjectNode log = JSON.createObjectNode();

        log.put("actor_id", actor);
        log.put("action", action);
        log.put("entity_type", entityType);
        log.put("entity_id", entityId);

        supabase(
            "POST",
            "/audit_logs",
            log,
            "return=minimal"
        );
    }

    private static void auditBestEffort(
        String actor,
        String action,
        String entityType,
        String entityId
    ) {

        try {
            audit(actor, action, entityType, entityId);

        } catch (Exception e) {
            System.err.println(
                "Audit log warning: " + e.getMessage()
            );
        }
    }

    // =====================================================
    // NATIVE CG / DS MODULES
    // =====================================================

    private static void handleLaunch(HttpExchange ex)
            throws IOException {

        if (preflight(ex)) return;

        try {
            requireRole(ex, "admin");

            sendError(
                ex,
                501,
                "Native CG/DS programs must be run locally. "
                + "They cannot execute on the hosted Render server."
            );

        } catch (SecurityException e) {
            sendError(ex, 401, e.getMessage());

        } catch (Exception e) {
            sendError(ex, 500, "Module request failed.");
        }
    }

    // =====================================================
    // STATIC WEBSITE SERVER
    // =====================================================

    private static void handlePages(HttpExchange ex)
            throws IOException {

        String method = ex.getRequestMethod();

        if (!"GET".equalsIgnoreCase(method)
            && !"HEAD".equalsIgnoreCase(method)) {

            sendError(ex, 405, "GET required.");
            return;
        }

        String path = ex.getRequestURI().getPath();

        if (path == null || path.isBlank()) {
            path = "/";
        }

        String file;

        if (path.equals("/") || path.equals("/login")) {
            file = "login.html";

        } else if (path.equals("/user") || path.equals("/user/")) {
            file = "user.html";

        } else if (path.equals("/admin") || path.equals("/admin/")) {
            file = "admin.html";

        } else if (path.equals("/worker") || path.equals("/worker/")) {
            file = "worker.html";

        } else {
            file = path.startsWith("/")
                ? path.substring(1)
                : path;
        }

        Path requested = WEB.resolve(file).normalize();

        if (file.contains("\\")
            || file.contains("\0")
            || !requested.startsWith(WEB)
            || !Files.exists(requested)
            || Files.isDirectory(requested)) {

            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        String lower = file.toLowerCase();
        String type = "application/octet-stream";

        if (lower.endsWith(".html")) {
            type = "text/html; charset=UTF-8";

        } else if (lower.endsWith(".css")) {
            type = "text/css; charset=UTF-8";

        } else if (lower.endsWith(".js")) {
            type = "application/javascript; charset=UTF-8";

        } else if (lower.endsWith(".json")) {
            type = "application/json; charset=UTF-8";

        } else if (lower.endsWith(".png")) {
            type = "image/png";

        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = "image/jpeg";

        } else if (lower.endsWith(".svg")) {
            type = "image/svg+xml";

        } else if (lower.endsWith(".ico")) {
            type = "image/x-icon";
        }

        long size = Files.size(requested);

        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set(
            "X-Content-Type-Options",
            "nosniff"
        );
        ex.getResponseHeaders().set("Cache-Control", "no-cache");

        if ("HEAD".equalsIgnoreCase(method)) {
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }

        ex.sendResponseHeaders(200, size);

        try {
            Files.copy(requested, ex.getResponseBody());
        } finally {
            ex.close();
        }
    }
}
