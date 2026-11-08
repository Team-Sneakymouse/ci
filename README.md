# Shared CI

Reusable GitHub Actions workflows for Team Sneakymouse. Callers own their triggers,
path filters, prerequisite jobs and concurrency policy. The shared jobs check out
the caller's repository and receive only its two Infisical connection secrets.
Both shared workflows run on the organization's Linux x64 runner `alphaarcadeus`.

## Skipping documentation-only pushes

Both workflows start with a short `changes` job. On a `push`, it lists the files
changed between `github.event.before` and the pushed commit. If every file matches
the `skip-paths` input, the build/publish/deploy job is skipped. Defaults:

```yaml
skip-paths: |
  *.md
  docs/*
  LICENSE*
```

Patterns are bash globs matched against repository-root paths, where `*` also
matches `/` (so `*.md` covers Markdown files in any folder). Pass `skip-paths: ''`
to always run. Other events (`workflow_dispatch`, `pull_request`), a branch's first
push, empty commits, and pushes whose previous commit can't be fetched (for example
after a force-push) always run. Renames count as changes to both paths.

The `changes` job writes its decision to the run's summary: on a skip, the list of
documentation files that changed; otherwise, a one-line reason for running.

A skipped run still finishes as `success`. The Mother-Mouse Discord bot reports it
as skipped by finding the gate job, shown on GitHub as `<caller job> / changes`,
and checking that every other job was skipped. If you rename the `changes` job,
coordinate with Mother-Mouse-Discord so the bot keeps matching it.

## Gradle publishing

`.github/workflows/gradle-publish.yml` builds and checks with Temurin Java 25,
then fetches `/Maven` from Infisical and publishes to the Sneakyrp repository.
Projects must also configure their Java/Kotlin toolchains and bytecode targets to 25.
On the shared self-hosted runner, Gradle invocations are serialized with `flock` on
`$GRADLE_USER_HOME/ci-gradle.lock` so concurrent publishes from different repos do
not contend on `caches/journal-1.lock`. The shared Gradle home is kept so jobs reuse
pre-seeded distributions (the runner often cannot reach `services.gradle.org`).

```yaml
permissions:
  contents: read
  id-token: write

jobs:
  publish:
    uses: Team-Sneakymouse/ci/.github/workflows/gradle-publish.yml@main
    secrets:
      INFISICAL_IDENTITY_ID: ${{ secrets.INFISICAL_IDENTITY_ID }}
      INFISICAL_DOMAIN: ${{ secrets.INFISICAL_DOMAIN }}
```

Inputs:

- `project-directory`: directory containing the wrapper and build; defaults to `.`.
- `publish-task`: one Gradle task; defaults to `publishMavenPublicationToSneakyrpRepository`.
- `publish`: defaults to `true`; `false` builds/checks without fetching secrets or publishing.
- `runs-on`: JSON runner name or label array; defaults to
  `["self-hosted", "Linux", "X64", "alphaarcadeus"]`.
- `skip-paths`: see [Skipping documentation-only pushes](#skipping-documentation-only-pushes).

Callers: SneakyBagOfHolding, SneakyConfigurableVoice, SneakyDiagnostics,
SneakyDiscordLink, SneakyEconomy, SneakyGroundItems, SneakyInventoryUtils,
SneakyMisc, SneakyPocketbase, SneakySTTEvents and craftengine-resources.
SneakyEconomy keeps publishing disabled. SneakyPocketbase selects its consumer API
publication. CraftEngine uses `integration` and its existing self-hosted runner.
Diagnostics publishes its API, plugin and Ignite companion together with the same
commit-derived version; its separate API release workflow is removed.

## Docker deployment

`.github/workflows/docker-deploy.yml` fetches `/Registry`, logs in, builds and
pushes an image tagged `latest`, then fetches `/Watchtower` and requests an update.
Required credentials are checked and failed HTTP responses fail the job. The
Watchtower request has a 10-second connection timeout and a 120-second total timeout.

Images are labeled `org.opencontainers.image.revision` (the caller's commit SHA)
and `org.opencontainers.image.source` (the caller's repository URL), and receive
the build argument `GIT_SHA`. A Dockerfile that wants the commit at runtime adds
`ARG GIT_SHA` and `ENV GIT_SHA=$GIT_SHA`; other Dockerfiles ignore it.

```yaml
permissions:
  contents: read
  id-token: write

jobs:
  deploy:
    uses: Team-Sneakymouse/ci/.github/workflows/docker-deploy.yml@main
    with:
      image-name: my-service
    secrets:
      INFISICAL_IDENTITY_ID: ${{ secrets.INFISICAL_IDENTITY_ID }}
      INFISICAL_DOMAIN: ${{ secrets.INFISICAL_DOMAIN }}
```

Inputs:

- `image-name`: required image name beneath `REGISTRY_HOST`.
- `context`: local build context; defaults to `.`.
- `dockerfile`: optional Dockerfile path; defaults to the context's `Dockerfile`.
- `skip-paths`: see [Skipping documentation-only pushes](#skipping-documentation-only-pushes).

Optional secrets:

- `WATCHTOWER_URL`: replaces the URL from Infisical `/Watchtower`, for services
  updated by a different Watchtower. `WATCHTOWER_TOKEN` still comes from Infisical.

Callers: dclock, Kanboard, media, Mother-Mouse-Discord, OverlayV1, shop,
SneakySTTEvents and SneakySpeedTest. STT retains its `stt-server/moonshine`
context and path filters. SpeedTest's tests and optional-deployment configuration
check remain in a prerequisite job; deployment is skipped when unconfigured.

## Rollout

1. Publish these workflows to this repository's `main` before merging caller changes.
2. Keep this repository public so both public and private repositories can call it.
   Its visibility was changed to public on 23 October 2026 for this rollout.
3. Confirm caller Actions policies allow `Team-Sneakymouse/ci`, and keep
   `INFISICAL_IDENTITY_ID` and `INFISICAL_DOMAIN` in each caller's secrets.
4. Verify a Maven publish and Docker deployment in GitHub Actions before merging
   the remaining callers. Check Infisical OIDC trust if it restricts workflow refs.

All Infisical fetches use project `lords-of-minecraft`, environment `prod`, and
the caller's identity. No credentials are stored here. Callers currently follow
`@main`; changes merged here apply to their next run. For controlled upgrades,
replace `@main` with a released commit SHA.

Custom deployment pipelines (Lawn, Reposilite, pocketbase and adventure-webui),
legacy credential migrations and reusable composite actions are outside this change.

See [GitHub's reusable workflow documentation](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows).
