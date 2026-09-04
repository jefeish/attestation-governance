# Attestation Governance Demo

This is a very small Java example.

The app says:

```text
Hello, World!
```

GitHub Actions builds the app and makes a signed note called an **attestation**.
The note helps us check that the JAR file came from this GitHub Actions build.

## What is here?

- `src/main/java/HelloWorld.java` is the Java app.
- `.github/workflows/mock-sonarqube.yml` is the mock SonarQube check.
- `.github/workflows/mock-codeql.yml` is the mock CodeQL check.
- `.github/workflows/mock-test.yml` is the mock test check.
- `.github/workflows/build-and-attest.yml` checks the results, builds the app,
  and attests the JAR.

## Run it on your computer

You need Java 17 or newer.

```bash
javac -d out src/main/java/HelloWorld.java
java -cp out HelloWorld
```

You should see:

```text
Hello, World!
```

## Make the JAR yourself

```bash
mkdir -p build/classes
javac -d build/classes src/main/java/HelloWorld.java
jar --create --file build/hello-world.jar --main-class HelloWorld -C build/classes .
java -jar build/hello-world.jar
```

## What the workflows do

The three mock check workflows run when a PR is opened or updated. They also
run on `main`. When they pass on `main`, each one creates a signed, durable
check-evidence attestation for the exact commit.

The build-and-attest workflow runs in either of two ways:

- GitHub starts it when a PR closes after being merged.
- A person starts it with **Run workflow** and chooses a branch, tag, or commit.

The build-and-attest workflow then:

1. Finds the exact commit to build.
2. Recreates the small evidence files for that commit.
3. Verifies the signed evidence attestations for all three checks.
4. Stops if any evidence is missing or invalid.
5. Builds the JAR from that exact commit.
6. Makes one build provenance attestation for the JAR.

This means a bypass user can merge code with failed PR checks, but that exact
merged commit cannot receive an attested JAR. The later build does not depend
on GitHub's short-lived check-run history.

```mermaid
flowchart TD
    A[Open or update PR] --> B[mock-sonarqube.yml]
    A --> C[mock-codeql.yml]
    A --> D[mock-test.yml]
    B --> E{Ruleset checks pass?}
    C --> E
    D --> E
    E -->|Yes| F[Normal merge]
    E -->|No| G[Normal merge is blocked]
    F --> H[Checks run again on exact main commit]
    H --> I[Each check signs durable evidence]
    I --> J[PR close starts build-and-attest.yml]
    J --> K{All evidence attestations verify?}
    K -->|No| L[No attested artifact]
    K -->|Yes| M[Build JAR]
    M --> N[Create provenance attestation]
```

The build and attestation are two **jobs in one workflow**, not two workflows.
They are separate because attestation must happen only after the JAR exists.
The three checks are separate workflows because that matches the normal PR
model and lets each check appear as its own required status check.

## Why checks run twice

Seeing the three checks run twice is expected.

The first run checks the PR. The second run checks the exact commit that landed
on the target branch. These commits are not always the same:

```text
PR head or test-merge SHA  !=  final target-branch SHA
```

The build must use the second SHA. Otherwise, it could build one commit while
showing check evidence from a different commit.

```mermaid
flowchart LR
    A[PR check SHA] --> B[Review and ruleset]
    B --> C[Merge, normal or bypass]
    C --> D[Final target-branch SHA]
    D --> E[Checks run again]
    E --> F[Durable check evidence]
    F --> G[Build and attest]
```

The second run also handles bypasses:

- If a bypassed merge passes all checks on the final commit, it can receive an
  attested build.
- If a bypassed merge fails any check, it creates no valid evidence and gets no
  attested build.

This is a commit identity problem, not a timing problem. Even if all PR checks
finished before the merge, their result may describe the PR SHA rather than the
final target-branch SHA.

## How merge models change this

The second run is the safest default for normal GitHub merge behavior.

| Merge model | Can the first checks prove the final build? | What it means |
| --- | --- | --- |
| Merge commit | No | The merge creates a new commit. Run checks again. |
| Squash merge | No | Git creates a new squashed commit. Run checks again. |
| Rebase merge | Usually no | The commits can receive new SHAs. Run checks again. |
| Fast-forward only | Sometimes | The PR head SHA can remain unchanged. |
| Merge queue | Sometimes | Checks can run on the exact merge candidate if the queue preserves it. |

The design can be changed to use a merge queue or fast-forward-only policy, but
that is a repository policy choice. This reference workflow does not assume
either policy. It always requires check evidence for the exact commit it will
build.

## Protect the protection

The check workflows and the build gate are part of the security boundary.
Protect them like production code:

- Require review for changes under `.github/workflows/`.
- Do not let normal developers bypass those reviews.
- Prefer a trusted reusable workflow stored in a separate protected repository.
- Keep the required check names and signer workflow identities under administrator
  control.

If a bypass user can change both a check workflow and the build gate, they could
remove the evidence requirement. The attestation proves what the trusted
workflow did, so the workflow itself must be trusted.

```mermaid
sequenceDiagram
    participant PR as Pull request
    participant Checks as Three check workflows
    participant Main as main
    participant Release as Build and attest

    PR->>Checks: Run checks
    Checks-->>PR: Pass or fail
    PR->>Main: Merge (normal or bypass)
    Main->>Checks: Run checks for exact merge SHA
    Checks->>Release: Sign durable evidence for SHA
    Release->>Release: Verify all three evidence attestations
    Release->>Release: Build only when all pass
    Release->>Release: Attest the JAR provenance
```

The workflow needs special permission to create the attestation:

- `attestations: write` lets it write the attestation.
- `id-token: write` lets GitHub sign the attestation.

The check workflows use `actions/attest@v4` to sign small evidence files. The
build workflow verifies those files later, even after GitHub removes old
workflow check records. It then makes one build provenance attestation for the
JAR.

The evidence files are deterministic: they contain only the repository, commit,
check name, and `success`. The later workflow can recreate the exact bytes and
verify their SHA and signed attestation.

## Check the attestation

After the workflow finishes, download `hello-world.jar` from the workflow run.
Then run this command with the GitHub CLI:

```bash
gh attestation verify hello-world.jar -R OWNER/REPOSITORY
```

Replace `OWNER/REPOSITORY` with the real repository name.

If the command says the attestation is valid, the JAR has a trusted build story.
In this reference design, that provenance attestation is only created after the
build workflow verifies all required check evidence.
