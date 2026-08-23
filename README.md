# HomeScope for Hubitat

HomeScope uses Hubitat as the central device manager while keeping ordinary Hubitat automations independent
from the HomeScope service. This public repository contains only the Hubitat-side distribution package.

The package installs:

- **HomeScope Read Connector** — owner-scoped, read-only discovery through fixed GET routes.
- **HomeScope Observation Bridge** — separately authenticated publication of registered, scalar advisory
  observations.
- **HomeScope Observation Bridge Child** — a fixed sensor-only child driver with no commands.

The read connector and observation bridge intentionally remain separate apps with separate OAuth credentials.
The package does not include Maker API access, device commands, rule changes, generic proxying, or unattended
self-update code.

The `0.1.3` source candidate adds fail-closed owner profiles to the Read Connector, hides credential details by
default, and supplies explicit Hubitat-compatible app icons. It has passed HomeScope's offline contract and
safety tests. On 2026-08-23, the owner confirmed that the exact candidate Read Connector and Observation Bridge
sources compiled and saved on the live Hubitat hub. The main HPM manifest remains on `0.1.2` while owner-controlled
safe-empty UI verification is completed. Installation, authorization, device selection, and live requests remain
separate owner-controlled steps.

After a future 0.1.3 update, existing Read Connector selections are intentionally not reused. The app starts in
**Safe empty** and requires fresh owner approval. Do not downgrade after saving the new profile because older code
could reinterpret legacy selection settings.

## Install

Follow [the installation guide](docs/hubitat-package-installation.md). Hubitat Package Manager can install the
package from:

```text
https://raw.githubusercontent.com/tonysebion/HomeScope-Hubitat/main/packageManifest.json
```

Keep credentials, access tokens, authenticated URLs, household information, and hub screenshots containing
private data out of public issues and pull requests.

## License

HomeScope's Hubitat distribution is available under the [MIT License](LICENSE).
