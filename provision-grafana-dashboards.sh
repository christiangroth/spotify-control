#!/usr/bin/env bash
set -euo pipefail

# Provisions every Grafana dashboard JSON file found in monitoring/grafana/
# to Grafana Cloud. Add a new dashboard by dropping its JSON file into
# monitoring/grafana/ - no workflow changes are needed for it to be picked up.
#
# Required env vars:
#   GRAFANA_CLOUD_SA_TOKEN - Grafana Cloud service account token
# Optional env vars:
#   COMMIT_SHA - included in the provisioning message (defaults to "unknown")

if [ -z "${GRAFANA_CLOUD_SA_TOKEN:-}" ]; then
  echo "Error: GRAFANA_CLOUD_SA_TOKEN secret must be set" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dashboard_dir="$script_dir/monitoring/grafana"
commit_sha="${COMMIT_SHA:-unknown}"

provision_dashboard() {
  local dashboard_file="$1"
  local payload
  payload=$(jq -n \
    --argjson dashboard "$(cat "$dashboard_file")" \
    --arg message "Provisioned by GitHub Actions (commit $commit_sha)" \
    '{dashboard: $dashboard, overwrite: true, message: $message}')
  local delays=(30 60 120 300)
  local attempt=0
  while true; do
    echo "Provisioning $dashboard_file (attempt $((attempt+1)))..."
    if curl --fail-with-body -s -X POST \
      "https://spotifycontrolprod.grafana.net/api/dashboards/db" \
      -H "Authorization: Bearer $GRAFANA_CLOUD_SA_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$payload"; then
      echo ""
      echo "Successfully provisioned $dashboard_file"
      return 0
    fi
    if [ "$attempt" -ge "${#delays[@]}" ]; then
      echo "Error: Failed to provision $dashboard_file after all retries" >&2
      return 1
    fi
    local wait_time="${delays[$attempt]}"
    echo "Retrying in ${wait_time}s..."
    sleep "$wait_time"
    attempt=$((attempt+1))
  done
}

shopt -s nullglob
dashboard_files=("$dashboard_dir"/*.json)
if [ "${#dashboard_files[@]}" -eq 0 ]; then
  echo "Error: No dashboard files found in $dashboard_dir" >&2
  exit 1
fi

exit_code=0
for dashboard_file in "${dashboard_files[@]}"; do
  provision_dashboard "$dashboard_file" || exit_code=1
done
exit "$exit_code"
