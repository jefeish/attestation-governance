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
- `.github/workflows/build-and-attest.yml` builds the app and attests the JAR.

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
2. It installs Java.
3. It compiles `HelloWorld.java`.
4. It puts the app into `hello-world.jar`.
5. It saves the JAR as a workflow artifact.
6. On a push to a public repository, it creates a build attestation for the JAR.
   Pull requests still build and test the app, but do not publish an attestation.
   GitHub does not support persisting attestations for user-owned private
   repositories, so this demo skips that step there.

The workflow needs special permission to create the attestation:

- `attestations: write` lets it write the attestation.
- `id-token: write` lets GitHub sign the attestation.
- `artifact-metadata: write` lets GitHub save artifact metadata.

The workflow uses the `actions/attest@v4` action. With no extra predicate
settings, this action makes a build provenance attestation.

## Check the attestation

After the workflow finishes, download `hello-world.jar` from the workflow run.
Then run this command with the GitHub CLI:

```bash
gh attestation verify hello-world.jar -R OWNER/REPOSITORY
```

Replace `OWNER/REPOSITORY` with the real repository name.

If the command says the attestation is valid, the JAR has a trusted build story.
