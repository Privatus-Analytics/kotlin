# Changelog

## Unreleased

- Changed: with `queueStub` on, a tag that is off (development, `enabled = false` or no site id) now prints the
  queue stub and nothing else, so `privatus.track(...)` calls in your own JavaScript never fail. Before, it
  printed nothing. With `queueStub` off it still prints nothing. The props block is not called when the tag is
  off, and auto inject still adds nothing.
- Added `PrivatusAutoInject`, a route plugin that turns auto inject on for some routes only.
- Added `example/`, a small Ktor app for the conformance tests.

## 1.0.0 - 2026-10-25

- Initial release.
- `PrivatusAnalytics` Ktor 3 server plugin with settings for site id, script host, modules,
  excluded paths, domains, single-page app mode, manual mode and extra attributes.
- Optional auto inject that adds the tag before `</head>` in HTML responses (off by default).
- `call.privatusTag()` and the kotlinx.html helper `privatusAnalytics(call)` for the head.
- `privatusEvent` and `privatusClick` for kotlinx.html tags, and `privatusEventAttrs`,
  `privatusEventData`, `privatusClickAttr`, `privatusClickData` for other template engines.
- Page props (`props { call -> ... }`), which print the `privatusPageProps` script and `data-props`.
- `queueStub` setting, so `privatus.track` calls made before the tracker loads are kept.
- CSP nonce support through `nonce { call -> ... }`.
- The tag prints only in production by default.
