rootProject.name = "spotify-control"

include("adapter-in-web")
include("adapter-out-mongodb")
include("adapter-out-outbox")
include("adapter-out-spotify")
include("adapter-in-outbox")
include("application-quarkus")
include("domain-api")
include("domain-impl")
include("util-outbox")
