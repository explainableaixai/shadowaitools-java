# shadowaitools (Java)

Scan network exports for AI applications from the JVM. Hand `ShadowAIToolsClient.scan` the path of a resolver log, gateway log or firewall CSV. It pulls out the hostnames, looks every one up in the AI tool register, and gives you back a list in which AI products are flagged, described and paired with their vendors' data-use terms. The browser-based [shadow AI detection tool for log exports](https://www.shadowaitools.com/free-shadow-ai-audit.php) does the same with reports attached. This library brings it into Java tooling.

## Dependency

```xml
<dependency>
  <groupId>io.github.explainableaixai</groupId>
  <artifactId>shadowaitools</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Quickest route: a JBang script

JBang runs a single Java file with its dependencies declared in comments. There is no project to set up, which makes it ideal for a one-off audit on an admin workstation:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.github.explainableaixai:shadowaitools:1.0.0

import com.alphaquantum.shadowaitools.ShadowAIToolsClient;
import java.util.Map;

public class aiscan {
    public static void main(String... args) throws Exception {
        var client = new ShadowAIToolsClient(System.getenv("AQ_API_KEY"));
        System.out.println("domain,category,trains_on_data");
        for (Map<String, Object> r : client.scan(args[0])) {
            if (Boolean.TRUE.equals(r.get("blocked"))) {
                System.out.printf("%s,%s,%s%n", r.get("domain"), r.get("primary_category"), r.get("trains_on_data"));
            }
        }
    }
}
```

Run `jbang aiscan.java proxy-export.csv > ai-inventory.csv` and open the CSV in any spreadsheet.

## What scan does, step by step

1. Reads the file line by line (`Files.readAllLines`).
2. Splits each line on whitespace, commas and semicolons.
3. Parses each token as a URI, adding `https://` when no scheme is present. Tokens that are not valid URIs are skipped quietly.
4. Keeps hosts containing a dot, lower-cased, in first-seen order without duplicates.
5. Calls `check` for each host in turn and returns the results as a `List<Map<String, Object>>`.

Because the parser ignores columns, it handles Squid and gateway logs, firewall CSVs and plain domain lists without configuration. The trade-off: IP addresses in the file also count as hosts and cost a lookup each. On wide exports, keep only the hostname column first.

## Reading a finding

Each map has `domain` and `blocked`. For AI tools you also get `primary_category`, `ai_type`, a `categories` list, `matched_domain` when a parent domain matched, and the vendor's training terms: `trains_on_data`, `opt_out_available`, `enterprise_no_training` and `api_no_training`, plus `terms_checked`.

`unstated` in a training field means the terms were read and do not address the question. Auditors usually list those tools separately, because the vendor has not committed to anything.

## Failure behaviour

`scan` stops at the first exception. For long files, extract the hosts and loop with your own handling instead:

```java
for (String host : hosts) {
    try {
        results.add(client.check(host));
    } catch (ApiException e) {
        if (e.getStatusCode() == 429) { Thread.sleep(30_000); continue; }
        if (e.getStatusCode() == 401 || e.getStatusCode() == 403) throw e;   // key or quota problem
    } catch (HttpTimeoutException e) {
        log.warn("timeout for {}", host);
    }
}
```

The default request timeout is 30 seconds. The builder's `timeout(Duration)` changes it.

## Scheduling

On a server, a Quartz or Spring `@Scheduled` job can scan the newest export every week and store each result set with a date. The difference between two weeks is the list of newly adopted tools:

```java
Set<String> added = new HashSet<>(thisWeek);
added.removeAll(lastWeek);
```

That short list is usually the most valuable line in a monthly security report.

## Load and quota

Lookups are sequential by design, so each unique host costs one call and the load stays even. Keep a local record of hosts already checked, and later runs only pay for new ones.

## Data handling

Only hostnames are sent, one per request. The rest of each log line (users, internal addresses, timestamps) never leaves your machine. Join results back to the log locally for per-user or per-department views, and store that join under the log's access rules.

## Frameworks and regulation

An AI inventory underpins several frameworks: deployer duties under the EU AI Act, ISO/IEC 42001 management systems, and the Map function in NIST's AI Risk Management Framework. A dated CSV from this library is a concrete artefact to hand to an auditor.

## Related data

Every finding is backed by the register used to [detect unauthorized AI tool use](https://www.aitoolsblocklist.com). Hosts cleared as non-AI can get a [domain category check](https://www.urlcategorizationdatabase.com/check-domain.php) instead. If the scan shows your own automation, put an [AI agent allow list against agent incidents](https://www.aiagentallowlist.com/ai-agent-incidents-report.php) in place.

Analysts who prefer notebooks can use [the Python scanner](https://pypi.org/project/shadowaitools/). There is also [a Go library for static binaries](https://pkg.go.dev/github.com/explainableaixai/shadowaitools-go) and [a Dart version](https://pub.dev/packages/shadowaitools).

## License

MIT
