package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import jakarta.enterprise.context.ApplicationScoped

// Builds the app_dashboard_view read model once for existing deployments (see #867 Slow Queries, ADR-0014)
// so DashboardResource can read a single precomputed document instead of recomputing the dashboard on every
// request. Later updates happen inline whenever the events feeding into DashboardStats are handled.
@ApplicationScoped
@Suppress("Unused")
class BackfillDashboardViewStarter(
  private val dashboardPort: DashboardPort,
) : Starter {

  override val id = "BackfillDashboardViewStarter-v1"

  override fun execute() {
    dashboardPort.rebuildDashboardView()
  }
}
