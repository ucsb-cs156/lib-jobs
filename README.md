# lib-jobs

Shared asynchronous jobs system for [ucsb-cs156](https://github.com/ucsb-cs156)
course projects:

- a **Maven library** (Spring Boot auto-configured starter) providing the
  `Job` entity, `JobService`, admin REST endpoints, and async executor wiring
- an **npm package** (`@ucsb-cs156/jobs-components`) providing the admin UI
  components (planned; see rollout plan)

Extracted from the homegrown jobs system previously duplicated in
[proj-courses](https://github.com/ucsb-cs156/proj-courses),
[proj-frontiers](https://github.com/ucsb-cs156/proj-frontiers),
[proj-scaffold](https://github.com/ucsb-cs156/proj-scaffold), and
[proj-happycows](https://github.com/ucsb-cs156/proj-happycows);
[proj-dining](https://github.com/ucsb-cs156/proj-dining) will be the first
fresh installation.

## Status

Design phase. See [docs/DESIGN.md](docs/DESIGN.md) for the full design,
including the drift survey of the existing implementations, decoupling
decisions, publishing setup (JitPack + npmjs), and the phased rollout plan.

## Usage (planned)

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.ucsb-cs156</groupId>
  <artifactId>lib-jobs</artifactId>
  <version>v0.1.0</version>
</dependency>
```
