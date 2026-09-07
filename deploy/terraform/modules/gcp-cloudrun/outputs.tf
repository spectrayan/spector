output "service_url" {
  description = "Public URL of Cloud Run service"
  value       = google_cloud_run_v2_service.spector.uri
}

output "service_name" {
  description = "Cloud Run service name"
  value       = google_cloud_run_v2_service.spector.name
}
