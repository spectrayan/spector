output "fqdn" {
  description = "Fully qualified domain name of the Container App"
  value       = azurerm_container_app.spector.latest_revision_fqdn
}

output "id" {
  description = "Container App ID"
  value       = azurerm_container_app.spector.id
}
