# Shared CI

Reusable GitHub Actions workflows for Team Sneakymouse. Callers own their triggers,
path filters, prerequisite jobs and concurrency policy. The shared jobs check out
the caller's repository and receive only its two Infisical connection secrets.
Both shared workflows run on the organization's Linux x64 runner `alphaarcadeus`.

## Gradle publishing

`.github/workflows/gradle-publish.yml` builds and checks with Temurin Java 25,
then fetches `/Maven` from Infisical and publishes to the Sneakyrp repository.
Projects must also configure their Java/Kotlin toolchains and bytecode targets to 25.

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
