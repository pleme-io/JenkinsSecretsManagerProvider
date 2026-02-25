# Akeyless Credentials Provider

A Jenkins plugin that provides **CredentialsProvider** integration with [Akeyless](https://www.akeyless.io/), so secrets stored in Akeyless appear as Jenkins credentials and can be used in pipelines with `credentials('id')`

## Features

- **Read-only** view of Akeyless secrets as Jenkins credentials
- **CredentialsProvider** API support: credentials appear in the global store and in pipeline `credentials()` / `withCredentials`
- **User-provided paths**: you configure the list of Akeyless secret paths; only **get-secret-value** is used (via SDK). No listing.
- **Standalone** — authenticates with your chosen method (API Key, Kubernetes, GCP, etc.) stored in the plugin config. No external plugin dependencies.

## Requirements

- Jenkins 2.479.3 or later

## Installation

1. Install **Akeyless Credentials Provider** (this plugin).
2. Configure: **Manage Jenkins → Configure System** → find **Akeyless Credentials Provider** and set:
   - **Akeyless URL**: Your Akeyless gateway API URL (used as-is; no suffix is added). If behind a load balancer, use the full URL including path (e.g. `https://gateway.example.com/api/v2`).
   - **Access ID**: Your Akeyless access ID (e.g. `p-abc123`), if required by your auth method.
   - **Authentication Method**: e.g. API Key, Kubernetes, GCP, etc.
   - **Folder path** (e.g. `/CICD/jenkins/test/test3`) and **Secret names** (one per line, e.g. `jenkinsai`). In the job use `credentials('jenkinsai')`. Full path = folder + name; no listing.
   - Or **Secret paths**: full path to each secret (one per line). No listing.
   Click **Save**.

**When does Jenkins call Akeyless?** Jenkins contacts Akeyless when resolving credentials: e.g. when you open **Manage Jenkins → Credentials**, or when a pipeline runs and uses `credentials('id')`. Only **get-secret-value** is used (via the Akeyless SDK); no listing.

## Usage

**Folder path + Secret names:** Set **Folder path** to e.g. `/CICD/jenkins/test/test3` and **Secret names** to one name per line (e.g. `jenkinsai`). In the job use `credentials('jenkinsai')`. The plugin calls get-secret-value for `/CICD/jenkins/test/test3/jenkinsai`; no listing.

**Secret paths (optional):** Or list full paths (one per line); then you can use the short name or full path as `credentialsId`.

Confirm the IDs in **Manage Jenkins → Credentials** or in the credential picker when editing a pipeline.

### Pipeline examples

**Declarative:**

```groovy
pipeline {
  agent any
  environment {
    API_KEY = credentials('my-api-key')
  }
  stages {
    stage('Build') {
      steps {
        sh 'echo Building...'
      }
    }
  }
}
```

**Scripted:**

```groovy
node {
  withCredentials([string(credentialsId: 'my-api-key', variable: 'API_KEY')]) {
    sh 'echo $API_KEY'
  }
}
```

## Configuration

- **Akeyless URL**: Gateway API URL (used as-is; no suffix added). Example: `https://my-gateway.akeyless.io` or `https://gateway.example.com/api/v2`.
- **Access ID**: Your Akeyless access ID (e.g. `p-abc123`), if required by your auth method.
- **Authentication Method**: API Key, Kubernetes, GCP, Azure AD, etc.
- **Secret paths**: One Akeyless secret path per line (e.g. `/CICD/jenkins/apikey`). Only get-secret-value is used.

## Configuration as Code (CasC)

Example:

```yaml
unclassified:
  akeylessCredentialsProviderConfig:
    akeylessUrl: "https://my-gateway.akeyless.io"
    accessId: "p-abc123"
    secretPaths: |
      /CICD/jenkins/apikey
      /CICD/jenkins/db-password
```

## Building

```bash
mvn clean package
```

The `.hpi` file is in `target/akeyless-credentials-provider-*.hpi`.

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
