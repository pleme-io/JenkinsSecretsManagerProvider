package io.jenkins.plugins.akeyless.credentials.provider.client;

import io.akeyless.client.ApiClient;
import io.akeyless.client.ApiException;
import io.akeyless.client.api.V2Api;
import io.akeyless.client.model.Auth;
import io.akeyless.client.model.AuthOutput;
import io.akeyless.client.model.ListItems;
import io.akeyless.client.model.ListItemsInPathOutput;
import io.jenkins.plugins.akeyless.credentials.AkeylessCredential;
import io.jenkins.plugins.akeyless.credentials.CredentialsPayload;
import io.jenkins.plugins.akeyless.cloudid.CloudIdProvider;
import io.jenkins.plugins.akeyless.cloudid.CloudProviderFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Client for Akeyless API: authenticate (using Akeyless plugin credential), list items, get secret value, get tags.
 */
public class AkeylessClient {

    private static final Logger LOG = Logger.getLogger(AkeylessClient.class.getName());

    private final String basePath;
    private final boolean skipSslVerification;
    private final AkeylessCredential credential;
    private ApiClient apiClient;
    private V2Api api;
    private String token;
    private HttpClient httpClient;

    public AkeylessClient(@Nonnull String akeylessUrl, boolean skipSslVerification, @Nonnull AkeylessCredential credential) {
        this.basePath = akeylessUrl.endsWith("/") ? akeylessUrl : akeylessUrl + "/";
        this.skipSslVerification = skipSslVerification;
        this.credential = credential;
    }

