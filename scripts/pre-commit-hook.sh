#!/bin/bash
# Pre-commit hook to run clj-kondo linting
# 
# To install:
# 1. Copy this to .git/hooks/pre-commit
# 2. Make executable: chmod +x .git/hooks/pre-commit

echo "Running clj-kondo linting..."

# Get list of staged Clojure files
staged_files=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(clj|cljs|cljc)$')

if [ -z "$staged_files" ]; then
    echo "No Clojure files to lint."
    exit 0
fi

# Run clj-kondo on staged files
clj-kondo --lint $staged_files

if [ $? -ne 0 ]; then
    echo "❌ clj-kondo found linting issues. Please fix them before committing."
    echo "💡 Tip: Run 'clj-kondo --lint src/' to see all issues"
    exit 1
fi

echo "✅ clj-kondo linting passed!"
exit 0
