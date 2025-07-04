#!/bin/bash

# Setup script for SOPS encryption with Withings secrets

set -e

echo "🔐 Setting up SOPS encryption for Withings secrets"
echo

# Check if SOPS is installed
if ! command -v sops &> /dev/null; then
    echo "❌ SOPS is not installed. Please install it first:"
    echo "   brew install sops"
    echo "   or download from: https://github.com/mozilla/sops/releases"
    exit 1
fi

echo "✅ SOPS is installed"

# Check if secrets.yaml exists
if [ ! -f "secrets.yaml" ]; then
    echo "❌ secrets.yaml not found. Please create it first with your credentials."
    exit 1
fi

echo "✅ secrets.yaml found"

# Check if .sops.yaml exists
if [ ! -f ".sops.yaml" ]; then
    echo "❌ .sops.yaml not found. Please configure encryption method."
    exit 1
fi

echo "✅ .sops.yaml found"

# Check if secrets.yaml is already encrypted
if sops --decrypt secrets.yaml &> /dev/null; then
    echo "✅ secrets.yaml is already encrypted"
else
    echo "🔒 Encrypting secrets.yaml..."
    
    # Create backup
    cp secrets.yaml secrets.yaml.backup
    
    # Encrypt in place
    sops -e -i secrets.yaml
    
    echo "✅ secrets.yaml encrypted successfully"
    echo "📄 Backup saved as secrets.yaml.backup"
fi

# Test decryption
echo "🧪 Testing decryption..."
if bb sops-helper.clj check; then
    echo "✅ SOPS setup complete!"
    echo
    echo "Next steps:"
    echo "1. Run: bb setup"
    echo "2. Run: bb weight"
else
    echo "❌ Decryption test failed"
    exit 1
fi