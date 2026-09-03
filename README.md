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
- `.github/workflows/build-and-attest.yml` runs mock checks, builds the app,
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

## What the GitHub workflow does

1. GitHub starts a clean computer.
2. It runs three tiny mock checks:
   `mock-sonarqube`, `mock-codeql`, and `mock-test`.
3. Only if all three checks pass, it installs Java and builds the JAR.
4. It saves the JAR for the next job.
5. On a push to a public repository, it makes two signed attestations:
   build provenance and check results.
6. Pull requests still run the checks and build the JAR, but do not publish
   attestations. GitHub does not support persisting attestations for user-owned
   private repositories, so this demo skips that step there.

The checks, build, and attest work are **jobs in one workflow**. They are not
three separate workflow files. The `needs` setting makes the build wait for all
three checks, and makes attestation wait for the checks and the build. If a
check fails, GitHub skips the build and attestation jobs.

The workflow needs special permission to create the attestation:

- `attestations: write` lets it write the attestation.
- `id-token: write` lets GitHub sign the attestation.

The workflow uses the `actions/attest@v4` action. With no extra predicate
settings, the first use makes a build provenance attestation. The second use
makes a small custom attestation containing the repository, commit, workflow
run, and mock check results. Both attestations refer to the same JAR SHA.

## Check the attestation

After the workflow finishes, download `hello-world.jar` from the workflow run.
Then run this command with the GitHub CLI:

```bash
gh attestation verify hello-world.jar -R OWNER/REPOSITORY
```

Replace `OWNER/REPOSITORY` with the real repository name.

If the command says the attestation is valid, the JAR has a trusted build story.
