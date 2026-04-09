package de.chrgroth.spotify.control.domain.model.playback.aggregation

enum class ActivityTimeWindow(val fromHour: Int) {
  H00_06(0),
  H06_12(6),
  H12_18(12),
  H18_24(18),
  ;

  companion object {
    fun fromHour(hour: Int): ActivityTimeWindow = when {
      hour < 6 -> H00_06
      hour < 12 -> H06_12
      hour < 18 -> H12_18
      else -> H18_24
    }
  }
}
