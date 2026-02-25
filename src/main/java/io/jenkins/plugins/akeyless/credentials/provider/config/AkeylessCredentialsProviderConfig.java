package io.jenkins.plugins.akeyless.credentials.provider.config;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.akeyless.credentials.provider.auth.AuthMethod;
import io.jenkins.plugins.akeyless.credentials.provider.client.AkeylessClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.util.List;

@Extension
public class AkeylessCredentialsProviderConfig extends jenkins.model.GlobalConfiguration {

    public static AkeylessCredentialsProviderConfig get() {
        return jenkins.model.GlobalConfiguration.all().get(AkeylessCredentialsProviderConfig.class);
    }

    public AkeylessCredentialsProviderConfig() {
        load();
    }

    private String akeylessUrl;
    private String accessId;
    private AuthMethod authMethod;
    /** Folder path: secrets are at folderPath + "/" + secretName. No listing. */
    private String folderPath;
    /** Secret names under the folder (one per line). In the job use credentials('secretName'). */
    private String secretNames;
    /** Full secret paths (one per line). Alternative to folder + names; no listing. */
    private String secretPaths;
    /** @deprecated use folderPath or secretPaths; kept for backward compatibility */
    private String pathPrefix;

    public String getAkeylessUrl() { return akeylessUrl; }

    @DataBoundSetter
    public void setAkeylessUrl(String akeylessUrl) { this.akeylessUrl = akeylessUrl; }

    public String getAccessId() { return accessId; }

    @DataBoundSetter
    public void setAccessId(String accessId) { this.accessId = accessId; }

    public AuthMethod getAuthMethod() { return authMethod; }

    @DataBoundSetter
    public void setAuthMethod(AuthMethod authMethod) { this.authMethod = authMethod; }

    /** Folder path; when not set, pathPrefix is used (so old config works as folder path). */
    public String getFolderPath() {
        if (folderPath != null && !folderPath.isBlank()) return folderPath;
        if (pathPrefix != null && !pathPrefix.isBlank()) return pathPrefix;
        return folderPath;
    }

    @DataBoundSetter
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public String getSecretNames() { return secretNames; }

    @DataBoundSetter
    public void setSecretNames(String secretNames) { this.secretNames = secretNames; }

    /** Full secret paths only (one per line). pathPrefix is not used here — it is used as folder path when Folder path is empty. */
    public String getSecretPaths() {
        return secretPaths;
    }

    @DataBoundSetter
    public void setSecretPaths(String secretPaths) { this.secretPaths = secretPaths; }

    /** @deprecated use folderPath or secretPaths */
    public String getPathPrefix() { return pathPrefix; }

    @DataBoundSetter
    public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }

    public boolean isConfigured() {
        return akeylessUrl != null && !akeylessUrl.isBlank()
                && authMethod != null
                && authMethod.isConfigured(accessId);
    }

    @Nullable
    public AkeylessClient buildClient() {
        if (!isConfigured()) return null;
        String url = akeylessUrl.trim();
        return new AkeylessClient(url, accessId != null ? accessId.trim() : null, authMethod);
    }

    public List<Descriptor<AuthMethod>> getAuthMethodDescriptors() {
        return AuthMethod.all().stream()
                .map(d -> (Descriptor<AuthMethod>) d)
                .toList();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        JSONObject section = json.optJSONObject("akeyless-credentials-provider");
        req.bindJSON(this, section != null ? section : json);
        save();
        return true;
    }
}
