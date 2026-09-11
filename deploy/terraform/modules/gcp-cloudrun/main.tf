resource "google_cloud_run_v2_service" "spector" {
  name     = var.name
  location = var.region
  ingress  = var.ingress

  template {
    containers {
      image = var.image

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
      }

      ports {
        container_port = 80
      }

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
        name       = "spector-data"
        mount_path = "/data"
      }
    }

    volumes {
      name = "spector-data"
      gcs {
        bucket    = var.gcs_bucket_name
        read_only = false
      }
    }

    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }
  }
}
