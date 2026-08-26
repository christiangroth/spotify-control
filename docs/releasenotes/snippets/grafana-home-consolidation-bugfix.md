* Dashboard provisioning now targets a configurable Grafana Cloud stack instead of a hardcoded URL, and all dashboards use a consistent naming scheme so they no longer collide with dashboards from other apps.
* Consolidated the Grafana Cloud metrics/logs API key secrets into a single one, since they were always the same token.
* The Grafana links in the app's technical menu are now built dynamically and only shown once the Grafana Cloud stack is configured.
