package io.jenkins.plugins.akeyless.credentials.provider.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.akeyless.credentials.AkeylessCredential;
import io.jenkins.plugins.akeyless.credentials.provider.client.AkeylessClient;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import hudson.security.ACL;

/**
 * Global configuration for Akeyless Credentials Provider: Akeyless URL and credential (from Akeyless plugin) for authentication.
 */
@Extension
public class AkeylessCredentialsProviderConfig extends jenkins.model.GlobalConfiguration {

    public static AkeylessCredentialsProviderConfig get() {
        return jenkins.model.GlobalConfiguration.all().get(AkeylessCredentialsProviderConfig.class);
    }

    public AkeylessCredentialsProviderConfig() {
        load();
    }

    private String akeylessUrl;
    private String credentialId;
    private String pathPrefix;
    private boolean skipSslVerification;

    public String getAkeylessUrl() {
        return akeylessUrl;
    }

    public void setAkeylessUrl(String akeylessUrl) {
        this.akeylessUrl = akeylessUrl;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public boolean isSkipSslVerification() {
        return skipSslVerification;
    }

    public void setSkipSslVerification(boolean skipSslVerification) {
        this.skipSslVerification = skipSslVerification;
    }

    public boolean isConfigured() {
        return akeylessUrl != null && !akeylessUrl.isBlank() && credentialId != null && !credentialId.isBlank();
    }

    /**
     * Build an AkeylessClient using the configured URL and credential. Returns null if not configured or credential not found.
     */
    @Nullable
    public AkeylessClient buildClient() {
        if (!isConfigured()) return null;
        Jenkins j = Jenkins.get();
        if (j == null) return null;
        List<AkeylessCredential> creds = CredentialsProvider.lookupCredentials(
                AkeylessCredential.class, j, ACL.SYSTEM, Collections.emptyList());
        AkeylessCredential cred = CredentialsMatchers.firstOrNull(creds, new IdMatcher(credentialId));
        if (cred == null) return null;
        String url = akeylessUrl.trim();
        if (!url.endsWith("/api/v2") && !url.endsWith("/api/v2/")) {
            url = url.endsWith("/") ? url + "api/v2" : url + "/api/v2";
        }
        return new AkeylessClient(url, skipSslVerification, cred);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        // Form fields are under the section name from config.jelly (f:section name="akeyless-credentials-provider")
        JSONObject section = json.optJSONObject("akeyless-credentials-provider");
        req.bindJSON(this, section != null ? section : json);
        save();
        return true;
    }

    /**
     * Fills the Akeyless credential dropdown. Uses credential ID as display when description is empty
     * so options are always visible. Includes current value so the saved selection stays visible.
     */
    public ListBoxModel doFillCredentialIdItems(@QueryParameter String credentialId) {
        ListBoxModel m = new ListBoxModel();
        m.add("— Select Akeyless credential —", "");
        Jenkins j = Jenkins.get();
        if (j != null) {
            List<AkeylessCredential> creds = CredentialsProvider.lookupCredentials(
                    AkeylessCredential.class, j, ACL.SYSTEM, Collections.emptyList());
            for (AkeylessCredential c : creds) {
                String name = c.getDescription();
                if (name == null || name.isBlank()) {
                    name = c.getId();
                }
                m.add(name, c.getId());
            }
            // Keep current selection visible even if credential was removed (e.g. show ID)
            if (credentialId != null && !credentialId.isBlank()) {
                boolean found = creds.stream().anyMatch(c -> credentialId.equals(c.getId()));
                if (!found) {
                    m.add("(current: " + credentialId + ")", credentialId);
                }
            }
        }
        return m;
    }
}
