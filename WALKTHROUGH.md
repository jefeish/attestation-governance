# Walkthrough: compliance and provenance attestations

See how one project creates **multiple signed attestations for the same JAR**:

- **Provenance** says which source commit and workflow produced the JAR.
- **Compliance** says which required checks passed for that commit.

Then try two bypass scenarios to see how the workflow handles checks that are
still running or have failed.

> Use a disposable **public fork**. The mock checks create signed evidence only
> for successful pushes to `main`. These steps temporarily change workflows
> and bypass branch rules; do not use a production repository.

## Set up the demo

1. **Fork this repository and enable GitHub Actions.**  
   This gives you a safe place to run the demo; public visibility is needed for
   the check workflows to create attestations.
2. **Add a ruleset for `main` requiring Mock SonarQube, Mock CodeQL, and Mock
   test.** Grant bypass permission to your test account.  
   This models the protected-branch policy and lets you exercise bypass.

## Follow a successful build

1. **Open a PR with a small change** to `src/main/java/HelloWorld.java`.  
   This starts the three required checks against the PR.
2. **Wait for all three checks to pass, then merge normally.**  
   This demonstrates the standard protected-branch path.
3. **In Actions, watch the checks run again on `main` and open the
   “Build and attest” run.**  
   The merge can create a new commit SHA, so the workflow requires evidence
   for the exact commit it will build—not just the earlier PR checks.
4. **Wait for the gate to finish and open the published release.**  
   The gate verifies signed evidence for all three checks before building.
   The release contains the JAR, its provenance, and its compliance record.

## Try the bypass scenarios

### Bypass while a required check is still running

Mock test already has a two-minute delay so you have time to merge while it is
running. No workflow edits are needed for this scenario.

1. **Open a PR with a small change and bypass the ruleset while Mock test is
   running.**  
   This demonstrates that merging early does not count an unfinished PR check
   as compliance evidence.
2. **Watch the main-branch checks and build gate.**  
   The gate waits for signed results on the merged commit. If all checks pass,
   the artifact can be attested; if evidence is not ready within five minutes,
   the gate stops without a release.

### Bypass after a required check fails

1. **Make Mock test fail** in `.github/workflows/mock-test.yml` by changing its
   command to `run: exit 1`.
2. **Open a PR with the change, wait for Mock test to fail, then bypass the
   ruleset to merge.**  
   This models a merge despite a failed required check.
3. **Check the main-branch run and build gate.**  
   The failed check creates no signed success evidence for the merged commit,
   so the gate fails and does not publish a JAR or release.

## Verify the attestations

Download `hello-world.jar` from a successful GitHub Release. Replace
`OWNER/REPOSITORY` with your fork's name.

1. **Verify provenance** to confirm the JAR has a valid build attestation:

   ```bash
   gh attestation verify hello-world.jar -R OWNER/REPOSITORY
   ```

2. **Inspect compliance** to see the checks recorded for the artifact:

   ```bash
   gh attestation verify hello-world.jar \
     -R OWNER/REPOSITORY \
     --predicate-type https://example.com/attestation/compliance/v1 \
     --format json \
     --jq '.[].verificationResult.statement.predicate'
   ```

   The result should list Mock SonarQube, Mock CodeQL, and Mock test as
   successful and identify the commit used for the build.

## Restore the demo

If you ran the failed-check scenario, restore the Mock test command to pass
and confirm the checks pass on `main`. Keep the built-in delay for the
in-progress bypass walkthrough. The README contains further detail about the
workflows, attestations, and design limitations.
