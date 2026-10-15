# Vendored JSch

Source: https://github.com/mwiede/jsch tag `jsch-2.28.8` (BSD-style license, see `LICENSE.txt`).

Why vendored: the sandbox/CI policy allows source distribution only; JSch is compiled
directly into the app as a Java source set.

Files removed from upstream (optional integrations the app does not use and whose
third-party dependencies are intentionally not declared):

- `Slf4jLogger.java`, `Log4j2Logger.java` (slf4j / log4j)
- `PageantConnector.java`, `SSHAgentConnector.java`, `JUnixSocketFactory.java`
  (JNA / junixsocket agent transports)
- `jgss/` (Kerberos GSS-API, not present on Android)

All algorithm class references in `JSch.java` are string literals, so removal only
drops those optional runtime registrations. `BouncyCastle` (`bcprov-jdk18on`) is
declared as a normal Gradle dependency, so the `com.jcraft.jsch.bc.*` providers
(Ed25519 fallback, ChaCha20-Poly1305, …) remain available on every Android API level.
