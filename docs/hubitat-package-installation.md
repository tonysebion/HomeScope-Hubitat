# Install HomeScope on Hubitat

HomeScope is distributed as one Hubitat Package Manager (HPM) package. It installs two apps and one driver while
preserving separate read and publication credentials.

## Before installation

- Back up the Hubitat hub.
- Keep HomeScope credentials, access tokens, and authenticated URLs out of public issues, chat, Git,
  screenshots, and logs.
- Do not expose either HomeScope endpoint through port forwarding or a public cloud relay.
- In HPM settings, set **Install updates automatically when** to **Never**. If you intentionally use HPM's
  **Exclude** mode for other packages, confirm that HomeScope is selected in the exclusion list.
- Review HomeScope release notes before approving each update manually.

## Install the package

1. Install or open **Hubitat Package Manager** on the hub.
2. Select **Install**, then **From a URL**.
3. Enter this manifest URL:

   ```text
   https://raw.githubusercontent.com/tonysebion/HomeScope-Hubitat/main/packageManifest.json
   ```

4. Confirm that HPM lists exactly these required components:

   - HomeScope Read Connector (app)
   - HomeScope Observation Bridge (app)
   - HomeScope Observation Bridge Child (driver)

5. Complete the package installation. This installs code only; it does not select household devices, generate
   HomeScope credentials, or perform live requests.
6. In Hubitat's **Apps** page, select **Add User App**, then install **HomeScope Read Connector**.
7. Select **Add User App** again and install **HomeScope Observation Bridge**.
8. Do not manually create a device from **HomeScope Observation Bridge Child**. The observation bridge creates
   only its own fixed, non-actuating child devices when a projection is explicitly registered.

Stop after both app instances open and save without a compile error. Device selection, OAuth token handling, and
live requests belong to a separate, deliberately limited qualification step.

## Authority boundaries

- **HomeScope Read Connector** exposes only fixed GET routes for owner-selected devices and evidence.
- **HomeScope Observation Bridge** accepts only bounded, registered scalar observations through its separate
  OAuth credential.
- **HomeScope Observation Bridge Child** declares sensor attributes and no commands.
- Never reuse either HomeScope token as the other token, and never substitute a Maker API token.

HPM manages installation and owner-approved code updates. It does not combine the two apps' authority, create
tokens, select devices, configure public access, or grant HomeScope an unattended self-update mechanism.

## Update or remove

Keep **Install updates automatically when** set to **Never**, or keep HomeScope selected when using **Exclude**
mode. Review the release notes before choosing HPM's update action. After an update, confirm that both apps still
save and that ordinary Hubitat automations continue independently.

Before removing a configured HomeScope app, revoke or rotate its credential and determine whether its app-owned
observation child devices must be retained. Removal is an owner action; do not delete a configured app merely to
repair or match package metadata.
