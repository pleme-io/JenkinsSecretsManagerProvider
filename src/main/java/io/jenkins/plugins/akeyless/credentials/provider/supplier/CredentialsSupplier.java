package io.jenkins.plugins.akeyless.credentials.provider.supplier;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import io.jenkins.plugins.akeyless.credentials.provider.config.AkeylessCredentialsProviderConfig;
import io.jenkins.plugins.akeyless.credentials.provider.factory.CredentialsFactory;
import io.jenkins.plugins.akeyless.credentials.provider.factory.Tags;
import io.jenkins.plugins.akeyless.credentials.provider.factory.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Supplies credentials from user-provided secret paths only. No listing; only get-secret-value is used (via SDK).
 * In pipelines use short name (e.g. jenkinsai) or full path.
 */
public class CredentialsSupplier {

    private static final Logger LOG = Logger.getLogger(CredentialsSupplier.class.getName());
    private static final Pattern PATH_SPLIT = Pattern.compile("[,\n\r]+");

    public static Collection<StandardCredentials> get(AkeylessCredentialsProviderConfig config) {
        if (config == null || !config.isConfigured()) {
            LOG.log(Level.INFO, "Akeyless Credentials Provider: not configured (URL and auth required)");
            return Collections.emptyList();
        }
        String folderPath = config.getFolderPath();
        String secretNamesInput = config.getSecretNames();
        String secretPathsInput = config.getSecretPaths();
        boolean hasFolderAndNames = folderPath != null && !folderPath.isBlank()
                && secretNamesInput != null && !secretNamesInput.isBlank();
        boolean hasSecretPaths = secretPathsInput != null && !secretPathsInput.isBlank();
        if (!hasFolderAndNames && !hasSecretPaths) {
            LOG.log(Level.INFO, "Akeyless Credentials Provider: set ''Folder path'' + ''Secret names'', or ''Secret paths'', in Manage Jenkins → Configure System.");
            return Collections.emptyList();
        }
        if (folderPath != null && !folderPath.isBlank() && (secretNamesInput == null || secretNamesInput.isBlank())) {
            LOG.log(Level.WARNING, "Akeyless Credentials Provider: ''Folder path'' is set but ''Secret names'' is empty. Add secret names (e.g. jenkinsai) so you can use credentials('jenkinsai') in the job.");
        }
        try {
            if (config.buildClient() == null) {
                LOG.log(Level.WARNING, "Akeyless Credentials Provider: could not build client (check URL and auth)");
                return Collections.emptyList();
            }
            Map<String, String> defaultTags = new HashMap<>();
            defaultTags.put(Tags.TYPE, Type.STRING);

            Collection<StandardCredentials> result = new ArrayList<>();

            // 1) Folder path (no secret name) + secret names: full path = folderPath + "/" + secretName.
            //    Secret name is the credential id used in the pipeline (e.g. credentials('jenkinsai')).
            if (hasFolderAndNames) {
                String folderNorm = folderPath.trim().replaceAll("/+$", "");
                if (!folderNorm.startsWith("/")) folderNorm = "/" + folderNorm;
                String[] names = PATH_SPLIT.split(secretNamesInput);
                for (String raw : names) {
                    String name = raw.trim();
                    if (name.isEmpty()) continue;
                    String fullPath = folderNorm + "/" + name.replaceAll("^/+", "");  // e.g. /CICD/jenkins/test/test3/jenkinsai
                    String id = credentialIdFromPath(name);
                    if (id != null) {
                        CredentialsFactory.create(id, fullPath, fullPath, defaultTags).ifPresent(result::add);
                        LOG.log(Level.INFO, "Akeyless Credentials Provider: folder+name credential id={0} path={1}", new Object[]{id, fullPath});
                    }
                }
            }

            // 2) Explicit full secret paths (and pathPrefix fallback)
            if (hasSecretPaths) {
                String[] rawPaths = PATH_SPLIT.split(secretPathsInput);
                for (String raw : rawPaths) {
                    String path = raw.trim();
                    if (path.isEmpty()) continue;
                    String akeylessPath = path.startsWith("/") ? path : "/" + path;
                    addCredentialVariants(result, akeylessPath, akeylessPath, defaultTags);
                }
            }

            if (!result.isEmpty()) {
                Set<String> ids = new HashSet<>();
                for (StandardCredentials c : result) ids.add(c.getId());
                LOG.log(Level.INFO, "Akeyless Credentials Provider: {0} credential(s), ids={1}", new Object[]{result.size(), ids});
            }
            return result;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading Akeyless credentials", e);
            return Collections.emptyList();
        }
    }

    /** Add one credential for the given path; register short name (last segment) and full path variants. */
    private static void addCredentialVariants(Collection<StandardCredentials> result, String akeylessPath, String description, Map<String, String> defaultTags) {
        Set<String> registered = new HashSet<>();
        String lastSeg = lastPathSegment(akeylessPath);
        if (lastSeg != null && registered.add(lastSeg)) {
            CredentialsFactory.create(lastSeg, akeylessPath, description, defaultTags).ifPresent(result::add);
        }
        String fullNorm = akeylessPath.replaceAll("^/+|/+$", "");
        for (String altId : new String[]{fullNorm, "/" + fullNorm}) {
            if (registered.add(altId)) {
                CredentialsFactory.create(altId, akeylessPath, description, defaultTags).ifPresent(result::add);
            }
        }
        LOG.log(Level.FINE, "Akeyless Credentials Provider: registered IDs for {0}: {1}", new Object[]{akeylessPath, registered});
    }

    /**
     * Credential ID from path: strips leading/trailing slashes and sanitizes to [a-zA-Z0-9/_.-].
     */
    private static String credentialIdFromPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String trimmed = path.replaceAll("^/+|/+$", "");
        if (trimmed.isEmpty()) return null;
        if (!trimmed.matches("[a-zA-Z0-9/_.-]+")) {
            trimmed = trimmed.replaceAll("[^a-zA-Z0-9/_.-]", "_");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Last segment of path (e.g. /CICD/jenkins/test/test3/jenkinsai → jenkinsai) for use as short credential ID. */
    private static String lastPathSegment(String path) {
        if (path == null || path.isEmpty()) return null;
        String trimmed = path.replaceAll("/+$", "").trim();
        int last = trimmed.lastIndexOf('/');
        if (last < 0) return credentialIdFromPath(trimmed);
        String segment = trimmed.substring(last + 1);
        return segment.isEmpty() ? null : credentialIdFromPath(segment);
    }
}
