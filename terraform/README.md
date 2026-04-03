# Terraform GitHub Rulesets

This directory contains Terraform configuration for managing GitHub repository rulesets.

## Prerequisites

1. **GitHub Personal Access Token** with `repo` scope permissions
2. **Terraform** installed locally

## Setup

1. Create a Terraform variables file:

```bash
cp terraform.tfvars.example terraform.tfvars
```

2. Edit `terraform.tfvars` and add your GitHub token:

```hcl
github_token = "ghp_your_token_here"
```

3. Initialize and apply:

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

## What This Configures

- **Branch**: `main`
- **Required Approvals**: 1 minimum
- **Code Owner Review**: Required
- **Dismiss Stale Reviews**: Enabled (reviews dismissed if new code is pushed)

## Verification

After applying, verify in GitHub:
- Go to **Settings** → **Rules** → **Rulesets**
- You should see `main-branch-protection` ruleset active
