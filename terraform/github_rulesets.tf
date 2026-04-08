# GitHub Repository Ruleset for main branch
# Requires at least 1 approval from code owners for merge to main

terraform {
  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }
}

provider "github" {
  token = var.github_token
}

variable "github_token" {
  description = "GitHub Personal Access Token with repo permissions"
  type        = string
  sensitive   = true
}

variable "repository_name" {
  description = "Repository name (without owner prefix)"
  type        = string
  default     = "miniurl"
}

# Ruleset for main branch protection
resource "github_repository_ruleset" "main_branch_protection" {
  name        = "main-branch-protection"
  target      = "branch"
  repository  = var.repository_name

  # Apply to main branch
  branch_name_pattern = "^main$"

  # Only apply to push operations
  conditions {
    ref_name {
      include = ["~DEFAULT_BRANCH"]
    }
  }

  # Require pull request before merging
  rules {
    pull_request {
      required_approving_review_count   = 1
      require_code_owner_review         = true
      dismiss_stale_reviews_on_push     = true
      require_last_push_approval        = false
    }
  }

  # Ensure ruleset cannot be bypassed (except by repo admins if needed)
  enforcement = "active"
}
