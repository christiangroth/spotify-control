pluginManagement {
  includeBuild("gradle-plugin-releasenotes")
}

rootProject.name = "spotify-control"

include("adapter-in-outbox")
include("adapter-in-scheduler")
include("adapter-in-starter")
include("adapter-in-web")
include("adapter-out-mongodb")
include("adapter-out-outbox")
include("adapter-out-spotify")
include("application-quarkus")
include("domain-api")
include("domain-impl")
include("util-outbox")
include("util-starters")
