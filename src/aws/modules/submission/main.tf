locals {
  identifier_prefix = "${terraform.workspace}-${var.name}-sub"
}

module "submission_role" {
  source = "../../libraries/iam_submission_lambda"
  name   = local.identifier_prefix
}

module "submission_store" {
  source                   = "../../libraries/submission_s3"
  name                     = var.name
  service                  = "submission"
  logs_bucket_id           = var.logs_bucket_id
  force_destroy_s3_buckets = var.force_destroy_s3_buckets
  replication_enabled      = var.replication_enabled
}

module "submission_lambda" {
  source                       = "../../libraries/java_lambda"
  lambda_function_name         = local.identifier_prefix
  lambda_repository_bucket     = var.lambda_repository_bucket
  lambda_object_key            = var.lambda_object_key
  lambda_handler_class         = var.lambda_handler_class
  lambda_execution_role_arn    = module.submission_role.arn
  lambda_timeout               = 20
  lambda_memory                = 1024
  lambda_environment_variables = merge({ SUBMISSION_STORE = module.submission_store.bucket_name }, var.lambda_environment_variables)
  app_alarms_topic             = var.alarm_topic_arn
  publish                      = var.provisioned_concurrent_executions != 0 ? true : false
}

resource "aws_lambda_provisioned_concurrency_config" "submission_lambda_provisioned_concurrency" {
  count                             = var.provisioned_concurrent_executions != 0 ? 1 : 0
  function_name                     = module.submission_lambda.lambda_function_name
  provisioned_concurrent_executions = var.provisioned_concurrent_executions
  qualifier                         = module.submission_lambda.version
}

module "submission_gateway" {
  source                  = "../../libraries/submission_api_gateway"
  name                    = var.name
  lambda_function_arn     = "${module.submission_lambda.lambda_function_arn}${var.provisioned_concurrent_executions != 0 ? format("%s%s", ":", module.submission_lambda.version) : ""}"
  lambda_function_name    = module.submission_lambda.lambda_function_name
  burst_limit             = var.burst_limit
  rate_limit              = var.rate_limit
  lambda_function_version = var.provisioned_concurrent_executions != 0 ? (can(tonumber(module.submission_lambda.version)) ? module.submission_lambda.version : 0) : 0
}
