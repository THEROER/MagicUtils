package dev.ua.theroer.magicutils.http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple multipart/form-data builder.
 */
public final class MultipartBody {
    private static final String CRLF = "\r\n";
    private static final String BOUNDARY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final String boundary;
    private final List<Part> parts;

    private MultipartBody(String boundary, List<Part> parts) {
        this.boundary = boundary;
        this.parts = parts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public HttpRequest.BodyPublisher publisher() {
        List<HttpRequest.BodyPublisher> publishers = new ArrayList<>();
        for (Part part : parts) {
            publishers.add(HttpRequest.BodyPublishers.ofByteArray(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8)));
            publishers.add(HttpRequest.BodyPublishers.ofByteArray(part.headers().getBytes(StandardCharsets.UTF_8)));
            publishers.add(part.body());
            publishers.add(HttpRequest.BodyPublishers.ofByteArray(CRLF.getBytes(StandardCharsets.UTF_8)));
        }
        publishers.add(HttpRequest.BodyPublishers.ofByteArray(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8)));
        return HttpRequest.BodyPublishers.concat(publishers.toArray(new HttpRequest.BodyPublisher[0]));
    }

    private static String randomBoundary() {
        StringBuilder sb = new StringBuilder(24);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 24; i++) {
            sb.append(BOUNDARY_CHARS.charAt(random.nextInt(BOUNDARY_CHARS.length())));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    public static final class Builder {
        private final List<Part> parts = new ArrayList<>();
        private String boundary;

        public Builder boundary(String boundary) {
            this.boundary = boundary;
            return this;
        }

        public Builder text(String name, String value) {
            Objects.requireNonNull(name, "name");
            String headers = "Content-Disposition: form-data; name=\"" + escape(name) + "\"" + CRLF + CRLF;
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(value != null ? value : "", StandardCharsets.UTF_8);
            parts.add(new Part(headers, body));
            return this;
        }

        public Builder file(String name, Path path) throws IOException {
            return file(name, path, null, null);
        }

        public Builder file(String name, Path path, String contentType, String filename) throws IOException {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
            String resolvedType = contentType != null ? contentType : Files.probeContentType(path);
            if (resolvedType == null) {
                resolvedType = "application/octet-stream";
            }
            String resolvedName = filename != null ? filename : path.getFileName().toString();
            String headers = "Content-Disposition: form-data; name=\"" + escape(name) + "\"; filename=\""
                    + escape(resolvedName) + "\"" + CRLF
                    + "Content-Type: " + resolvedType + CRLF + CRLF;
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofFile(path);
            parts.add(new Part(headers, body));
            return this;
        }

        public Builder bytes(String name, byte[] data, String contentType, String filename) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(data, "data");
            String resolvedType = contentType != null ? contentType : "application/octet-stream";
            String resolvedName = filename != null ? filename : "blob";
            String headers = "Content-Disposition: form-data; name=\"" + escape(name) + "\"; filename=\""
                    + escape(resolvedName) + "\"" + CRLF
                    + "Content-Type: " + resolvedType + CRLF + CRLF;
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofByteArray(data);
            parts.add(new Part(headers, body));
            return this;
        }

        public MultipartBody build() {
            String actualBoundary = boundary != null && !boundary.isBlank() ? boundary : randomBoundary();
            return new MultipartBody(actualBoundary, new ArrayList<>(parts));
        }
    }

    private record Part(String headers, HttpRequest.BodyPublisher body) {
    }
}
