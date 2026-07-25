* Added a Docker `HEALTHCHECK` to the JVM and native container images that polls the application's readiness endpoint, so orchestrators can detect and restart unhealthy containers automatically.
