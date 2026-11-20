# VerifyInboxes Java library

[![CI](https://github.com/verifyinboxes/verifyinboxes-java/actions/workflows/ci.yml/badge.svg)](https://github.com/verifyinboxes/verifyinboxes-java/actions/workflows/ci.yml)

The official Java library for the [VerifyInboxes](https://verifyinboxes.com) API: check whether email addresses can
receive mail, one at a time or as lists of any size, and read the results.

- Retries that never charge twice, waiting for checks and lists, results read page by page
- A blocking client and the same calls as `CompletableFuture`s; an interrupted or cancelled call names every job it
  made
- Exceptions by kind, each naming the request it came from
- Webhook signature checks
- Java 11 or later, `java.net.http` and Jackson

## Install

Not published yet: until the first release is on Maven Central, build it from a clone and install it into your
local Maven repository:

```bash
git clone https://github.com/verifyinboxes/verifyinboxes-java
cd verifyinboxes-java
./mvnw -B install
```

Then, and once released, Maven:

```xml
<dependency>
  <groupId>com.verifyinboxes</groupId>
  <artifactId>verifyinboxes-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

or Gradle:

```kotlin
implementation("com.verifyinboxes:verifyinboxes-java:0.1.0")
```

It needs Java 11 or later and brings one library, `com.fasterxml.jackson.core:jackson-databind` 2.18.2. Its packages
are `com.verifyinboxes` (the client), `com.verifyinboxes.models` (what the API returns) and
`com.verifyinboxes.errors` (the exceptions); its module name is `com.verifyinboxes`.

## Quickstart

An organisation admin makes an API key in the VerifyInboxes app under **Developers** → **API keys**. Put it in the
environment:

```bash
export VERIFYINBOXES_API_KEY="bv_..."
```

```java
import com.verifyinboxes.VerifyInboxesClient;
import com.verifyinboxes.models.Result;
import java.util.List;

public class Quickstart {
    public static void main(String[] args) {
        try (VerifyInboxesClient client = VerifyInboxesClient.fromEnv()) { // reads VERIFYINBOXES_API_KEY
            Result result = client.verify("ann@example.com");
            System.out.println(result.verdict());    // valid, invalid, catch_all, risky or unknown
            System.out.println(result.safeToSend()); // true or false
            System.out.println(result.reason());     // why, e.g. mailbox_not_found
            System.out.println(result.jobId());      // the check's job

            for (Result each : client.verifyMany(List.of("bob@example.com", "cat@example.com"))) {
                System.out.println(each.email() + " " + each.verdict());
            }
        }
    }
}
```

An exception's message ends with the request id of the API's answer.

## The client

```java
VerifyInboxesClient client = VerifyInboxesClient.builder()
        .apiKey("bv_...")                               // default: the VERIFYINBOXES_API_KEY environment variable
        .baseUrl("https://api.verifyinboxes.com")       // default: VERIFYINBOXES_BASE_URL, else this
        .timeout(Duration.ofSeconds(60))                // one attempt of one request
        .maxRetries(2)                                  // see "Retries"
        .httpClient(HttpClient.newHttpClient())         // its proxy, TLS and executor settings
        .executor(myExecutor)                           // where the async() calls run
        .userAgent("my-app/1.2")                        // put in front of verifyinboxes-java/<version>
        .build();
```

Without a key, `build()` throws `MissingApiKeyException`, which names the variable. A key with a space or a control
character in it, or one that is not ASCII (half of a copy and paste), is refused there too, and the exception never
repeats it. Keep the key out of your code: the environment variable is the place for it. The client never logs it,
and its `toString()` shows only its first 6 characters.

Make one client and share it: it is safe for use by several threads at once, and keeps its connections open between
calls. `close()` (it is `AutoCloseable`) lets go of the threads it made; an executor you gave it is not shut down.
Every method blocks until it is done; `client.async()` has the same calls returning a `CompletableFuture` (below).
Options are small immutable objects (`VerifyOptions`, `CreateListParams`, `ResultsParams`, `DownloadParams`) and
every duration is a `java.time.Duration`.

A client given an `HttpClient` must not follow redirects (the default): the API does not send one, and the key is
never sent anywhere else. Every request is sent as HTTP/1.1, whatever the `HttpClient`'s version, and no client is
made while the JDK's switch `jdk.httpclient.enableAllMethodRetry` is on (see "Retries").

### Personal access tokens

Where an API key works, so does a personal access token (`vi_at_...`): what an app gets when a person signs in to it
with their VerifyInboxes account. It acts as that person, with their role in the organisation they chose, and lives
an hour; after that the API answers `401` (`AuthenticationException`). Pass it as the key, and make a new client
with the new token once it is renewed.

## Checking addresses

### One address

```java
Result result = client.verify("ann@example.com");
Result fresh = client.verify("ann@example.com", VerifyOptions.defaults().forceRecheck(true)); // skip the cache
```

The API answers within about 5 seconds, or says the check is still running; then the client asks every 2 seconds
until it is done, for up to the options' `timeout` (default 60 seconds). Every result names its job
(`result.jobId()`): a `catch_all` or `risky` one can later be sent to `client.lists().resolve`.

| `Result` | |
| --- | --- |
| `email()` | The address, as checked. |
| `verdict()` | `valid`, `invalid`, `catch_all`, `risky` or `unknown`. |
| `safeToSend()` | Whether to send to it: `valid`, or a `catch_all` whose score reaches your organisation's threshold. |
| `score()` | For a `catch_all` verdict, the probability that the mailbox exists (0 to 1), else `null`. |
| `reason()` | Why: `mailbox_exists`, `mailbox_not_found`, `no_mail_server`, `accept_all`, `syntax`, `typo`, ... |
| `didYouMean()` | For a `typo`: the address it probably is. |
| `flags()` | `disposable`, `role`, `free_provider`, `gateway`. |
| `provider()`, `catchAllDomain()`, `source()`, `verifiedAt()` | The domain's mail provider, whether it accepts every address, where the answer came from, and when. |
| `jobId()` | The job that holds the result. |

The API adds verdicts, reasons and flags over time: a value you do not know is kept as the string it is, never an
error. Show it, and act on `safeToSend()`.

### Waiting, and picking a check up again

A check that has not finished when the timeout runs out throws a `WaitTimeoutException`. The check carries on, and
the exception names its job, so nothing is lost:

```java
try {
    result = client.verify("ann@example.com", VerifyOptions.defaults().timeout(Duration.ofSeconds(10)));
} catch (WaitTimeoutException stillRunning) {
    String jobId = stillRunning.jobIds().get(0);
    // ... later: picks it up where it was
    result = client.checks().waitFor(jobId);
}
```

`client.checks().start` does not wait at all: you get the result, or a pending check with its job.

```java
Check check = client.checks().start("ann@example.com");
if (check.isPending()) {
    Result result = client.checks().waitFor(check.jobId(), Duration.ofMinutes(2));
} else {
    System.out.println(check.result().verdict());
}

Job job = client.checks().get(jobId); // the job as it is now; job.result() once it is done
```

### Many addresses

**Never check many addresses with a loop of `verify`.** Checks go ahead of lists only up to 10 running at once:
past that, your checks wait behind other work, and a loop gains nothing over the ways below.

`verifyMany` returns the results in the order of its input:

```java
List<Result> results = client.verifyMany(addresses, Duration.ofMinutes(10));
```

Up to 10 addresses are checked one by one, together. More go as one list (below), which the call waits for and
reads back; every result's `jobId()` is then the list's. For a large or long-running batch, use a list yourself. An
empty list, or an empty address, is refused before any request.

### Lists

```java
JobCreated created = client.lists().create(CreateListParams.file(Path.of("prospects.csv")).emailColumn("email"));
created = client.lists().create(CreateListParams.stream(inputStream, "prospects.csv"));   // read as it is sent
created = client.lists().create(CreateListParams.csv("email\nann@example.com\n"));        // a CSV's text
created = client.lists().create(CreateListParams.emails(List.of("ann@example.com", "bob@example.com")));

Job job = client.lists().waitFor(created.id()); // asks at once, then after 2, 4, 8, 16 and every 30 seconds
System.out.println(job.status() + " " + job.counts().valid()); // done, and how many are valid
```

The upload's name is the file's name, else `list.csv` (or `.filename(...)`).

**How long a list takes.** A few hundred addresses take minutes, and Microsoft-hosted ones longer.
`lists().waitFor(id)` waits as long as it takes; `lists().waitFor(id, timeout)` throws a `WaitTimeoutException` after
the timeout (the list carries on), and `lists().waitFor` picks it up again at any time. Instead of waiting, you can be
told: see "Webhooks" below.

The results, every row once and in order, read a page at a time as you iterate (nothing is asked until then, and
each iteration starts again from the first page):

```java
for (ResultRow row : client.lists().results(created.id())) {
    System.out.println(row.row() + " " + row.email() + " " + row.verdict() + " " + row.safeToSend());
}

client.lists().results(id, ResultsParams.defaults().onlySafeToSend(true));
client.lists().results(id, ResultsParams.defaults().verdicts("valid", "catch_all"));
client.lists().results(id, ResultsParams.defaults().partial(true));   // a list still running, as it stands
client.lists().results(id, ResultsParams.defaults().pageSize(1000));  // rows per request, 1 to 1000 (default 500)
```

To keep your own cursor (to resume elsewhere), read one page at a time:

```java
ResultsPage page = client.lists().resultsPage(id, null);
while (true) {
    save(page.data());
    if (page.nextCursor() == null) {
        break;
    }
    page = client.lists().resultsPage(id, page.nextCursor());
}
```

A page may hold fewer rows than asked, even none, before the last one: follow `nextCursor()` until it is `null`. A
page that answers with the very cursor it was asked with is an error (`VerifyInboxesException`), never an endless
loop.

The CSV you sent, with the results appended:

```java
long written = client.lists().download(id, Path.of("results.csv"));
client.lists().download(id, Path.of("safe.csv"), DownloadParams.defaults().onlySafeToSend(true));
client.lists().download(id, outputStream); // any OutputStream, not closed
```

A download to a `Path` is written beside the file and renamed over it once the whole download is there: if it fails,
the file that was there is untouched, no part file is left, and a download cut short is made again from the start.
A download to an `OutputStream` is sent again until the stream has its first byte; after that a failure is thrown.
The stream is written from the HTTP client's threads, one write at a time; once the call has ended no write starts,
though one the stream was blocked in finishes when the stream lets it.

`client.lists().get(id)` is the list as it is now.

### Catch-all resolution

For an organisation allowed to send test emails, the `catch_all` and `risky` addresses of a finished list or check
can be settled by a test email. Most are settled within about 3 days of sending, some take up to 7 days. One request
takes at most 500 addresses (more is refused with `too_many_to_resolve`): for a list with more, name them in parts of
at most 500:

```java
ResolveCreated resolution = client.lists().resolve(created.id());                       // all of them, if at most 500
ResolveCreated some = client.lists().resolve(created.id(), List.of("bob@example.com"));
Job job = client.lists().waitFor(resolution.id());
```

## Everything else

```java
Account account = client.account().get(); // name(), balance(), reviewed(), testEmails(), safeToSendThreshold()

Domain domain = client.domains().get("example.com"); // provider(), mxHosts(), catchAll(), emailPattern(), ... (1 credit)

int added = client.suppressions().add(List.of("ann@example.com"), List.of("competitor.example"));
int removed = client.suppressions().remove(List.of("ann@example.com"), null);
SuppressionList list = client.suppressions().list(); // domains(), and addressCount() (addresses are kept only as a hash)

WebhookCreated hook = client.webhooks().create("https://example.com/webhooks/verifyinboxes",
        List.of(Webhooks.EVENT_JOB_COMPLETED));
hook.secret(); // shown only now: store it
List<Webhook> hooks = client.webhooks().list();
client.webhooks().delete(hook.id());

NoticePage page = client.notices().list(lastSeen, 100); // verdicts you bought, overturned since
// keep page.nextAfter() for next time

FeedbackResult reported = client.feedback().report(List.of(
        FeedbackEvent.of("ann@example.com", "hard_bounce", "2026-11-04T10:00:00Z").withDsnCode("5.1.1").withEventId("b-1")));

IntegrationCreated integration = client.integrations().create("instantly", "main workspace");
integration.url(); // shown only now: paste it into the provider
List<Integration> integrations = client.integrations().list();
client.integrations().delete(integration.id());
```

`WebhookCreated.secret()` and `IntegrationCreated.url()` are left out of the models' `toString()`, and kept when you
read them or write the model as JSON.

## Asynchronous calls

`client.async()` has every call above returning a `CompletableFuture`, run on the client's executor (by default its
own pool of daemon threads, named `verifyinboxes-<n>`, let go by `close()`):

```java
client.async().verify("ann@example.com")
        .thenAccept(result -> System.out.println(result.verdict()));

CompletableFuture<List<ResultRow>> rows = client.async().lists().results(id); // every row, read whole
```

A call that fails completes its future with the exception the blocking call would have thrown (`join()` wraps it in
a `CompletionException`). `async().lists().results` reads every row into a list; for a list too large to hold, read
it a page at a time with `async().lists().resultsPage`.

## Errors

Every exception is unchecked, and a `VerifyInboxesException`:

| Exception (`com.verifyinboxes.errors`) | When |
| --- | --- |
| `InvalidRequestException` | `400`, `413`, `414`, `415`, `422`: the request does not fit (`invalid_request`, `consumer_list`, ...) |
| `AuthenticationException` | `401`: the key or token is missing, mistyped, revoked, or past its hour |
| `InsufficientCreditsException` | `402`: the balance does not cover the request; nothing was charged |
| `PermissionDeniedException` | `403`: allowed for nobody with this credential |
| `NotFoundException` | `404 not_found`, or `410 gone` (a list whose data was deleted) |
| `ConflictException` | `409`: `not_ready` (the list has not finished), `idempotency_conflict` |
| `RateLimitException` | `429`: retried on its own, unless `Retry-After` is over 60 seconds |
| `ServerException` | `5xx`: retried on its own; the exception is the last answer's |
| `ApiException` | the base of the above: every answer of the API with an error status, any other `4xx` included |
| `ConnectionException` | the request got no answer |
| `ApiTimeoutException` | ... in time (a `ConnectionException`) |
| `WaitTimeoutException` | a check or a list was still running when the time to wait ran out |
| `CancelledException` | the call's thread was interrupted: see "Cancellation" |
| `MissingApiKeyException` | no key given and `VERIFYINBOXES_API_KEY` not set |
| `VerifyInboxesException` itself | the API answered something it should not have |

A wrong argument (an empty list of addresses, a page size of 0) is an `IllegalArgumentException`, thrown before
anything is sent; a file that cannot be read or written, an `UncheckedIOException`.

An `ApiException` carries what the API said, and the request id support asks for:

```java
try {
    client.lists().create(CreateListParams.file(Path.of("prospects.csv")));
} catch (InsufficientCreditsException e) {
    System.out.println("top up first");
} catch (ApiException e) {
    System.out.println(e.statusCode());   // 422
    System.out.println(e.code());         // consumer_list  <- match on this
    System.out.println(e.requestId());    // the answer's X-Request-Id: quote it when you write to support
    System.out.println(e.getMessage());   // 422 consumer_list: ... (request id 019a...)
}
```

| `ApiException` | |
| --- | --- |
| `statusCode()` | The HTTP status. |
| `code()` | The API's error code, or `null` when the answer was not the API's (a proxy's error page). Match on it; treat one you do not know by its status. |
| `apiMessage()` | What the API said. The exception's message is `<status> <code>: <message> (request id <id>)`: show it or log it; do not parse it. |
| `requestId()` | The answer's `X-Request-Id`. After retries, the last answer's. |
| `retryAfter()` | The answer's `Retry-After`, when it had one; else `null`. |
| `body()` | The answer's body as it came. |
| `jobIds()` | The jobs the call had made when it failed. |

**Job ids.** Every exception thrown while waiting, and by `verifyMany`, names every job the call made, in the order
of its input: `e.jobIds()`, or `VerifyInboxesException.jobIdsOf(e)` whatever wraps it (a `CompletionException`, a
`CancellationException`). Those jobs carry on, and the credits they hold are spent on them: pick them up with
`client.checks().waitFor` or `client.lists().waitFor` instead of starting again.

## Cancellation

Interrupting the thread of a call stops it, whatever it was doing (a request, a pause before a retry, waiting for a
check or a list): it throws a `CancelledException`, and the thread stays interrupted; nothing is sent again. One
request is let finish, so that no job you are charged for goes unnamed: the one that starts a check (`verify`,
`checks().start`, each check of `verifyMany`) or `verifyMany`'s list, when it was already sent. It ends within the
client's timeout, the job it made goes into the exception's `jobIds()`, and nothing more is started or waited for.

Cancelling the future of an `async()` call does the same. For the calls that can make a job (`verify`, `verifyMany`
and `checks().start`), `future.cancel(true)` **blocks** until the call has stopped (at most the client's timeout,
usually the few seconds the API takes to answer a check), and the future's `CancellationException` names the jobs
through its cause; if the call had its result first, the future keeps it and `cancel` returns `false`. Every other
call is cancelled at once, without waiting:

```java
CompletableFuture<List<Result>> pending = client.async().verifyMany(addresses);
// ...
pending.cancel(true);
try {
    pending.join();
} catch (CancellationException e) {
    System.out.println("still running: " + VerifyInboxesException.jobIdsOf(e));
}
```

A check or a list that `verify` or `verifyMany` had started is not stopped by the cancellation: it carries on and its
credits are held, and the exception names it: wait for it again with `checks().waitFor` or `lists().waitFor`. A
future completed from outside (`orTimeout`, `complete`) stops its call too, but cannot name its jobs: give the call
its own timeout instead (`VerifyOptions.timeout`, `verifyMany(emails, timeout)`), which does.

`lists().create` and `lists().resolve` stop at once, even when their request has already reached the API (so that an
upload can be cancelled). On Java 16 and later the request is cut off there and then. On Java 11 to 15 the JDK's
`HttpClient` cannot cut off a request it has begun: a file, a CSV's text or a stream stops being read, but what was
already handed to the connection (a short CSV, or a list of addresses sent as JSON, whole) may still arrive. Either
way, if it had reached the API, the list or the resolution exists and holds credits, but it is not in `jobIds()`. If you may interrupt one, pass your own `idempotencyKey`, and after a `CancelledException` make the same
call again with the same key, within 24 hours (the API keeps a key's answer that long):

- if the first request ran, the API answers with what it answered then (the list it made, or its error) and runs
  nothing again: nothing more is charged;
- if it is still running, the API answers `409 idempotency_in_progress`, which the library sends again after its
  `Retry-After` (up to `maxRetries` times);
- if it never ran (for example it never arrived, or was answered `429` or `5xx`), it runs now, once.

An upload is known by its key alone, never by its file: send the same file again under the same key, and a new key
with a new file.

## Retries

A request that fails in a way worth retrying is sent again, up to `maxRetries` times (default 2):

| Failure | Retried |
| --- | --- |
| No answer: the connection failed, was reset or closed, or timed out | Yes |
| `429` | Yes, after `Retry-After` |
| `409 idempotency_in_progress` | Yes, after `Retry-After` |
| `5xx` | Yes |
| Any other `4xx` | No: the same request would get the same answer |

The wait is the answer's `Retry-After` when it has one. A `Retry-After` over 60 seconds is not waited for: the
exception is thrown at once, with the wait in `retryAfter()`. Otherwise it backs off exponentially, with jitter: up
to half a second before the first retry, up to a second before the second, doubling to at most eight.

**Nothing is charged twice.** Every `POST` carries an `Idempotency-Key`, the same on every attempt of one call.
When an answer is lost after the API ran the request (a check, a list, an upload, a resolution, feedback, a
suppression), the retry gets the first answer again: one job, charged once. Every request is sent as HTTP/1.1, where
the JDK's `HttpClient` never sends a `POST` (or a `DELETE`) again by itself, so every resend of one is this
library's, by the rules here. (It does send a `GET` again once by itself when the connection it reused turns out to
be closed; over HTTP/2 it would also resend a request a closing server says it did not process, by rules that have
changed between Java releases.) The JDK's switch `jdk.httpclient.enableAllMethodRetry` would make it resend every
method, a `POST` included, by itself: `build()` refuses to make a client while it is on, read as the JDK reads it (the
system property, else a line of the JDK's `conf/net.properties`; `-Djdk.httpclient.enableAllMethodRetry` with no
value counts as on), with an `IllegalStateException` naming it. The JDK reads the switch only once, when the first
request of the JVM is sent: set it off before then.

A `409 idempotency_in_progress` (`ConflictException`) left after the retries means the first request with that key
is still running on the API. Repeat the call later with your own `idempotencyKey`, the same one: the API then answers
with what the first request answered, and runs nothing twice. (Without a key of your own, each call makes a new one,
which cannot be sent again: pass your own where a repeat matters.)

Your own key makes one request safe to send again after your program restarts. A key names **one** request, and the
API keeps its answer for 24 hours: in that time the same key gets that answer again, whatever you send with it. An
upload is known by its key alone, never by its file, so another file under a used key gets the first file's list;
and an error is kept like any answer, so a `402` stays a `402` under its key. So make the key from the request
itself, and give a new file, or a retry after you topped up, a new key:

```java
client.verify("ann@example.com", VerifyOptions.defaults().idempotencyKey("order-" + orderId)); // one key per order

byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("prospects.csv")));
client.lists().create(CreateListParams.file(Path.of("prospects.csv"))
        .idempotencyKey("import-" + HexFormat.of().formatHex(digest))); // one key per file (HexFormat: Java 17)
```

A file is sent again whole: a `Path` is read again, a CSV's text is sent again from memory. A stream
(`CreateListParams.stream`) is sent as it is read, so it is not sent again once its sending began.

Two requests are not sent again once they may have arrived: `webhooks().create` and `integrations().create`, whose
answers hold a secret shown once. They are retried only when the connection was never made, or after a `429`; after
a `ConnectionException`, look at `webhooks().list()` or `integrations().list()` before making another. A `DELETE`
whose answer was lost is sent again; if the retry finds it gone, the first one did it.

```java
VerifyInboxesClient client = VerifyInboxesClient.builder().maxRetries(0).build(); // never retry
```

## Webhooks

A webhook gets a `POST` when a list finishes (`job.completed`) and when a verdict you bought is overturned
(`address.contradicted`). Check its signature before you read anything in it, against the raw body as it arrived,
never JSON parsed and written again:

```java
byte[] body = exchange.getRequestBody().readAllBytes();
String header = exchange.getRequestHeaders().getFirst(Webhooks.SIGNATURE_HEADER);
if (!Webhooks.verifySignature(body, header, secret)) {
    exchange.sendResponseHeaders(400, -1); // not from VerifyInboxes, changed on the way, or too old
    return;
}
// ... read the event from body
```

`verifySignature` returns `true` only for a valid signature, and never throws: a header it cannot read is not
valid. `secret` is the 64 characters `webhooks().create` returned. A signature more than 5 minutes from your clock is
refused, so that an old delivery cannot be sent again: `verifySignature(body, header, secret, tolerance, now)` sets
both. It needs no client and no key (`client.webhooks().verifySignature` is the same check). Deliveries come at least
once: `X-Boxverifier-Delivery` (`Webhooks.DELIVERY_HEADER`) is the same on every retry of one, to drop duplicates.

## Types

Every answer is an immutable class named after the API's schemas (`Result`, `Job`, `JobCreated`, `ResultRow`,
`ResultsPage`, `Domain`, ...) in `com.verifyinboxes.models`. Its accessors are its fields in camelCase
(`safeToSend()` for `safe_to_send`); a value that can be `null` is a boxed type, and a list is never `null`.
Timestamps are ISO 8601 strings. `toString()` is the model's JSON, with the API's names; Jackson writes and reads
them the same way. A field this version does not know is ignored, and a value it does not know is kept as the
string it is, never an error.

## Examples

- [`Quickstart.java`](https://github.com/verifyinboxes/verifyinboxes-java/blob/main/src/test/java/com/verifyinboxes/examples/Quickstart.java):
  one check, then several with `verifyMany`.
- [`ListFromCsv.java`](https://github.com/verifyinboxes/verifyinboxes-java/blob/main/src/test/java/com/verifyinboxes/examples/ListFromCsv.java):
  a list from a CSV: start it, wait, read the results, download them.
- [`WebhookReceiver.java`](https://github.com/verifyinboxes/verifyinboxes-java/blob/main/src/test/java/com/verifyinboxes/examples/WebhookReceiver.java):
  a webhook receiver on the JDK's own HTTP server, checking the signature before anything else.

They are compiled with the tests. Run one from a clone:

```bash
./mvnw -B -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:target/test-classes:$(cat target/classpath.txt)" com.verifyinboxes.examples.Quickstart ann@example.com
```

## Versioning

The library follows [semantic versioning](https://semver.org). It is `0.x` until the API's public launch, when it
becomes `1.0.0`; until then a minor version may change the library's surface, and the changes are listed in its
release notes. The API itself changes without breaking you: it adds fields, values and error codes, which this
library keeps as they are. `Version.SDK` is the version, as the `User-Agent` names it.

## Development

```bash
./mvnw -B verify   # compiles (every lint warning an error), tests, and builds the jar, sources and javadoc
```

The first run downloads Maven 3.9.9. The tests run against a fake API on `127.0.0.1` (no network).
`src/test/resources/conformance/scenarios.json` is shared by every official VerifyInboxes library: what each must
send and do on the wire (retries with the same `Idempotency-Key`, job ids kept on a timeout, request ids on errors,
results through empty pages, the webhook signature cases). `ConformanceTest` replays it through the blocking client
and through `async()`, each request on a connection already used once. `SmokeTest` runs against the live API when
`VERIFYINBOXES_SMOKE_API_KEY` holds a key (it spends a few credits).

## Licence

MIT. See [LICENSE](https://github.com/verifyinboxes/verifyinboxes-java/blob/main/LICENSE). To report a security
problem, see [SECURITY.md](https://github.com/verifyinboxes/verifyinboxes-java/blob/main/SECURITY.md).
