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

variable "external_ingress" {
  description = "Enable public external ingress"
  type        = bool
  default     = true
}
