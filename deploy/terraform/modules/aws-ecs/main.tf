resource "aws_cloudwatch_log_group" "spector" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "spector" {
  family                   = var.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  volume {
    name = "spector-data"
    efs_volume_configuration {
      file_system_id     = var.efs_file_system_id
      transit_encryption = "ENABLED"
      authorization_config {
        access_point_id = var.efs_access_point_id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([
    {
      name      = "spector"
      image     = var.image
      essential = true
      portMappings = [
        {
          containerPort = 7700
          hostPort      = 7700
          protocol      = "tcp"
        },
        {
          containerPort = 7070
          hostPort      = 7070
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "SPECTOR_PORT", value = "7070" },
        { name = "SPECTOR_NODE_ID", value = "${var.name}-aws" },
        { name = "SPECTOR_EMBEDDING_DIMS", value = tostring(var.dimensions) },
        { name = "SPECTOR_EMBEDDING_PROVIDER", value = var.embedding_provider },
        { name = "SPECTOR_EMBEDDING_MODEL", value = var.embedding_model },
        { name = "SPECTOR_EMBEDDING_BASE_URL", value = var.embedding_base_url },
        { name = "SPECTOR_EMBEDDING_API_KEY", value = var.embedding_api_key },
        { name = "SPECTOR_GENERATION_PROVIDER", value = var.generation_provider },
        { name = "SPECTOR_GENERATION_MODEL", value = var.generation_model },
        { name = "SPECTOR_GENERATION_BASE_URL", value = var.generation_base_url },
        { name = "SPECTOR_GENERATION_API_KEY", value = var.generation_api_key },
        { name = "SPECTOR_MEMORY_CAPACITY", value = tostring(var.memory_capacity) },
        { name = "SPECTOR_ENTITY_EXTRACTION_MODE", value = var.entity_extraction_mode },
        { name = "SPECTOR_TAG_EXTRACTOR", value = var.tag_extractor },
        { name = "SPECTOR_TEXT_SEARCH_MODE", value = var.text_search_mode }
      ]
      mountPoints = [
        {
          sourceVolume  = "spector-data"
          containerPath = "/data"
          readOnly      = false
        }
      ]
      ulimits = [
        {
          name      = "nofile"
          softLimit = var.nofile_soft_limit
          hardLimit = var.nofile_hard_limit
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.spector.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "spector"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "spector" {
  name            = var.name
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.spector.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.subnets
    security_groups  = var.security_groups
    assign_public_ip = var.assign_public_ip
  }

  dynamic "load_balancer" {
    for_each = var.target_group_arn != "" ? [1] : []
    content {
      target_group_arn = var.target_group_arn
      container_name   = "spector"
      container_port   = 7700
    }
  }
}
