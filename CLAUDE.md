# JenkinsSecretsManagerProvider

> **★★★ CSE / Knowable Construction.** This repo operates under **Constructive Substrate Engineering** — canonical specification at [`pleme-io/theory/CONSTRUCTIVE-SUBSTRATE-ENGINEERING.md`](https://github.com/pleme-io/theory/blob/main/CONSTRUCTIVE-SUBSTRATE-ENGINEERING.md). The Compounding Directive (operational rules: solve once, load-bearing fixes only, idiom-first, models stay current, direction beats velocity) is in the org-level pleme-io/CLAUDE.md ★★★ section. Read both before non-trivial changes.

A Jenkins plugin that provides **CredentialsProvider** integration with [Akeyless](https://www.akeyless.io/), so secrets stored in Akeyless appear as Jenkins credentials and can be used in pipelines with `credentials('id')`
