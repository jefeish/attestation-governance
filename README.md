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
run on `main`, so there is a check result for the exact commit that was
actually merged.

The build-and-attest workflow runs in either of two ways:

- GitHub starts it when one of the three check workflows finishes on `main`.
- A person starts it with **Run workflow** and chooses a branch, tag, or commit.

The build-and-attest workflow then:

1. Finds the exact commit to build.
2. Waits until all three separate check workflows finish for that commit.
3. Stops if any check is missing or failed.
4. Builds the JAR from that exact commit.
5. Makes build provenance and check-results attestations.

This means a bypass user can merge code with failed PR checks, but that exact
merged commit cannot receive an attested JAR.

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
    H --> I[build-and-attest.yml]
    I --> J{All exact-commit checks pass?}
    J -->|No| K[No attested artifact]
    J -->|Yes| L[Build JAR]
    L --> M[Create attestations]
```

The build and attestation are two **jobs in one workflow**, not two workflows.
They are separate because attestation must happen only after the JAR exists.
The three checks are separate workflows because that matches the normal PR
model and lets each check appear as its own required status check.

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
    Checks->>Release: Workflow completed
    Release->>Release: Verify all three checks for SHA
    Release->>Release: Build only when all pass
    Release->>Release: Attest the JAR
```

The workflow needs special permission to create the attestation:

- `attestations: write` lets it write the attestation.
- `id-token: write` lets GitHub sign the attestation.

The workflow uses the `actions/attest@v4` action. With no extra predicate
settings, the first use makes a build provenance attestation. The second use
makes a small custom attestation containing the repository, commit, and mock
check results. Both attestations refer to the same JAR SHA.

## Check the attestation

After the workflow finishes, download `hello-world.jar` from the workflow run.
Then run this command with the GitHub CLI:

```bash
gh attestation verify hello-world.jar -R OWNER/REPOSITORY
```

Replace `OWNER/REPOSITORY` with the real repository name.

If the command says the attestation is valid, the JAR has a trusted build story.
