package de.chrgroth.spotify.control.domain.port.out.readmodel

import de.chrgroth.spotify.control.domain.model.DashboardStats

interface DashboardViewRepositoryPort {
  fun save(stats: DashboardStats)
  fun find(): DashboardStats?
}
