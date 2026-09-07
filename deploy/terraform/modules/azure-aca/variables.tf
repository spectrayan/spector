variable "name" {
  description = "Container App name"
  type        = string
  default     = "spector"
}

variable "resource_group_name" {
  description = "Azure Resource Group name"
  type        = string
}

variable "container_app_environment_id" {
  description = "Container App Environment ID"
  type        = string
}

variable "storage_name" {
  description = "Storage mount name registered in Container App Environment"
  type        = string
}

variable "image" {
  description = "Docker image URI"
  type        = string
  default     = "ghcr.io/spectrayan/spector:latest"
}

variable "cpu" {
  description = "Allocated CPU cores (e.g. 1.0, 2.0)"
  type        = number
  default     = 1.0
}

variable "memory" {
  description = "Allocated memory (e.g. 2Gi)"
  type        = string
  default     = "2Gi"
}

variable "dimensions" {
  description = "Vector dimensions"
  type        = number
  default     = 384
}

variable "external_ingress" {
  description = "Enable public external ingress"
  type        = bool
  default     = true
}
