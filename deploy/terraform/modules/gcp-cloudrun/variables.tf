variable "name" {
  description = "Cloud Run service name"
  type        = string
  default     = "spector"
}

variable "region" {
  description = "GCP region"
  type        = string
}

variable "image" {
  description = "Docker image URI"
  type        = string
  default     = "ghcr.io/spectrayan/spector:latest"
}

variable "cpu" {
  description = "CPU allocation"
  type        = string
  default     = "2"
}

variable "memory" {
  description = "Memory allocation"
  type        = string
  default     = "2Gi"
}

variable "dimensions" {
  description = "Vector dimensions"
  type        = number
  default     = 384
}

variable "gcs_bucket_name" {
  description = "GCS bucket name mounted to /data"
  type        = string
}

variable "ingress" {
  description = "Ingress traffic settings (INGRESS_TRAFFIC_ALL or INGRESS_TRAFFIC_INTERNAL_ONLY)"
  type        = string
  default     = "INGRESS_TRAFFIC_ALL"
}

variable "min_instances" {
  description = "Minimum instance count"
  type        = number
  default     = 1
}

variable "max_instances" {
  description = "Maximum instance count"
  type        = number
  default     = 3
}
