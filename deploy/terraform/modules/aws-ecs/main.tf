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
        { name = "SPECTOR_DIMS", value = tostring(var.dimensions) }
      ]
      mountPoints = [
        {
          sourceVolume  = "spector-data"
          containerPath = "/data"
          readOnly      = false
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
