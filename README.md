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

Version `0.1.11` lets the Read Connector recognize a complete, current HomeScope Observation Bridge Child
snapshot as one canonical projected-observation record. Recognition is deliberately closed: incomplete,
expired, non-actionable, malformed, or unexpected child state remains ordinary read-only attribute evidence
and cannot be treated as an actionable projection. The change adds no route, device command, configuration,
automation, generic proxy, physical-device, credential, event-delivery, or scope authority. Installation,
authorization, device selection, and live requests remain owner-controlled.

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
