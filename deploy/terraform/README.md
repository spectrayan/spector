# Spector Terraform Cloud Infrastructure Modules

This directory contains reusable Terraform modules to deploy Spector Memory as a containerized, persistent service across the major cloud providers:

- **AWS ECS Fargate** (`modules/aws-ecs`): Serverless container task mounted to persistent AWS EFS storage.
- **Google Cloud Run** (`modules/gcp-cloudrun`): Cloud Run v2 service with GCS / Filestore volume persistence.
- **Azure Container Apps** (`modules/azure-aca`): Fully managed serverless containers with Azure Files storage mounts.

---

## AWS Quick Example

```hcl
module "spector" {
  source = "./modules/aws-ecs"

  name                = "spector-prod"
  aws_region          = "us-east-1"
  cluster_id          = aws_ecs_cluster.main.id
  subnets             = module.vpc.private_subnets
  security_groups     = [aws_security_group.spector.id]
  execution_role_arn  = aws_iam_role.ecs_execution.arn
  task_role_arn       = aws_iam_role.ecs_task.arn
  efs_file_system_id  = aws_efs_file_system.spector.id
  efs_access_point_id = aws_efs_access_point.spector.id

  # Provider configuration (optional — defaults to Ollama)
  embedding_provider  = "openai"
  embedding_model     = "text-embedding-3-small"
  embedding_api_key   = var.openai_api_key  # sensitive
  dimensions          = 1536
  generation_provider = "openai"
  generation_model    = "gpt-4o-mini"
  generation_api_key  = var.openai_api_key
}
```

---

## Google Cloud Run Quick Example

```hcl
module "spector" {
  source = "./modules/gcp-cloudrun"

  name            = "spector-prod"
  region          = "us-central1"
  gcs_bucket_name = google_storage_bucket.spector_data.name
}
```

---

## Azure Container Apps Quick Example

```hcl
module "spector" {
  source = "./modules/azure-aca"

  name                         = "spector-prod"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  storage_name                 = "spectorfiles"
}
```
