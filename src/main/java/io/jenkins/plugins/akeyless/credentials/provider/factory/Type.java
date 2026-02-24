package io.jenkins.plugins.akeyless.credentials.provider.factory;

/**
 * Jenkins credential type values for tag jenkins:credentials:type.
 */
public abstract class Type {
    public static final String STRING = "string";
    public static final String USERNAME_PASSWORD = "usernamePassword";
    public static final String SSH_USER_PRIVATE_KEY = "sshUserPrivateKey";
    public static final String CERTIFICATE = "certificate";
    public static final String FILE = "file";

    private Type() {}
}
