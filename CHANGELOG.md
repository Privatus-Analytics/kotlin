# Changelog

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
