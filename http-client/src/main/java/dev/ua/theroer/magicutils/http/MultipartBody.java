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

    /**
     * Creates a new multipart body builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the Content-Type header value for this multipart body.
     *
     * @return content type with boundary
     */
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * Builds the body publisher for the multipart payload.
     *
     * @return body publisher
     */
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

    /**
     * Builder for multipart/form-data content.
     */
    public static final class Builder {
        private final List<Part> parts = new ArrayList<>();
        private String boundary;

        /**
         * Creates a new builder.
         */
        public Builder() {
        }

        /**
         * Overrides the multipart boundary string.
         *
         * @param boundary boundary value
         * @return this builder
         */
        public Builder boundary(String boundary) {
            this.boundary = boundary;
            return this;
        }

        /**
         * Adds a text field part.
         *
         * @param name field name
         * @param value field value
         * @return this builder
         */
        public Builder text(String name, String value) {
            Objects.requireNonNull(name, "name");
            String headers = "Content-Disposition: form-data; name=\"" + escape(name) + "\"" + CRLF + CRLF;
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(value != null ? value : "", StandardCharsets.UTF_8);
            parts.add(new Part(headers, body));
            return this;
        }

        /**
         * Adds a file part with a detected content type.
         *
         * @param name field name
         * @param path file path
         * @return this builder
         * @throws IOException if the file cannot be read
         */
        public Builder file(String name, Path path) throws IOException {
            return file(name, path, null, null);
        }

        /**
         * Adds a file part with custom content type and filename.
         *
         * @param name field name
         * @param path file path
         * @param contentType content type or null to auto-detect
         * @param filename file name or null to use the path name
         * @return this builder
         * @throws IOException if the file cannot be read
         */
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

        /**
         * Adds a raw byte part with optional content type and filename.
         *
         * @param name field name
         * @param data byte array payload
         * @param contentType content type or null for default
         * @param filename filename or null for default
         * @return this builder
         */
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

        /**
         * Builds the multipart body.
         *
         * @return multipart body
         */
        public MultipartBody build() {
            String actualBoundary = boundary != null && !boundary.isBlank() ? boundary : randomBoundary();
            return new MultipartBody(actualBoundary, new ArrayList<>(parts));
        }
    }

    private record Part(String headers, HttpRequest.BodyPublisher body) {
    }
}
