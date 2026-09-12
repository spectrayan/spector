---
title: Terraform Multi-Cloud Deployment
description: "Deploy Spector Cognitive Memory across AWS, GCP, and Azure using reusable Terraform modules."
---

# ☁️ Terraform Multi-Cloud Deployment (Experimental)

> **Infrastructure-as-code reference modules for AWS ECS, GCP Cloud Run, and Azure Container Apps.**

> [!WARNING]
> **Experimental / Reference Only**: These Terraform modules are provided as architectural references and have not been production-applied. For production setups, prefer the official [Docker Compose](docker.md) or [Kubernetes Helm Chart](helm.md).

Spector includes modular Terraform packages located in [`deploy/terraform/modules/`](https://github.com/spectrayan/spector/tree/main/deploy/terraform/modules) that illustrate container execution with persistent cloud storage mounts.

---

## Supported Cloud Platforms

| Cloud Provider | Service Architecture | Storage Mechanism | Module Path |
|:---|:---|:---|:---|
| **Amazon Web Services (AWS)** | ECS Fargate + ALB | Amazon EFS (Elastic File System) | `deploy/terraform/modules/aws-ecs` |
| **Google Cloud Platform (GCP)** | Cloud Run v2 (Direct VPC) | Google Cloud Storage (GCS) FUSE volume | `deploy/terraform/modules/gcp-cloudrun` |
| **Microsoft Azure** | Azure Container Apps (ACA) | Azure Files (SMB Share) | `deploy/terraform/modules/azure-aca` |

---

## 1. AWS ECS Fargate Module

Provisions an ECS Fargate service and task definition mounted to Amazon EFS:

```hcl
module "spector_aws" {
  source = "github.com/spectrayan/spector//deploy/terraform/modules/aws-ecs"

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
  embedding_api_key   = var.openai_api_key
  dimensions          = 1536
  generation_provider = "openai"
  generation_model    = "gpt-4o-mini"
  generation_api_key  = var.openai_api_key
}

output "spector_endpoint" {
  value = module.spector_aws.service_url
}
```

---

## 2. GCP Cloud Run Module

Provisions a Cloud Run v2 service mounting a persistent Cloud Storage bucket via GCS FUSE:

```hcl
module "spector_gcp" {
  source = "github.com/spectrayan/spector//deploy/terraform/modules/gcp-cloudrun"

  name            = "spector-prod"
  region          = "us-central1"
  gcs_bucket_name = google_storage_bucket.spector_data.name
}

output "spector_url" {
  value = module.spector_gcp.service_url
}
```

---

## 3. Azure Container Apps Module

Provisions an Azure Container App mounting Azure Files storage:

```hcl
module "spector_azure" {
  source = "github.com/spectrayan/spector//deploy/terraform/modules/azure-aca"

  name                         = "spector-prod"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  storage_name                 = "spectorfiles"
}

output "spector_fqdn" {
  value = module.spector_azure.fqdn
}
```

---

## Execution Workflow

```bash
cd deploy/terraform
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```