    /**
     * Ensure we have a valid token; authenticate if needed.
     */
    public synchronized String getToken() throws ApiException {
        if (token != null && !token.isEmpty()) {
            return token;
        }
        CredentialsPayload payload = credential.getCredentialsPayload();
        String existingToken = payload.getToken() == null ? null : payload.getToken().getPlainText();
        if (existingToken != null && !existingToken.isEmpty()) {
            token = existingToken;
            return token;
        }
        Auth auth = payload.getAuth();
        if (payload.isCloudIdNeeded()) {
            try {
                CloudIdProvider idProvider = CloudProviderFactory.getCloudIdProvider(auth.getAccessType());
                auth.setCloudId(idProvider.getCloudId());
            } catch (Exception e) {
                throw new ApiException(0, "Failed to get cloud ID: " + e.getMessage());
            }
        }
        if (api == null) {
            apiClient = new ApiClient();
            apiClient.setBasePath(basePath);
            apiClient.setVerifyingSsl(!skipSslVerification);
            api = new V2Api(apiClient);
        }
        // Use Java HttpClient for auth so we guarantee POST and no redirect-to-GET. The plugin's
        // ApiClient/OkHttp can send GET or follow redirects as GET, causing 405.
        String authUrl = basePath + "auth";
        String jsonBody = apiClient.getJSON().serialize(auth);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApiException(0, "Auth failed: HTTP " + response.statusCode() + " " + response.body());
            }
            AuthOutput authOutput = apiClient.getJSON().deserialize(response.body(), AuthOutput.class);
            if (authOutput == null || authOutput.getToken() == null) {
                throw new ApiException(0, "Auth response had no token");
            }
            token = authOutput.getToken();
        } catch (java.io.IOException e) {
            throw new ApiException(0, "Auth request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Auth interrupted: " + e.getMessage());
        }
        return token;
    }

    /** Build HttpClient once; use for all POST calls so we never send GET (plugin SDK sends GET). */
    private HttpClient getHttpClient() throws ApiException {
        if (httpClient != null) return httpClient;
        HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
        if (skipSslVerification) {
            try {
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new java.security.SecureRandom());
                builder.sslContext(ssl);
            } catch (Exception e) {
                throw new ApiException(0, "Failed to set up SSL: " + e.getMessage());
            }
        }
        httpClient = builder.build();
        return httpClient;
    }

    /** POST to Akeyless API path with JSON body; deserialize response. Avoids plugin SDK sending GET. */
    private <T> T post(String path, Object body, Class<T> responseType) throws ApiException {
        String body2 = postRaw(path, body);
        return apiClient.getJSON().deserialize(body2, responseType);
    }

    /** POST to Akeyless API path with JSON body; return raw response body (for list-items to avoid SDK strict model). */
    private String postRaw(String path, Object body) throws ApiException {
        ensureApiClient();
        String url = basePath + path;
        String jsonBody = apiClient.getJSON().serialize(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return sendRequest(path, request);
    }

    private String sendRequest(String path, HttpRequest request) throws ApiException {
        try {
            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApiException(0, path + " failed: HTTP " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new ApiException(0, path + " request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, path + " interrupted: " + e.getMessage());
        }
    }

    private void ensureApiClient() throws ApiException {
        getToken();
        if (apiClient == null) {
            apiClient = new ApiClient();
            apiClient.setBasePath(basePath);
            apiClient.setVerifyingSsl(!skipSslVerification);
            api = new V2Api(apiClient);
        }
    }

    private V2Api api() throws ApiException {
        getToken();
        return api;
    }

    /**
     * Recursively list all static-secret items under an optional path prefix.
     * Akeyless list-items only returns items at one path level, so we recurse into subfolders.
     */
    @Nonnull
    public List<AkeylessItem> listItems(String pathPrefix) throws ApiException {
        String pathForRequest = null;
        if (pathPrefix != null && !pathPrefix.isEmpty()) {
            pathForRequest = pathPrefix.trim().replaceAll("/+$", "");
        }
        LOG.log(Level.INFO, "Akeyless API: listItems recursive (pathPrefix={0})", pathForRequest != null ? pathForRequest : "<root>");
        List<AkeylessItem> allSecrets = new java.util.ArrayList<>();
        listItemsRecursive(pathForRequest, allSecrets, 0);
        LOG.log(Level.INFO, "Akeyless API: listItems recursive found {0} static secret(s) total", allSecrets.size());
        return allSecrets;
    }

    private static final int MAX_RECURSION_DEPTH = 20;

    private void listItemsRecursive(String path, List<AkeylessItem> secrets, int depth) throws ApiException {
        if (depth > MAX_RECURSION_DEPTH) {
            LOG.log(Level.WARNING, "Akeyless list-items: max recursion depth reached at path={0}", path);
            return;
        }
        ListItems body = new ListItems();
        body.setToken(getToken());
        if (path != null && !path.isEmpty()) {
            body.setPath(path);
        }
        String raw = postRaw("list-items", body);
        if (isJsonEmptyObject(raw)) {
            return;
        }
        ParsedListItems parsed = parseListItemsAll(raw);
        secrets.addAll(parsed.secrets);
        for (String folderPath : parsed.folderPaths) {
            LOG.log(Level.FINE, "Akeyless list-items: recursing into folder {0}", folderPath);
            listItemsRecursive(folderPath, secrets, depth + 1);
        }
    }

    private static boolean isJsonEmptyObject(String json) {
        if (json == null) return false;
        String s = json.trim();
        return "{}".equals(s);
    }

    /** Result of parsing a list-items response: secrets found at this level and folder paths to recurse into. */
    private static final class ParsedListItems {
        final List<AkeylessItem> secrets = new java.util.ArrayList<>();
        final List<String> folderPaths = new java.util.ArrayList<>();
    }

    /** Parse list-items JSON response; returns secrets and folder paths. */
    private ParsedListItems parseListItemsAll(String json) throws ApiException {
        ParsedListItems result = new ParsedListItems();
        try {
            JsonElement parsed = JsonParser.parseString(json);
            JsonArray items = null;
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (root.has("items") && root.get("items").isJsonArray()) {
                    items = root.getAsJsonArray("items");
                } else if (root.has("Items") && root.get("Items").isJsonArray()) {
                    items = root.getAsJsonArray("Items");
                }
                // Also check for "folders" / "Folders" array
                JsonArray folders = null;
                if (root.has("folders") && root.get("folders").isJsonArray()) {
                    folders = root.getAsJsonArray("folders");
                } else if (root.has("Folders") && root.get("Folders").isJsonArray()) {
                    folders = root.getAsJsonArray("Folders");
                }
                if (folders != null) {
                    for (JsonElement f : folders) {
                        if (f.isJsonPrimitive() && f.getAsJsonPrimitive().isString()) {
                            result.folderPaths.add(f.getAsString());
                        } else if (f.isJsonObject()) {
                            String fp = firstNonEmptyJson(f.getAsJsonObject(), "folder_name", "FolderName", "name", "path");
                            if (fp != null) result.folderPaths.add(fp);
                        }
                    }
                }
                if (items == null) {
                    String snippet = json.length() > 400 ? json.substring(0, 400) + "..." : json;
                    LOG.log(Level.FINE, "Akeyless list-items response has no items array (keys: {0}). Snippet: {1}",
                            new Object[]{root.keySet(), snippet});
                    return result;
                }
            } else if (parsed.isJsonArray()) {
                items = parsed.getAsJsonArray();
            }
            if (items == null) {
                return result;
            }
            for (JsonElement el : items) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = firstNonEmptyJson(o, "ItemName", "item_name", "name");
                String path = firstNonEmptyJson(o, "ItemPath", "item_path", "path");
                String type = firstNonEmptyJson(o, "ItemType", "item_type", "type");
                if (name == null) name = path != null ? path : "";
                if (path == null) path = name;
                if (isFolderType(type)) {
                    String folderPath = path != null && !path.isEmpty() ? path : name;
                    if (folderPath != null && !folderPath.isEmpty()) {
                        result.folderPaths.add(folderPath);
                    }
                    continue;
                }
                if (!isStaticSecretType(type)) {
                    continue;
                }
                result.secrets.add(new AkeylessItem(name, type != null ? type : "", path));
            }
            LOG.log(Level.FINE, "Akeyless list-items parsed {0} secret(s), {1} folder(s)",
                    new Object[]{result.secrets.size(), result.folderPaths.size()});
        } catch (Exception e) {
            throw new ApiException(0, "Failed to parse list-items response: " + e.getMessage());
        }
        return result;
    }

    private static boolean isStaticSecretType(String type) {
        if (type == null || type.isEmpty()) return true;
        String n = type.replace('-', '_').replace(' ', '_');
        return "STATIC_SECRET".equalsIgnoreCase(n) || "STATICSECRET".equalsIgnoreCase(n);
    }

    private static boolean isFolderType(String type) {
        if (type == null || type.isEmpty()) return false;
        String n = type.replace('-', '_').replace(' ', '_').toLowerCase();
        return "folder".equals(n);
    }

    private static String firstNonEmptyJson(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                String v = o.get(key).getAsString();
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return null;
    }

    /**
     * Get secret value for a static secret by name (full path), e.g. /CICD/jenkins/apikey.
     * Uses POST to get-secret-value (same as CLI: akeyless get-secret-value --name /path) so the
     * request is not sent as GET by the SDK (which can cause 405).
     */
    @Nonnull
    public GetSecretValueResult getSecretValue(String name) throws ApiException {
        String pathForApi = name;
        if (pathForApi != null && !pathForApi.isEmpty() && !pathForApi.startsWith("/")) {
            pathForApi = "/" + pathForApi;
        }
        LOG.log(Level.INFO, "Akeyless Credentials Provider: get-secret-value for path={0}", pathForApi);
        String raw;
        try {
            // Try "name" first (CLI-style). If gateway returns {}, retry with "names" array (SDK-style).
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("token", getToken());
            bodyMap.put("name", pathForApi);
            String jsonBody = new com.google.gson.Gson().toJson(bodyMap);
            ensureApiClient();
            String url = basePath + "get-secret-value";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            raw = sendRequest("get-secret-value", request);
        } catch (ApiException e) {
            LOG.log(Level.WARNING, "Akeyless Credentials Provider: get-secret-value failed for {0}: {1}", new Object[]{pathForApi, e.getMessage()});
            throw e;
        }
        Object value = parseGetSecretValueResponse(raw, pathForApi, name);
        // If response is {} or unparseable, retry with "names" array (some gateways only accept this)
        if (value == null && (raw == null || raw.trim().isEmpty() || "{}".equals(raw.trim()))) {
            LOG.log(Level.INFO, "Akeyless Credentials Provider: get-secret-value got empty response, retrying with names array");
            try {
                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("token", getToken());
                bodyMap.put("names", Collections.singletonList(pathForApi));
                bodyMap.put("json", true);
                String jsonBody = new com.google.gson.Gson().toJson(bodyMap);
                ensureApiClient();
                String url = basePath + "get-secret-value";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                raw = sendRequest("get-secret-value", request);
                value = parseGetSecretValueResponse(raw, pathForApi, name);
            } catch (ApiException e2) {
                LOG.log(Level.WARNING, "Akeyless Credentials Provider: get-secret-value (names) failed: {0}", e2.getMessage());
                throw e2;
            }
        }
        // If still empty, try form-encoded (cmd=get-secret-value&name=...&token=...) as some gateways expect it
        if (value == null && (raw == null || raw.trim().isEmpty() || "{}".equals(raw.trim()))) {
            LOG.log(Level.INFO, "Akeyless Credentials Provider: get-secret-value retrying with form-encoded body");
            try {
                String formBody = "token=" + java.net.URLEncoder.encode(getToken(), "UTF-8")
                        + "&name=" + java.net.URLEncoder.encode(pathForApi, "UTF-8")
                        + "&cmd=get-secret-value";
                ensureApiClient();
                String url = basePath + "get-secret-value";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();
                raw = sendRequest("get-secret-value", request);
                value = parseGetSecretValueResponse(raw, pathForApi, name);
            } catch (ApiException e2) {
                LOG.log(Level.WARNING, "Akeyless Credentials Provider: get-secret-value (form) failed: {0}", e2.getMessage());
                throw e2;
            } catch (java.io.UnsupportedEncodingException e2) {
                throw new ApiException(0, "URL encode: " + e2.getMessage());
            }
        }
        if (value == null) {
            String snippet = raw != null && raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
            LOG.log(Level.WARNING, "Akeyless Credentials Provider: get-secret-value returned no parseable value for {0}. Response: {1}", new Object[]{pathForApi, snippet});
            throw new ApiException("No value returned for secret: " + name);
        }
        LOG.log(Level.FINE, "Akeyless Credentials Provider: get-secret-value succeeded for {0}", pathForApi);
        if (value instanceof String) {
            return GetSecretValueResult.string((String) value);
        }
        if (value instanceof byte[]) {
            return GetSecretValueResult.binary((byte[]) value);
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) value;
            if (m.containsKey("value")) {
                Object v = m.get("value");
                if (v instanceof String) return GetSecretValueResult.string((String) v);
                if (v instanceof byte[]) return GetSecretValueResult.binary((byte[]) v);
            }
            if (m.containsKey("val")) {
                Object v = m.get("val");
                if (v instanceof String) return GetSecretValueResult.string((String) v);
            }
            return GetSecretValueResult.string(m.toString());
        }
        return GetSecretValueResult.string(value.toString());
    }

    /**
     * Parse get-secret-value API response. Response may be: a single value string, an object
     * with "value"/"val" or with the secret path as key (map of path -> value).
     */
    private Object parseGetSecretValueResponse(String json, String pathForApi, String name) throws ApiException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json.trim());
            if (parsed.isJsonPrimitive()) {
                return parsed.getAsJsonPrimitive().isString() ? parsed.getAsString() : parsed.getAsString();
            }
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                String err = root.get("error").getAsString();
                throw new ApiException(0, "get-secret-value error: " + err);
            }
            // Direct value/val (CLI-style response)
            if (root.has("value") && !root.get("value").isJsonNull()) {
                return root.get("value").getAsString();
            }
            if (root.has("val") && !root.get("val").isJsonNull()) {
                return root.get("val").getAsString();
            }
            // Map: path -> value (SDK-style when names array is used)
            for (String key : new String[]{pathForApi, name, "/" + name}) {
                if (key != null && root.has(key) && !root.get(key).isJsonNull()) {
                    JsonElement v = root.get(key);
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                        return v.getAsString();
                    }
                    if (v.isJsonObject()) {
                        JsonObject o = v.getAsJsonObject();
                        if (o.has("value")) return o.get("value").getAsString();
                        if (o.has("val")) return o.get("val").getAsString();
                    }
                    return v.toString();
                }
            }
            // Single key in object (e.g. API returns { "<path>": "secret" })
            if (root.size() == 1) {
                return root.entrySet().iterator().next().getValue().getAsString();
            }
            return null;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "Failed to parse get-secret-value response: " + e.getMessage());
        }
    }

    /**
     * Get tags for an item. Uses get-tags API if available; otherwise returns empty map.
     * Akeyless tags are key-value; we expect jenkins:credentials:type, etc.
     */
    @Nonnull
    public Map<String, String> getTags(String itemName) {
        try {
            // V2Api may have getTags(GetTags body); fallback to empty if not present
            return getTagsInternal(itemName);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not get tags for " + itemName + ", using defaults", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> getTagsInternal(String itemName) {
        // SDK may not have getTags; use reflection so we don't require GetTags/GetTagsOutput at compile time
        try {
            Class<?> getTagsClass = Class.forName("io.akeyless.client.model.GetTags");
            Object body = getTagsClass.getDeclaredConstructor().newInstance();
            body.getClass().getMethod("setToken", String.class).invoke(body, getToken());
            body.getClass().getMethod("setName", String.class).invoke(body, itemName);
            java.lang.reflect.Method apiMethod = api().getClass().getMethod("getTags", getTagsClass);
            Object out = apiMethod.invoke(api(), body);
            if (out != null) {
                java.lang.reflect.Method getTags = out.getClass().getMethod("getTags");
                Object tagsObj = getTags.invoke(out);
                if (tagsObj != null && tagsObj instanceof Iterable) {
                    Map<String, String> map = new HashMap<>();
                    for (Object t : (Iterable<?>) tagsObj) {
                        Object k = t.getClass().getMethod("getKey").invoke(t);
                        Object v = t.getClass().getMethod("getValue").invoke(t);
                        if (k != null && v != null) map.put(k.toString(), v.toString());
                    }
                    return map;
                }
            }
        } catch (Exception ignored) {
            // SDK might not have getTags
        }
        return Collections.emptyMap();
    }

    private static String getItemAttr(Object item, String... methodOrFieldNames) {
        for (String name : methodOrFieldNames) {
            try {
                if (name.startsWith("get") && name.length() > 3) {
                    java.lang.reflect.Method m = item.getClass().getMethod(name);
                    Object v = m.invoke(item);
                    if (v != null) return v.toString();
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }

    public static final class AkeylessItem {
        private final String name;
        private final String itemType;
        private final String path;

        public AkeylessItem(String name, String itemType, String path) {
            this.name = name;
            this.itemType = itemType;
            this.path = path;
        }

        public String getName() { return name; }
        public String getItemType() { return itemType; }
        public String getPath() { return path; }
    }

    public static final class GetSecretValueResult {
        private final String stringValue;
        private final byte[] binaryValue;

        private GetSecretValueResult(String stringValue, byte[] binaryValue) {
            this.stringValue = stringValue;
            this.binaryValue = binaryValue;
        }

        public static GetSecretValueResult string(String s) {
            return new GetSecretValueResult(s, null);
        }

        public static GetSecretValueResult binary(byte[] b) {
            return new GetSecretValueResult(null, b);
        }

        public boolean isString() { return stringValue != null; }
        public String getStringValue() { return stringValue; }
        public byte[] getBinaryValue() { return binaryValue; }
    }
}
