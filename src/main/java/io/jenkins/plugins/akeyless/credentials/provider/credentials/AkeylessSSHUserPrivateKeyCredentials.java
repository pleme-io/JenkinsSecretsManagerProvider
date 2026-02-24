package io.jenkins.plugins.akeyless.credentials.provider.credentials;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsUnavailableException;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.util.Secret;
import io.akeyless.client.ApiException;
import io.jenkins.plugins.akeyless.credentials.provider.client.AkeylessClient;
import io.jenkins.plugins.akeyless.credentials.provider.client.AkeylessClient.GetSecretValueResult;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

public class AkeylessSSHUserPrivateKeyCredentials extends BaseStandardCredentials implements SSHUserPrivateKey {
    private static final Secret NO_PASSPHRASE = Secret.fromString("");

    private final String akeylessPath;
    private final String username;

    @DataBoundConstructor
    public AkeylessSSHUserPrivateKeyCredentials(String id, String akeylessPath, String description, String username) {
        super(id, description);
        this.akeylessPath = akeylessPath != null ? akeylessPath : id;
        this.username = username != null ? username : "";
    }

    @NonNull
    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @Deprecated
    public String getPrivateKey() {
        return getPrivateKeys().isEmpty() ? "" : getPrivateKeys().get(0);
    }

    @Override
    public Secret getPassphrase() {
        return NO_PASSPHRASE;
    }

    @NonNull
    @Override
    public List<String> getPrivateKeys() {
        AkeylessClient client = AkeylessStringCredentials.getClient();
        try {
            GetSecretValueResult r = client.getSecretValue(akeylessPath);
            if (r.isString()) {
                return Collections.singletonList(r.getStringValue());
            }
            throw new CredentialsUnavailableException("Secret '" + akeylessPath + "' is binary, cannot use as SSH key");
        } catch (ApiException e) {
            throw new CredentialsUnavailableException("Could not retrieve secret from Akeyless: " + e.getMessage(), e);
        }
    }
}
