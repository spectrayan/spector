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
