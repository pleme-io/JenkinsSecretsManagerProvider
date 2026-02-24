package io.jenkins.plugins.akeyless.credentials.provider;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import io.jenkins.plugins.akeyless.credentials.AkeylessCredential;
import io.jenkins.plugins.akeyless.credentials.provider.config.AkeylessCredentialsProviderConfig;
import io.jenkins.plugins.akeyless.credentials.provider.supplier.CredentialsSupplier;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class AkeylessCredentialsProvider extends CredentialsProvider {

    private static final Logger LOG = Logger.getLogger(AkeylessCredentialsProvider.class.getName());

    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentials(@Nonnull Class<C> type,
                                                          @Nonnull ItemGroup itemGroup,
                                                          @Nonnull Authentication authentication) {
        LOG.log(Level.INFO, "Akeyless Credentials Provider: getCredentials called type={0} itemGroup={1} auth={2}",
                new Object[]{type.getSimpleName(), itemGroup != null ? itemGroup.getClass().getSimpleName() : "null", authentication != null ? "present" : "null"});

        // Never supply AkeylessCredential (auth TO Akeyless). Those come from the regular Jenkins store.
        // Returning them here would cause infinite recursion: buildClient() looks up AkeylessCredential, which calls us again.
        if (AkeylessCredential.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        // Only supply credentials in global (Jenkins) context; we have no store for folders/jobs
        if (itemGroup != Jenkins.get()) {
            LOG.log(Level.FINE, "Akeyless Credentials Provider: skipping non-global context (itemGroup != Jenkins)");
            return Collections.emptyList();
        }
        // Return credentials when called as SYSTEM (e.g. by our store) or when the user has VIEW permission (e.g. credentials list / pipeline)
        if (!ACL.SYSTEM.equals(authentication) && !Jenkins.get().getACL().hasPermission(authentication, CredentialsProvider.VIEW)) {
            LOG.log(Level.FINE, "Akeyless Credentials Provider: no permission (not SYSTEM and no VIEW)");
            return Collections.emptyList();
        }
        AkeylessCredentialsProviderConfig config = AkeylessCredentialsProviderConfig.get();
        if (config == null || !config.isConfigured()) {
            LOG.log(Level.INFO, "Akeyless Credentials Provider: config missing or not configured, returning no credentials");
            return Collections.emptyList();
        }
        Collection<StandardCredentials> all = CredentialsSupplier.get(config);
        List<C> filtered = all.stream()
                .filter(c -> type.isAssignableFrom(c.getClass()))
                .map(type::cast)
                .collect(Collectors.toList());
        LOG.log(Level.INFO, "Akeyless Credentials Provider: returning {0} credential(s) for type {1}", new Object[]{filtered.size(), type.getSimpleName()});
        return filtered;
    }

    @Override
    public CredentialsStore getStore(ModelObject object) {
        return object == Jenkins.get() ? new AkeylessCredentialsStore(this) : null;
    }

    /** Icon shown next to the Akeyless store in Credentials. Change to a Jenkins symbol (e.g. symbol-key, symbol-lock) or add CSS for icon-akeyless-credentials-store. */
    @Override
    public String getIconClassName() {
        return "icon-akeyless-credentials-store";
    }
}
