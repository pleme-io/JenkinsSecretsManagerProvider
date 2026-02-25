package io.jenkins.plugins.akeyless.credentials.provider.client;

import io.akeyless.client.ApiClient;
import io.akeyless.client.ApiException;
import io.akeyless.client.api.V2Api;
import io.akeyless.client.model.Auth;
import io.akeyless.client.model.AuthOutput;
import io.akeyless.client.model.GetSecretValue;
import io.jenkins.plugins.akeyless.credentials.provider.auth.AuthMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AkeylessClient {

    private static final Logger LOG = Logger.getLogger(AkeylessClient.class.getName());

    private final String basePath;
    private final String accessId;
    private final AuthMethod authMethod;
    private V2Api api;
    private String token;

    public AkeylessClient(@Nonnull String akeylessUrl, @Nullable String accessId, @Nonnull AuthMethod authMethod) {
        this.basePath = akeylessUrl.endsWith("/") ? akeylessUrl.substring(0, akeylessUrl.length() - 1) : akeylessUrl;
        this.accessId = accessId;
        this.authMethod = authMethod;
    }

    private synchronized V2Api api() {
        if (api == null) {
            ApiClient client = new ApiClient();
            client.setBasePath(basePath);
            api = new V2Api(client);
        }
        return api;
    }

    public synchronized String getToken() throws ApiException {
        if (token != null && !token.isEmpty()) {
            return token;
        }
        try {
            LOG.log(Level.INFO, "Akeyless: authenticating with method={0}, access_id={1}",
                    new Object[]{authMethod.getClass().getSimpleName(), accessId});
            Auth auth = authMethod.buildAuth(accessId);
            AuthOutput authOutput = api().auth(auth);
            token = authOutput != null ? authOutput.getToken() : null;
            if (token == null || token.isEmpty()) {
                throw new ApiException("Auth response had no token");
            }
            LOG.log(Level.INFO, "Akeyless: authenticated successfully with {0}", authMethod.getClass().getSimpleName());
            return token;
        } catch (ApiException e) {
            LOG.log(Level.WARNING, "Akeyless: authentication failed with {0}: {1}",
                    new Object[]{authMethod.getClass().getSimpleName(), e.getMessage()});
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Akeyless: authentication failed with {0}: {1}",
                    new Object[]{authMethod.getClass().getSimpleName(), e.getMessage()});
            throw new ApiException("Authentication failed: " + e.getMessage());
        }
    }

    @Nonnull
    public GetSecretValueResult getSecretValue(String name) throws ApiException {
        String pathForApi = name;
        if (pathForApi != null && !pathForApi.isEmpty() && !pathForApi.startsWith("/")) {
            pathForApi = "/" + pathForApi;
        }
        LOG.log(Level.INFO, "Akeyless: get-secret-value for path={0}", pathForApi);
        GetSecretValue body = new GetSecretValue();
        body.setToken(getToken());
        body.setNames(Collections.singletonList(pathForApi));
        Map<String, Object> out = api().getSecretValue(body);
        if (out == null || out.isEmpty()) {
            throw new ApiException("No value returned for secret: " + name);
        }
        Object value = out.get(pathForApi);
        if (value == null && out.size() == 1) {
            value = out.values().iterator().next();
        }
        if (value == null) {
            throw new ApiException("Empty value for secret: " + name);
        }
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
            return GetSecretValueResult.string(m.toString());
        }
        return GetSecretValueResult.string(value.toString());
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
