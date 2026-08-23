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

Version `0.1.4` keeps the fail-closed owner profiles, hidden credential details, and explicit Hubitat-compatible
app icons introduced in `0.1.3`. It also replaces the Read Connector's snapshot UUID generation with bounded
Hubitat-supported clock and app-state primitives. The change adds no device command, configuration,
event-delivery, or scope authority. Installation, authorization, device selection, and live requests remain
separate owner-controlled steps.

After updating from a release before 0.1.3, existing Read Connector selections are intentionally not reused. The app starts in
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
