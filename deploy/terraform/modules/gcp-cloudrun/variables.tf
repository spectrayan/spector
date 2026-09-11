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

variable "embedding_provider" {
  description = "Embedding provider type (ollama, openai, google, anthropic, mistral, azure, bedrock, onnx)"
  type        = string
  default     = "ollama"
}

variable "embedding_model" {
  description = "Embedding model name"
  type        = string
  default     = "nomic-embed-text"
}

variable "embedding_base_url" {
  description = "Embedding provider base URL"
  type        = string
  default     = ""
}

variable "embedding_api_key" {
  description = "Embedding provider API key"
  type        = string
  default     = ""
  sensitive   = true
}

variable "generation_provider" {
  description = "Generation (LLM) provider type"
  type        = string
  default     = "ollama"
}

variable "generation_model" {
  description = "Generation model name"
  type        = string
  default     = "llama3.2"
}

variable "generation_base_url" {
  description = "Generation provider base URL"
  type        = string
  default     = ""
}

variable "generation_api_key" {
  description = "Generation (LLM) provider API key"
  type        = string
  default     = ""
  sensitive   = true
}

variable "entity_extraction_mode" {
  description = "Entity extraction mode (NONE, LLM, DICTIONARY)"
  type        = string
  default     = "NONE"
}

variable "tag_extractor" {
  description = "Tag extractor mode (content, llm, none)"
  type        = string
  default     = "content"
}

variable "text_search_mode" {
  description = "Text search mode (HYBRID, VECTOR_ONLY, BM25_ONLY, FULL_STACK)"
  type        = string
  default     = "HYBRID"
}

variable "memory_capacity" {
  description = "Maximum number of memories"
  type        = number
  default     = 10000
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
