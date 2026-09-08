# Providers in Models Meter 1.0.1

Connections are independent. Switching the dashboard or a widget does not replace another provider's credentials. Disconnecting a provider removes its encrypted credential, usage/model cache and provider alerts. The first model check after connecting is silent.

| Provider | Subscription usage | Model catalog | Connection |
| --- | --- | --- | --- |
| ChatGPT / Codex | Account allowance windows, including monthly free-tier quotas; reset credits when supplied | Account-specific Codex model picker | Existing ChatGPT OAuth flow |
| Anthropic / Claude | Claude five-hour and seven-day utilization when supplied | Public Anthropic catalog from models.dev; not account entitlement | Claude sign-in, `sessionKey` cookie, or Claude Code OAuth access token |
| Cursor | Individual plan percentage over the returned monthly billing cycle | Cloud Agents models, with a separate optional user API key | Cursor sign-in or `WorkosCursorSessionToken` cookie |
| OpenCode Go | Rolling, weekly and monthly percentages when supplied | Public Go models endpoint | OpenCode Go workspace API key |

An Anthropic API key measures API access and does not provide a personal Claude subscription's quotas. A Cursor organization Admin API token is not interchangeable with its subscription session cookie. Accounts with multiple Claude organizations currently require the Claude Code OAuth token instead of the web-session route.

## Login on a phone

Settings → Providers offers the provider's own login page for Claude and Cursor. The app reads only the expected session cookie from the provider's domain, validates a usage response and stores the credential in Android Keystore / Apple Keychain. It does not intercept passwords and does not install a JavaScript bridge. iOS uses a nonpersistent WebKit data store. Android clears the bounded WebView login session before and after login; the normal external browser is unaffected.

Some SSO providers reject embedded browsers. Manual token entry remains available in the same screen. OpenCode Go uses the API key from the user's OpenCode workspace. Cursor's optional Cloud Agents API key controls catalog access separately from subscription usage.

No credential is sent to a model catalog host that does not need it. Redirects on credential-bearing API requests are rejected. Tokens are not sent through the Wear Data Layer or widget storage. Anthropic, Cursor and OpenCode Go credentials are not included in settings exports. Android retains the original, separately selected ChatGPT authentication-export feature and its explicit warning.

## Alerts and refresh

The global notification setting controls new-model, low-quota and reset reminders; the new-model toggle can disable model alerts separately. New providers support low-quota crossings, detected refills, and reminders for the reset timestamps supplied by the provider. Background work and reminders follow OS scheduling limits; a reset reminder asks the user to refresh and does not claim a fresh server response. Android's additional-provider reset reminders are inexact alarms; they do not request exact-alarm access. Wear receives the phone's regular mirrored alerts when enabled in the companion settings.

Missing limits are absent rather than 100% available. Percentages such as `0.6` mean 0.6 percent, not 60 percent. The UI rounds to whole percentages consistently with existing Codex meters. Catalog errors do not discard successful quota data. An old successful catalog retains its check timestamp until another successful response arrives.

The original ChatGPT usage-history, reset-credit redemption and detailed pace features continue to use the ChatGPT account. Other providers have their own usage dashboard and widgets; they do not expose ChatGPT's credit actions.

## Protocol references

Reviewed for this implementation:

- [Cursor Cloud Agents API endpoints](https://cursor.com/docs/cloud-agent/api/endpoints): user API model catalog.
- [Cursor API overview](https://cursor.com/docs/api): distinction from organization APIs.
- [OpenCode Go documentation](https://opencode.ai/docs/go/) and [official Go usage endpoint change](https://github.com/anomalyco/opencode/pull/16513): `GET /zen/go/v1/usage` with rolling/weekly/monthly usage percentages and reset offsets.
- [OpenCode Go model catalog](https://opencode.ai/zen/go/v1/models): public list; the app uses model IDs when names are absent.
- [models.dev](https://models.dev) and [its catalog](https://models.dev/api.json): public Anthropic model names and release ordering.
- [CodexBar provider integrations](https://github.com/steipete/CodexBar): independently maintained reference for Claude subscription usage (`/api/organizations/{id}/usage` or `/api/oauth/usage`) and Cursor's `/api/usage-summary` session route. These account routes are not guaranteed public third-party API contracts.

Live provider-account verification requires the user's own login. Contract fixtures exercise real response shapes, absent fields, billing periods, reset offsets, malformed responses, and model discovery; they do not replace testing with a real subscription.
