resource "azurerm_container_app" "spector" {
  name                         = var.name
  container_app_environment_id = var.container_app_environment_id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  template {
    container {
      name   = "spector"
      image  = var.image
      cpu    = var.cpu
      memory = var.memory

      env {
        name  = "SPECTOR_PORT"
        value = "7070"
      }
      env {
        name  = "SPECTOR_DIMS"
        value = tostring(var.dimensions)
      }

      volume_mounts {
        name = "spector-storage"
        path = "/data"
      }
    }

    volume {
      name         = "spector-storage"
      storage_type = "AzureFile"
      storage_name = var.storage_name
    }

    min_replicas = 1
    max_replicas = 1
  }

  ingress {
    external_enabled = var.external_ingress
    target_port      = 80
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}
