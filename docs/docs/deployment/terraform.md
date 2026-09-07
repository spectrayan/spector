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

Provisions an ECS Fargate cluster, task definition, and Amazon EFS filesystem for persistent memory storage:

```hcl
module "spector_aws" {
  source = "github.com/spectrayan/spector//deploy/terraform/modules/aws-ecs"

  environment        = "production"
  vpc_id             = "vpc-0123456789abcdef0"
  subnet_ids         = ["subnet-0123", "subnet-0456"]
  cpu                = 2048   # 2 vCPU
  memory             = 4096   # 4 GB RAM
  image              = "ghcr.io/spectrayan/spector:latest"
  storage_size_gb    = 50
  enable_efs_backup  = true
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

  project_id  = "my-gcp-project"
  region      = "us-central1"
  service_name = "spector-prod"
  image       = "ghcr.io/spectrayan/spector:latest"
  memory      = "4Gi"
  cpu         = "2"
  bucket_name = "spector-persistent-storage-prod"
}

output "spector_url" {
  value = module.spector_gcp.service_url
}
```

---

## 3. Azure Container Apps Module

Provisions an Azure Container Apps environment with an Azure Storage account and file share:

```hcl
module "spector_azure" {
  source = "github.com/spectrayan/spector//deploy/terraform/modules/azure-aca"

  resource_group_name = "rg-spector-prod"
  location            = "eastus"
  app_name            = "spector-prod"
  image               = "ghcr.io/spectrayan/spector:latest"
  cpu                 = 2.0
  memory              = "4.0Gi"
  storage_share_name  = "spector-share"
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
