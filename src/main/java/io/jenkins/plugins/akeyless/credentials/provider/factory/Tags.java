package io.jenkins.plugins.akeyless.credentials.provider.factory;

/**
 * Tag keys on Akeyless items used to map to Jenkins credential types (same convention as AWS Secrets Manager Credentials Provider).
 * @see <a href="https://plugins.jenkins.io/aws-secrets-manager-credentials-provider/">AWS Secrets Manager Credentials Provider</a>
 */
public abstract class Tags {
    private static final String NAMESPACE = "jenkins:credentials:";

    public static final String FILENAME = NAMESPACE + "filename";
    public static final String TYPE = NAMESPACE + "type";
    public static final String USERNAME = NAMESPACE + "username";

    private Tags() {}
}
