variable "name" {
  description = "Application name"
  type        = string
  default     = "spector"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "cluster_id" {
  description = "ECS Cluster ID"
  type        = string
}

variable "subnets" {
  description = "Subnet IDs for ECS task"
  type        = list(string)
}

variable "security_groups" {
  description = "Security group IDs for ECS task"
  type        = list(string)
  default     = []
}

variable "assign_public_ip" {
  description = "Assign public IP to task"
  type        = bool
  default     = false
}

variable "image" {
  description = "Docker image URI"
  type        = string
  default     = "ghcr.io/spectrayan/spector:latest"
}

variable "cpu" {
  description = "CPU units (1024 = 1 vCPU)"
  type        = string
  default     = "1024"
}

variable "memory" {
  description = "Memory in MB"
  type        = string
  default     = "2048"
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

variable "execution_role_arn" {
  description = "IAM role ARN for ECS task execution"
  type        = string
}

variable "task_role_arn" {
  description = "IAM role ARN for ECS task"
  type        = string
}

variable "efs_file_system_id" {
  description = "EFS File System ID for persistent memory storage"
  type        = string
}

variable "efs_access_point_id" {
  description = "EFS Access Point ID"
  type        = string
}

variable "target_group_arn" {
  description = "Optional ALB Target Group ARN"
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

variable "nofile_soft_limit" {
  description = "Soft limit for open file descriptors (nofile) required for Project Panama FFM off-heap mmap and socket handles"
  type        = number
  default     = 65536
}

variable "nofile_hard_limit" {
  description = "Hard limit for open file descriptors (nofile) required for Project Panama FFM off-heap mmap and socket handles"
  type        = number
  default     = 65536
}
