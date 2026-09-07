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
        name  = "SPECTOR_DIMS"
        value = tostring(var.dimensions)
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
