package io.lpin.android.sdk.licensing.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class LiasLicenseCli {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || isHelp(args[0])) {
            printUsage();
            return;
        }

        String command = args[0];
        Map<String, String> options = parseOptions(args);
        switch (command) {
            case "extract-public-key" -> extractPublicKey(options);
            case "sign" -> sign(options);
            case "sign-key" -> signKey(options);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static boolean isHelp(String command) {
        return "help".equals(command) || "--help".equals(command) || "-h".equals(command);
    }

    private static void printUsage() {
        System.out.println("LIAS offline license CLI\n" +
                "\n" +
                "Commands:\n" +
                "  extract-public-key --public-key /path/to/id_ed25519.pub\n" +
                "  sign --private-key /path/to/id_ed25519 --package-name com.example.app --signing-cert-sha256 ABCD --features face,scanner,space --not-before 2026-01-01T00:00:00Z --not-after 2027-01-01T00:00:00Z [--customer Acme] [--license-id acme-prod] [--key-id main] [--output /tmp/license.json]\n" +
                "  sign-key --private-key /path/to/id_ed25519 --app-pkg-id com.example.app --issued-at 2026-01-01T00:00:00Z --expire-at 2027-01-01T00:00:00Z [--signing-cert-sha256 AABB] [--features face,scanner] [--output /tmp/license.key]\n" +
                "\n" +
                "Notes:\n" +
                "- Generate the signing key with: ssh-keygen -t ed25519 -f lias_license_ed25519\n" +
                "- Private keys must be unencrypted for this CLI.\n" +
                "- The SDK expects the resulting license file to be shipped as assets/lpin-lias-license.json by default.");
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Expected an option starting with --, but got: " + token);
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for option: " + token);
            }
            options.put(token.substring(2), args[++index]);
        }
        return options;
    }

    private static void extractPublicKey(Map<String, String> options) throws Exception {
        Path publicKeyPath = requirePath(options, "public-key");
        Ed25519PublicKeyParameters publicKey = readPublicKey(publicKeyPath);
        String base64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getEncoded());
        System.out.println(base64Url);
    }

    private static void sign(Map<String, String> options) throws Exception {
        Path privateKeyPath = requirePath(options, "private-key");
        String packageName = requireOption(options, "package-name");
        String signingCertSha256 = normalizeDigest(requireOption(options, "signing-cert-sha256"));
        String notBefore = requireUtcTimestamp(options, "not-before");
        String notAfter = requireUtcTimestamp(options, "not-after");
        String keyId = options.getOrDefault("key-id", "main");
        String customer = options.get("customer");
        String licenseId = options.getOrDefault("license-id", packageName + "-" + Instant.now().getEpochSecond());
        List<String> features = parseFeatures(requireOption(options, "features"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("licenseId", licenseId);
        if (customer != null && !customer.isBlank()) {
            payload.put("customer", customer.trim());
        }
        payload.put("packageName", packageName.trim());
        payload.put("signingCertSha256", signingCertSha256);
        payload.put("features", features);
        payload.put("notBefore", notBefore);
        payload.put("notAfter", notAfter);

        String payloadJson = canonicalize(payload);
        Ed25519PrivateKeyParameters privateKey = readPrivateKey(privateKeyPath);
        String signatureBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payloadJson, privateKey));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", 1);
        envelope.put("algorithm", "Ed25519");
        envelope.put("keyId", keyId);
        envelope.put("payload", payload);
        envelope.put("signature", signatureBase64Url);

        String outputJson = GSON.toJson(envelope) + System.lineSeparator();
        String outputPath = options.get("output");
        if (outputPath == null || outputPath.isBlank()) {
            System.out.print(outputJson);
            return;
        }
        Files.writeString(Path.of(outputPath), outputJson, StandardCharsets.UTF_8);
    }

    private static void signKey(Map<String, String> options) throws Exception {
        Path privateKeyPath = requirePath(options, "private-key");
        String appPkgId = requireOption(options, "app-pkg-id").trim();
        String issuedAt = requireUtcTimestamp(options, "issued-at");
        String expireAt = requireUtcTimestamp(options, "expire-at");
        String signingCertSha256 = options.containsKey("signing-cert-sha256")
                ? normalizeDigest(requireOption(options, "signing-cert-sha256"))
                : null;
        List<String> features = options.containsKey("features")
                ? parseFeatures(requireOption(options, "features"))
                : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_pkg_id", appPkgId);
        payload.put("issued_at", issuedAt);
        payload.put("expire_at", expireAt);
        if (signingCertSha256 != null && !signingCertSha256.isBlank()) {
            payload.put("signing_cert_sha256", signingCertSha256);
        }
        if (features != null) {
            payload.put("features", features);
        }

        String payloadJson = canonicalize(payload);
        Ed25519PrivateKeyParameters privateKey = readPrivateKey(privateKeyPath);
        String payloadBase64Url = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signatureBase64Url = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(payloadJson, privateKey));
        String licenseKey = payloadBase64Url + "." + signatureBase64Url;

        String outputPath = options.get("output");
        if (outputPath == null || outputPath.isBlank()) {
            System.out.println(licenseKey);
            return;
        }
        Files.writeString(Path.of(outputPath), licenseKey + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static byte[] sign(String payloadJson, Ed25519PrivateKeyParameters privateKey) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        signer.update(payloadBytes, 0, payloadBytes.length);
        return signer.generateSignature();
    }

    private static Ed25519PrivateKeyParameters readPrivateKey(Path privateKeyPath) throws Exception {
        String pem = Files.readString(privateKeyPath, StandardCharsets.UTF_8);
        String stripped = pem
                .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                .replace("-----END OPENSSH PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] blob = Base64.getDecoder().decode(stripped);
        CipherParameters parameters = OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(blob);
        if (!(parameters instanceof Ed25519PrivateKeyParameters privateKey)) {
            throw new IllegalArgumentException("Expected an Ed25519 OpenSSH private key: " + privateKeyPath);
        }
        return privateKey;
    }

    private static Ed25519PublicKeyParameters readPublicKey(Path publicKeyPath) throws Exception {
        String keyLine = Files.readString(publicKeyPath, StandardCharsets.UTF_8).trim();
        String[] parts = keyLine.split("\\s+");
        if (parts.length < 2 || !"ssh-ed25519".equals(parts[0])) {
            throw new IllegalArgumentException("Expected an ssh-ed25519 public key: " + publicKeyPath);
        }
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        CipherParameters parameters = OpenSSHPublicKeyUtil.parsePublicKey(blob);
        if (!(parameters instanceof Ed25519PublicKeyParameters publicKey)) {
            throw new IllegalArgumentException("Expected an Ed25519 public key: " + publicKeyPath);
        }
        return publicKey;
    }

    private static Path requirePath(Map<String, String> options, String key) {
        return Path.of(requireOption(options, key));
    }

    private static String requireOption(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }

    private static String requireUtcTimestamp(Map<String, String> options, String key) {
        String value = requireOption(options, key).trim();
        try {
            Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Option --" + key + " must be an ISO-8601 UTC timestamp like 2027-01-01T00:00:00Z", exception);
        }
        return value;
    }

    private static List<String> parseFeatures(String value) {
        List<String> features = new ArrayList<>();
        for (String feature : value.split(",")) {
            String normalized = feature.trim().toLowerCase(Locale.US);
            if (!normalized.isEmpty()) {
                features.add(normalized);
            }
        }
        if (features.isEmpty()) {
            throw new IllegalArgumentException("At least one feature is required");
        }
        return features;
    }

    private static String normalizeDigest(String value) {
        return value.replace(":", "").trim().toUpperCase(Locale.US);
    }

    @SuppressWarnings("unchecked")
    private static String canonicalize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return GSON.toJson(stringValue);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> listValue) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < listValue.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalize(listValue.get(index)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?> mapValue) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(GSON.toJson(entry.getKey()));
                builder.append(':');
                builder.append(canonicalize(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        throw new IllegalArgumentException("Unsupported canonical JSON value: " + value.getClass());
    }
}
