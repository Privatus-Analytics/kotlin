# Privatus Analytics for Ktor

**Simple, private website analytics for your Ktor app.** No cookies, honest
numbers, and a dashboard you understand in seconds.

## Why Privatus Analytics

**Google Analytics is slow, confusing and hungry for data.** It sends what your
visitors do to Google. In many countries it means showing a cookie pop-up (the
box that asks visitors to accept cookies), and many people click "reject", so
you never see them. Bots pad the numbers that are left.

**Privatus Analytics fixes that.** It counts visits to your Ktor app without
cookies and never stores IP addresses. Bots are filtered out before they are
counted. Your data is never sold or shared.

What you get:

- **A dashboard you understand in seconds.** Visitors, top pages and where
  people came from, all on one screen.
- **No cookie pop-up needed for our analytics.** We set no cookies and store no
  personal data. Laws differ by country, so check your own legal advice.
- **Honest numbers.** Known bots and fake traffic are left out on every plan.
- **A fast app.** The tracking script is about 3 KB, tiny next to most tracking
  tags.
- **Data you own.** We never sell it or show you ads, and you can pull it out
  any time through the API.

**Start free.** The Free plan has 10,000 events a month and 3 sites, with no
card. Every new account also gets 14 days of Pro free, with no card. [Create
your account](https://privatusanalytics.com).

## What this package does

- Prints the Privatus Analytics tracking script in your `<head>` with one call,
  or lets the plugin put it there for you.
- Only prints the browser script. It never sends events from your server and
  sets no cookies.

You need a Privatus Analytics account to see reports, and there is a free plan.
Sign up at https://privatusanalytics.com.

## Requirements

* Java 17 or later.
* Ktor 3.x (server).
* Kotlin 2.x.
* A Privatus Analytics site id (it starts with `pa_`). You find it under
  Site settings, Tracking.

## Install

Add the library to `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.privatusanalytics:privatus-analytics-ktor:1.0.0")
}
```

For the full setup guide and every tracker option, see the [Privatus
Analytics docs](https://privatusanalytics.com/docs).

Set your site id with an environment variable:

```sh
PRIVATUS_SITE_ID=pa_YOURSITEID
```

or when you install the plugin:

```kotlin
import com.privatusanalytics.ktor.PrivatusAnalytics

fun Application.module() {
    install(PrivatusAnalytics) {
        siteId = "pa_YOURSITEID"
    }
}
```

## Add the tag

With [kotlinx.html](https://ktor.io/docs/server-html-dsl.html)
(`ktor-server-html-builder`), put `privatusAnalytics(call)` in the head:

```kotlin
import com.privatusanalytics.ktor.privatusAnalytics

get("/") {
    call.respondHtml {
        head {
            title("My app")
            privatusAnalytics(call)
        }
        body { h1 { +"Hello" } }
    }
}
```

In production it prints:

```html
<script defer src="https://privatusstats.com/js/pa.js" data-site="pa_YOURSITEID"></script>
```

The tag prints nothing when there is no site id, and nothing outside
production (see below).

### Freemarker, Thymeleaf, Pebble and other templates

`call.privatusTag()` gives you the same tag as a plain string. Pass it to your
template and print it without escaping:

```kotlin
call.respond(FreeMarkerContent("index.ftl", mapOf("privatusTag" to call.privatusTag())))
```

| Engine | In the `<head>` |
|---|---|
| Freemarker | `${privatusTag?no_esc}` |
| Thymeleaf | `<th:block th:utext="${privatusTag}"></th:block>` |
| Pebble | `{{ privatusTag | raw }}` |
| Mustache | `{{{privatusTag}}}` |

### Or let the plugin add it

If you would rather not touch your pages, switch on auto inject:

```kotlin
install(PrivatusAnalytics) {
    siteId = "pa_YOURSITEID"
    autoInject = true
}
```

It adds the tag just before `</head>` in `text/html` responses with status
200. It skips Ajax requests (those with `X-Requested-With: XMLHttpRequest`),
pages without a `<head>`, and pages that already have the tag. It runs before
Ktor's `Compression` plugin, so compressed responses work, and it sets the
right `Content-Length`. Responses that already have a `Content-Encoding`
header are left alone.

To add it to some routes only, leave `autoInject` off and install
`PrivatusAutoInject` on those routes:

```kotlin
routing {
    route("/blog") {
        install(PrivatusAutoInject)
        get { call.respondText(page, ContentType.Text.Html) }
    }
}
```

## Configuration

Every setting is optional except the site id.

```kotlin
install(PrivatusAnalytics) {
    siteId = "pa_YOURSITEID"                  // or the PRIVATUS_SITE_ID environment variable
    scriptHost = "https://privatusstats.com"  // or PRIVATUS_SCRIPT_HOST
    modules = listOf("engage", "auto", "vitals", "clicks", "ask")
    excludedPaths = listOf("/admin/*", "/preview/*")
    domains = listOf("example.com", "www.example.com")
    spa = "auto"                              // auto, history, hash or off
    manual = false                            // true turns off the automatic pageview
    autoInject = false
    enabledEnvironments = listOf("production")
    extraAttributes = mapOf("data-mask" to "/orders/*->/orders/:id")
    props { call -> mapOf("plan" to call.principal<UserSession>()?.plan) }
    nonce { call -> call.attributes[CspNonceKey] }
    queueStub = false
}
```

| Setting | Default | What it does |
|---|---|---|
| `siteId` | `PRIVATUS_SITE_ID` | The `data-site` attribute. Nothing prints without it |
| `scriptHost` | `https://privatusstats.com` | Where `/js/pa.js` is served. Change it only if you host your own copy |
| `modules` | tracker default (`engage`) | The `data-modules` attribute. Setting it replaces the default, so keep `engage` for time on page and scroll depth. `emptyList()` loads none |
| `excludedPaths` | none | The `data-exclude` attribute. Path patterns the tracker never counts |
| `domains` | all hosts | The `data-domains` attribute. Count only these hostnames |
| `spa` | `auto` | The `data-spa` attribute (SPA means single-page app) |
| `manual` | `false` | The `data-manual` attribute |
| `enabled` | `null` | `true` or `false` forces the tag on or off everywhere |
| `enabledEnvironments` | `["production"]` | Values of `ktor.deployment.environment` that get the tag |
| `autoInject` | `false` | Adds the tag to the head of HTML responses for you |
| `extraAttributes` | none | Any other `data-*` attribute. A `true` value prints the bare name |
| `props { call -> }` | none | Page props sent with every pageview and event. See below |
| `nonce { call -> }` | none | Your Content Security Policy nonce, added to every script |
| `queueStub` | `false` | Prints a small script first, so `privatus.track` calls made before the tracker loads are kept and sent later. It still prints when the tag is off, so those calls never fail |

Each tracker attribute is explained in the [script attributes
reference](https://privatusanalytics.com/docs/tracker/attributes), and the
modules in [Modules](https://privatusanalytics.com/docs/tracker/modules).

You can also change a setting for one page:

```kotlin
privatusAnalytics(call) { modules = listOf("engage", "auto"); excludedPaths = listOf("/preview/*") }
call.privatusTag { queueStub = true }
```

### Content Security Policy

A Content Security Policy (CSP) is a browser rule that blocks scripts you did
not allow. Many apps allow their own inline scripts with a random "nonce" per
request. Ktor has no built-in nonce, so tell the plugin where yours is with
`nonce { call -> ... }`, or pass one for a single tag:
`call.privatusTag { nonce = myNonce }`.

### Development and test

The tag is on when `ktor.deployment.environment` is in `enabledEnvironments`
(by default only `production`). If you do not set that property, the tag is on
when Ktor's development mode is off. So your own clicking around while you
build never reaches your reports.

```hocon
# application.conf
ktor.deployment.environment = ${?KTOR_ENV}
```

To see the tag while you build, set `enabled = true`. The tracker also ignores
`localhost` unless you pass `data-allow-local` or `data-debug` (see [Debug
mode](https://privatusanalytics.com/docs/tracker/debug)):

```kotlin
extraAttributes = mapOf("data-debug" to "true")
```

### Leaving people out

Use `excludedPaths` (the `data-exclude` attribute) for admin areas. To leave
out signed-in staff, skip the tag for them:

```kotlin
head {
    if (call.principal<UserSession>()?.admin != true) privatusAnalytics(call)
}
```

## Product analytics: see which features people use

Privatus Analytics can show which buttons, forms and features people really use. You
add a few attributes to your HTML. The tracker reads them in the browser and
sends the events. Your server still sends nothing.

### Name events well

Use a short "Noun verb" name, like `Report exported` or `Invite sent`. Put the
details in props, not in the name. So write `Report exported` with
`"format" to "csv"`, not `Report exported as CSV`. Then all exports add up in
one place, and you can split them by format.

### Events on buttons, links and forms

With kotlinx.html, call `privatusEvent` first inside the tag:

```kotlin
button {
    privatusEvent("Report exported", "format" to "csv", "source" to "toolbar")
    +"Export"
}
```

It prints:

```html
<button data-privatus-event="Report exported" data-privatus-prop-format="csv" data-privatus-prop-source="toolbar">Export</button>
```

On a form, the event is sent when the form is submitted:

```kotlin
form(action = "/newsletter") {
    privatusEvent("Newsletter joined", "list" to "weekly")
    // fields
}
```

For other template engines, `privatusEventAttrs` returns the attributes as a
string. Print it without escaping inside the tag:

```kotlin
val exportAttrs = privatusEventAttrs("Report exported", "format" to "csv")
```

```html
<!-- Freemarker -->
<button ${exportAttrs?no_esc}>Export</button>
<!-- Pebble -->
<button {{ exportAttrs | raw }}>Export</button>
```

Thymeleaf cannot print raw attributes, so use `privatusEventData`, which
returns a map of attribute names and values, and set them with `th:attr`.

The rules:

* The name is trimmed and cut to 120 characters. An empty name prints nothing.
* Prop keys are made lowercase. Anything that is not a letter, a digit or `_`
  becomes `_`, so `"Report Type"` becomes `report_type`. Keys are cut to 64
  characters.
* At most 30 props. Values become text: `true`, `false`, numbers as written,
  lists joined with commas. `null` values are left out. Values are cut to 500
  characters.
* Everything is escaped for HTML.
* The attributes print in every environment, even when the tag is off. They do
  nothing without the tracker, so your pages look the same in development and
  production.

### Click maps

A click map shows where people click on a page. Give the important parts a
name so they stay easy to find, even when the page changes:

```kotlin
a(href = "/pricing") {
    privatusClick("pricing.cta")
    +"See plans"
}
```

For other templates: `privatusClickAttr("pricing.cta")` gives
`data-privatus-click="pricing.cta"`, and `privatusClickData` gives a map.

A name may use letters, digits and `_ . : -`, up to 100 characters. Anything
else prints nothing, and the library writes a warning to your log.

### Page props: plan, role and other groups

Page props are sent with every pageview and event on the page. Use them to
split your reports by plan, role, app version, feature flag or account tier.

Set them for every page with a block. It runs on each request and gets the
call:

```kotlin
install(PrivatusAnalytics) {
    props { call ->
        val session = call.sessions.get<UserSession>()
        mapOf("plan" to session?.plan, "role" to session?.role, "app_version" to "2.4")
    }
}
```

A fixed map works too: `props("app_version" to "2.4")`. You can also set or
change props for one page. They are merged over the configured ones, and
`null` removes one:

```kotlin
privatusAnalytics(call) { props = mapOf("section" to "billing", "role" to null) }
```

The tag then prints a small script before the tracker, with your nonce when
you have one, and points the tracker at it:

```html
<script nonce="...">window.privatusPageProps=function(){return {"plan":"pro","role":"admin"}}</script>
<script defer src="https://privatusstats.com/js/pa.js" data-site="pa_YOURSITEID" data-props="privatusPageProps"></script>
```

Keys follow the same rules as event props. Numbers and true or false stay as
they are, and lists stay lists (up to 30 items). The props are escaped so they
are safe inside a script. Auto inject uses the same setting and calls your
block with the same call. The block only runs when the tag prints.

**Never put names, emails, user ids or anything that points to one person in
props.** Privatus Analytics counts visitors without any id. Put groups (like the plan or
the role), not people. The server also removes emails and long ids it finds.

### Track from your own JavaScript

For things that are not a click or a submit, call the tracker from your
JavaScript, as the [JavaScript API
docs](https://privatusanalytics.com/docs/tracker/javascript-api) explain:

```js
privatus.track("Feature used", { feature: "export" })
```

If your code may run before the tracker has loaded, switch on the queue stub.
It prints a tiny script before the tracker that keeps your calls in a list and
sends them once the tracker is ready:

```kotlin
install(PrivatusAnalytics) { queueStub = true }
```

or for one page: `privatusAnalytics(call) { queueStub = true }`. The stub
prints even where the tag is off (in development, with `enabled = false`, or
with no site id). Then only the stub prints: no tracker and no page props. So
you can always call `privatus.track(...)`. In development the calls just wait
in the queue and are never sent. Auto inject adds nothing when the tag is off.

### See the results

* [Funnels](https://privatusanalytics.com/docs/features/funnels): how many
  people go from one step to the next.
* [Click maps](https://privatusanalytics.com/docs/features/click-maps): where
  people click.
* [Goals](https://privatusanalytics.com/docs/features/goals): turn an event
  into a goal to count conversions.
* [Events](https://privatusanalytics.com/docs/events): every custom event and
  its props.

## Privacy

The script sets no cookies and stores nothing in the browser. The event, click
and props helpers only print HTML attributes and a small script. The library
never reads IP addresses or browser details, and never sends anything from
your server. Keep people out of your props and event props: no names, emails
or user ids. See the [privacy
documentation](https://privatusanalytics.com/docs/privacy) for what is
collected.

If you have a privacy policy, it should say which tools handle your visitors' data. You can paste this:

> This website uses [Privatus Analytics](https://privatusanalytics.com/privacy-analytics) to count visits. It
> does not use cookies and does not follow you from one website to another. Your IP address is used for a moment
> to count visits and is never stored.

Change it to fit your site. The [privacy policy generator](https://privatusanalytics.com/tools/privacy-policy-generator)
makes a longer version with your site's name in it.

## Development

The tests run in Docker so your local Java and Gradle do not matter:

```sh
./run-docker.sh
```

It runs the tests (with `ktor-server-test-host` and `kotlin.test`), ktlint
(a Kotlin style checker) and the package build.

## Contributing

Bug reports and pull requests are welcome at
https://github.com/Privatus-Analytics/kotlin.

## License

MIT. See [LICENSE](LICENSE). Privatus Analytics is a product of Two Phase LLC,
Wyoming, USA.
