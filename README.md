# Mock check attestation reference

This repository is a small Java reference showing one complete flow:

1. A pull request runs four independent checks.
2. The build runs only when every check passes.
3. A merge to `main` builds the JAR, creates GitHub artifact attestations, and
   publishes a durable evidence record as a GitHub Release.

The checks are intentionally mocks. The example demonstrates orchestration,
artifact binding, and record retention without requiring CodeQL, SonarQube, or
another external service.

## What is included

| File | Purpose |
| --- | --- |
| `src/main/java/.../HelloWorld.java` | Minimal Java application used in pull requests. |
| `mock-codeql.yml` | Fails when Java source contains `MOCK_CODEQL_FAILURE`. |
| `mock-sonarqube.yml` | Fails when Java source contains `MOCK_SONAR_FAILURE`. |
| `mock-dependency-review.yml` | Fails when `pom.xml` contains `mock-vulnerable`. |
| `mock-tests.yml` | Runs the real Maven unit test as the mock test gate. |
| `reference-build.yml` | Waits for every gate, builds the JAR, attests it, and archives the record. |

All GitHub Actions are official actions pinned to full commit SHAs.

## Try it in a pull request

Change the greeting in `HelloWorld.java` and update its test. The
`Reference build and attestation archive` workflow runs the four checks in
parallel. Its `build` job has explicit `needs` dependencies and verifies every
reusable workflow returned `pass` before running:

```bash
mvn --batch-mode --no-transfer-progress clean package
```

To demonstrate a blocked build, add one of the mock failure strings documented
above. The corresponding check fails and GitHub skips the dependent build job.

Pull requests build and test but do not publish attestations or Releases. This
avoids giving untrusted pull-request code write or OIDC permissions.

## Durable record after merge

On a push to `main`, the workflow publishes:

- GitHub build provenance for the JAR;
- one custom artifact attestation for each mock check;
- the JAR itself;
- `check-evidence.json` containing all four check records;
- each custom predicate;
- each Sigstore attestation bundle;
- `record-manifest.json`, linking repository, commit, workflow run, artifact
  name, artifact digest, and required controls;
- `SHA256SUMS` covering every archived file.

These files become assets on a uniquely named GitHub Release:

```text
attestation-record-<commit>-<run-id>-<run-attempt>
```

Workflow artifacts and check-run history have retention limits. GitHub Release
assets remain until someone explicitly deletes the Release, so they provide the
long-lived copy in this example. They are not immutable: restrict Release
deletion, repository administration, and tag deletion to trusted maintainers,
and retain audit logs. A production archive with regulatory immutability
requirements should copy the same record to write-once object storage.

The downloaded bundle can be verified independently with a current GitHub CLI:

```bash
gh release download <record-tag> --dir record
(cd record && sha256sum --check SHA256SUMS)
gh attestation verify record/hello-attestations-<commit>.jar \
  --repo jefeish/attestation-governance \
  --bundle record/build-provenance.sigstore.json
```

Custom bundles can be verified by replacing `--bundle` with the desired
`*.sigstore.json` file and adding:

```text
--predicate-type https://github.com/jefeish/attestation-governance/predicates/mock-check/v1
```

## Trust boundary

The reusable mock workflows derive their own result. The build consumes their
job outputs; there is no caller-supplied `result` input. On `main`, the same
reviewed workflow creates the artifact, predicates, attestations, and Release.

Protect `main` with a ruleset requiring:

- pull requests and status checks;
- CODEOWNERS approval for `.github/workflows/`;
- signed commits and resolved review conversations;
- blocked force pushes, branch deletion, and tag deletion;
- tightly restricted repository and Release administration.

`.github/CODEOWNERS` assigns the workflows to the repository owner. The checked
in Dependabot configuration proposes updates for pinned Actions dependencies.

This is a workflow reference, not a claim that the mock checks provide real
security assurance. Replace each mock implementation with an authoritative
service integration while preserving its `result` and evidence output contract.

## Platform requirements

Publishing GitHub artifact attestations for private repositories requires
GitHub Enterprise Cloud. The workflow also needs GitHub Actions, Releases, and a
caller token that grants `contents: write`, `id-token: write`, and
`attestations: write` to the `publish-record` job. Pull-request jobs retain
read-only permissions.
