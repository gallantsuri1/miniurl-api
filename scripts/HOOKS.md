# Git Hooks

This directory contains git hooks to enforce repository policies.

## Available Hooks

### pre-push
Prevents direct pushes to protected branches (`main` and `master`).
All changes must go through pull requests.

## Installation

To install the git hooks, run the following command from the repository root:

```bash
./scripts/install-hooks.sh
```

Or manually copy the hooks:

```bash
cp scripts/pre-push .git/hooks/pre-push
chmod +x .git/hooks/pre-push
```

## Verification

To verify the hooks are installed:

```bash
ls -la .git/hooks/pre-push
```

## What happens if I try to push directly to main?

If you try to push directly to `main` or `master`, you'll see an error like this:

```
❌ ERROR: Direct push to 'main' branch is not allowed!

Please follow these steps:
  1. Create a new branch: git checkout -b feature/your-feature
  2. Commit your changes: git commit -m 'Your message'
  3. Push your branch: git push origin feature/your-feature
  4. Create a Pull Request on GitHub
```

## Bypassing the hook (Emergency only!)

In rare cases, you may need to bypass the hook (e.g., for emergency fixes).
Use the `--no-verify` flag:

```bash
git push --no-verify origin main
```

⚠️ **Warning**: Only use this in emergencies. Bypassing hooks undermines the review process.
