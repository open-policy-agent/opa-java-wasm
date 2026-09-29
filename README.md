[![CI](https://github.com/open-policy-agent/opa-java-wasm/workflows/CI/badge.svg)](https://github.com/open-policy-agent/opa-java-wasm)
[![GitHub Release](https://img.shields.io/github/tag/open-policy-agent/opa-java-wasm.svg?style=flat&color=green)](https://github.com/open-policy-agent/opa-java-wasm/tags)
[![Maven Central](https://maven-badges.sml.io/sonatype-central/org.openpolicyagent/opa-java-wasm/badge.svg?style=flat&color=green)](https://central.sonatype.com/artifact/org.openpolicyagent/opa-java-wasm)

# Open Policy Agent WebAssembly Java SDK

This is an SDK for using WebAssembly(wasm) compiled [Open Policy Agent](https://www.openpolicyagent.org/) policies
with Java powered by [Endive](https://github.com/bytecodealliance/endive), a pure Java Wasm runtime.

Initial implementation was based
on [Open Policy Agent WebAssembly NPM Module](https://github.com/open-policy-agent/npm-opa-wasm)
and [Open Policy Agent WebAssembly dotnet core SDK](https://github.com/me-viper/OpaDotNet)

## Why

We want **fast**, **in-process** and **secure** OPA policies evaluation, and avoid network bottlenecks when using [opa-java](https://github.com/open-policy-agent/opa-java).

Using this integration for policy evaluation you can switch from the traditional integration pattern:

<p align="center">
  <picture>
    <img width="50%" src="imgs/traditional.png">
  </picture>
</p>

to a fully embedded:

<p align="center">
  <picture>
    <img width="50%" src="imgs/with-wasm.png">
  </picture>
</p>

# Getting Started

## Install the module

With Maven add the core module dependency:

```xml
<dependency>
    <groupId>org.openpolicyagent</groupId>
    <artifactId>opa-java-wasm</artifactId>
    <version>latest_release</version>
</dependency>
```

<!--
```java
//DEPS org.openpolicyagent:opa-java-wasm:999-SNAPSHOT

var policyPath = Path.of("core/src/main/resources/demo-policy.wasm");
var targetPath = Path.of("policy.wasm");
Files.copy(policyPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

var policyWasm = new File("policy.wasm");
```
-->

## Usage

There are only a couple of steps required to start evaluating the policy.

### Import the module

```java
import org.openpolicyagent.wasm.OpaPolicy;
```

### Load the policy

```java
var policy = OpaPolicy.builder().withPolicy(policyWasm).build();
```

The `policyWasm` can be a variety of things, including raw byte array, `InputStream`, `Path`, `File`.
The content should be the compiled policy Wasm file, a valid WebAssembly module.

For example:

```java
var policy = OpaPolicy.builder().withPolicy(new File("policy.wasm")).build();
```

### Evaluate the Policy

The `OpaPolicy` object returned from `loadPolicy()` has a couple of important
APIs for policy evaluation:

`data(data)` -- Provide an external `data` document for policy evaluation.

- `data` MUST be a `String`, which assumed to be a well-formed stringified JSON

`evaluate(input)` -- Evaluates the policy using any loaded data and the supplied
`input` document.

- `input` parameter MUST be a `String` serialized `object`, `array` or primitive literal which assumed to be a well-formed stringified JSON

Example:

```java
var input = "{\"path\": \"/\", \"role\": \"admin\"}";

var policy = OpaPolicy.builder().withPolicy(policyWasm).build();
var result = policy.evaluate(input);
System.out.println("Result is: " + result);
```

<!--
```java
Files.write(Paths.get("TestReadme.result"), (result + "\n").getBytes());
```
-->

> For any `opa build` created WASM binaries the result set, when defined, will
> contain a `result` key with the value of the compiled entrypoint. See
> [https://www.openpolicyagent.org/docs/latest/wasm/](https://www.openpolicyagent.org/docs/latest/wasm/)
> for more details.

## Policy Pool

`OpaPolicyPool` manages a bounded set of `OpaPolicy` instances for concurrent
use.  It uses lock-free data structures internally, so it is safe to use with
virtual threads (no carrier-thread pinning).

```java
import org.openpolicyagent.wasm.OpaPolicyPool;

var pool = OpaPolicyPool.create(
        () -> OpaPolicy.builder().withPolicy(policyWasm).build(),
        4); // at most 4 concurrent instances

try (var loan = pool.borrow()) {    // blocks if all 4 are in use
    loan.policy()
        .data("{\"role\": {\"alice\": \"admin\"}}")
        .input("{\"user\": \"alice\"}");
    String result = loan.policy().evaluate();
}
// policy is automatically returned to the pool

pool.close();
```

Each policy is reset to a clean state when returned to the pool (data, input
and entrypoint are cleared), so the next borrower always starts fresh.

If a processing error may have left the policy in a bad state, call
`loan.discard()` instead of letting `close()` return it:

```java
try (var loan = pool.borrow()) {
    try {
        loan.policy().data(data).input(input);
        result = loan.policy().evaluate();
    } catch (RuntimeException ex) {
        loan.discard(); // destroys this instance; pool will create a fresh one
        // handle error ...
    }
}
```

> **Note:** A single `OpaPolicy` instance is **not thread-safe**.
> Without the pool, use one instance per thread.

## Builtins support:

At the moment the following builtins are supported(and, by default, automatically injected when needed):

- String
  - `sprintf` **NOTE:** this implementation is [SDK-dependent](https://www.openpolicyagent.org/docs/latest/policy-reference/#builtin-strings-sprintf) and might generate different results depending on the runtime, please, limit the usage to trivial use-cases.

- Json
  - `json.is_valid`

- Yaml
  - `yaml.is_valid`
  - `yaml.marshal`
  - `yaml.unmarshal`

### Writing the policy

See
[https://www.openpolicyagent.org/docs/policy-language](https://www.openpolicyagent.org/docs/policy-language)

### Compiling the policy

Either use the
[Compile REST API](https://www.openpolicyagent.org/docs/latest/rest-api/#compile-api)
or `opa build` CLI tool.

For example:

```bash
opa build -t wasm -e example/allow example.rego
```

Which is compiling the `example.rego` policy file with the result set to
`data.example.allow`. The result will be an OPA bundle with the `policy.wasm`
binary included. See [./examples](./examples) for a more comprehensive example.

See `opa build --help` for more details.

## Support

This SDK is community supported and maintained. For bug reports and feature requests, please use Github issues. For real-time support, please join the [Open Policy Agent Slack](https://slack.openpolicyagent.org).

## Development

To develop this library you need to have installed the following tools:

- Java 11+
- Maven
- the `opa` cli
- `tar`

the typical command to build and run the tests is:

```bash
mvn spotless:apply clean install
```

to disable the tests based on the Opa testsuite:

```bash
OPA_TESTSUITE=disabled mvn spotless:apply install
```

## Releases

The versions in `core/pom.xml` are updated as part of the release process.
New releases are manually triggered by running the
[release workflow](https://github.com/open-policy-agent/opa-java-wasm/blob/main/.github/workflows/release.yaml).

This workflow requires a number of secrets to be set, while `OSSRH_PASSWORD` and
username should not need to be rotated, `JAVA_GPG_SECRET_KEY` should be updated
if the key has expired and has been removed from the
[keyserver](https://keyserver.ubuntu.com). This secret is **not** base64
encoded.

An example error from a release run where they key has expired, note that this
is not the final error about `Remote staging failed: Staging rules failure!`.

```
No public key: Key with id: (78fe9b032725616c) was not able to be located on &lt;a href=http://keyserver.ubuntu.com:11371/&gt;http://keyserver.ubuntu.com:11371/&lt;/a&gt;. Upload your public key and try the operation again.
```
