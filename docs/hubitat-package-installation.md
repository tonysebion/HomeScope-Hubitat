# Install and Release HomeScope for Hubitat

HomeScope is distributed as one Hubitat Package Manager (HPM) package. The package installs two apps and one
driver while preserving separate read and publication credentials.

The anonymous public distribution is `tonysebion/HomeScope-Hubitat`. It contains only the reviewed Hubitat
sources, manifest, documentation, and MIT license. Never publish the private HomeScope repository or put a
GitHub token, Hubitat credential, authenticated URL, household record, or local path in the distribution.

## Before installation

- Back up the Hubitat hub.
- Keep HomeScope credentials, access tokens, and authenticated URLs out of chat, Git, screenshots, and logs.
- Do not expose either HomeScope endpoint through port forwarding or a public cloud relay.
- In HPM settings, set **Install updates automatically when** to **Never**. If you intentionally use HPM's
  **Exclude** mode for other packages, confirm that HomeScope is selected in the exclusion list. Check for
  updates and approve each HomeScope update manually after reviewing its release notes.

## Publish an update

1. Choose the next package version and update the private manifest template and release notes.
2. Copy only these reviewed files into `tonysebion/HomeScope-Hubitat`:

   - `apps/HomeScopeReadConnector.groovy`
   - `apps/HomeScopeObservationBridge.groovy`
   - `drivers/HomeScopeObservationBridgeChild.groovy`
   - `docs/hubitat-package-installation.md`
   - the completed package manifest and `LICENSE`

3. Confirm that every component and license URL names the public repository and the new immutable version tag.
4. Commit the reviewed Groovy files, documentation, manifest candidate, and license. Create the immutable tag
   named by every component URL at that exact source commit. Never move or reuse a release tag.
5. From an unauthenticated network client, fetch every tagged component and the license. Byte-compare the
   downloaded Groovy sources with the reviewed release sources.
6. Only after step 5 succeeds, publish the completed candidate as `packageManifest.json` on the distribution
   repository's `main` branch. Fetch that manifest anonymously and verify its component URLs once more.

Do not publish the complete private HomeScope repository. A custom HPM repository listing is unnecessary for
installation by manifest URL and may be added later if public catalog discovery is wanted.

## Install the released package

1. Install or open **Hubitat Package Manager** on the hub.
2. Select **Install**, then **From a URL**.
3. Enter this exact raw manifest URL—not the GitHub repository or `blob/main` webpage URL:

   ```text
   https://raw.githubusercontent.com/tonysebion/HomeScope-Hubitat/main/packageManifest.json
   ```
4. Confirm that HPM lists exactly these required components:

   - HomeScope Read Connector (app)
   - HomeScope Observation Bridge (app)
   - HomeScope Observation Bridge Child (driver)

5. Complete the package installation. This installs code only; it does not select household devices or create
   HomeScope credentials.
6. In Hubitat's **Apps** page, select **Add User App**, then install **HomeScope Read Connector**.
7. Select **Add User App** again and install **HomeScope Observation Bridge**.
8. Do not manually create a device from **HomeScope Observation Bridge Child**. The observation bridge creates
   only its own fixed, non-actuating child devices when a projection is explicitly registered.

After installing or upgrading, open **HomeScope Read Connector** and verify it shows `Safe empty`, no effective
device/evidence/event scope, and hidden credential details. Releases at or before 0.1.2 used legacy selection
settings; 0.1.3 intentionally ignores and clears that authority, so the owner must choose a new profile and scope.
Do not downgrade after saving the new profile because legacy code could reinterpret stale legacy settings.

Stop after both app instances open and save without a compile error. Device selection, OAuth token generation,
and live requests belong to the separately approved live-qualification checkpoint.

## Authority boundaries

- **HomeScope Read Connector** exposes only its fixed GET routes for owner-selected devices and evidence.
- **HomeScope Observation Bridge** accepts only bounded, registered scalar observations through its separate
  OAuth credential.
- **HomeScope Observation Bridge Child** declares sensor attributes and no commands.
- Never reuse either HomeScope token as the other token, and never substitute a Maker API token.

HPM manages installation and owner-approved code updates. It does not combine the two apps' authority, create
tokens, select devices, configure public access, or grant HomeScope an unattended self-update mechanism.

## Update or remove

Review HomeScope release notes before choosing HPM's update action. Keep **Install updates automatically when**
set to **Never**, or keep HomeScope selected when using **Exclude** mode.
After an update, confirm that both apps still save and that ordinary Hubitat automations continue independently.
For version 0.1.11 or later, a selected HomeScope Observation Bridge Child is returned as one canonical
projected-observation record only when its full snapshot is current, internally consistent, and actionable.
Incomplete, expired, non-actionable, malformed, or unexpected child state remains ordinary read-only attribute
evidence. This recognition does not add a command or configuration path.

Before removing a configured HomeScope app, revoke or rotate its credential and determine whether its app-owned
observation child devices must be retained. Removal is an owner action; do not delete a configured app merely to
repair or match package metadata.
