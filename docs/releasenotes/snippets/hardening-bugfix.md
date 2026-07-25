* Hardened the CI pipeline: pushes now only trigger builds on `main`, concurrent runs on the same branch are guarded, and the Gradle cache is only written from `main`.
