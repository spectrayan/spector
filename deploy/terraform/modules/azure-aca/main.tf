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
        name  = "SPECTOR_EMBEDDING_DIMS"
        value = tostring(var.dimensions)
      }
      env {
        name  = "SPECTOR_EMBEDDING_PROVIDER"
        value = var.embedding_provider
      }
      env {
        name  = "SPECTOR_EMBEDDING_MODEL"
        value = var.embedding_model
      }
      env {
        name  = "SPECTOR_EMBEDDING_BASE_URL"
        value = var.embedding_base_url
      }
      env {
        name  = "SPECTOR_EMBEDDING_API_KEY"
        value = var.embedding_api_key
      }
      env {
        name  = "SPECTOR_GENERATION_PROVIDER"
        value = var.generation_provider
      }
      env {
        name  = "SPECTOR_GENERATION_MODEL"
        value = var.generation_model
      }
      env {
        name  = "SPECTOR_GENERATION_BASE_URL"
        value = var.generation_base_url
      }
      env {
        name  = "SPECTOR_GENERATION_API_KEY"
        value = var.generation_api_key
      }
      env {
        name  = "SPECTOR_MEMORY_CAPACITY"
        value = tostring(var.memory_capacity)
      }
      env {
        name  = "SPECTOR_ENTITY_EXTRACTION_MODE"
        value = var.entity_extraction_mode
      }
      env {
        name  = "SPECTOR_TAG_EXTRACTOR"
        value = var.tag_extractor
      }
      env {
        name  = "SPECTOR_TEXT_SEARCH_MODE"
        value = var.text_search_mode
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
