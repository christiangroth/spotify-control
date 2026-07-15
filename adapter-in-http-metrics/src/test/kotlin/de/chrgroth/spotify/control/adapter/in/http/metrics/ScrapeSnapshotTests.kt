package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScrapeSnapshotTests {

  @Test
  fun `repeated reads within the ttl reuse the cached value without re-fetching`() {
    var fetchCount = 0
    val snapshot = ScrapeSnapshot(ttl = Duration.ofSeconds(1), nanoTime = { 0L }) { fetchCount++ }

    val first = snapshot.current()
    val second = snapshot.current()

    assertThat(first).isEqualTo(0)
    assertThat(second).isEqualTo(0)
    assertThat(fetchCount).isEqualTo(1)
  }

  @Test
  fun `a read after the ttl has expired triggers a fresh fetch`() {
    var fetchCount = 0
    var now = 0L
    val snapshot = ScrapeSnapshot(ttl = Duration.ofSeconds(1), nanoTime = { now }) { fetchCount++ }

    val first = snapshot.current()
    now = Duration.ofSeconds(2).toNanos()
    val second = snapshot.current()

    assertThat(first).isEqualTo(0)
    assertThat(second).isEqualTo(1)
    assertThat(fetchCount).isEqualTo(2)
  }

  @Test
  fun `a read exactly at the ttl boundary triggers a fresh fetch`() {
    var fetchCount = 0
    var now = 0L
    val snapshot = ScrapeSnapshot(ttl = Duration.ofSeconds(1), nanoTime = { now }) { fetchCount++ }

    snapshot.current()
    now = Duration.ofSeconds(1).toNanos()
    snapshot.current()

    assertThat(fetchCount).isEqualTo(2)
  }
}
