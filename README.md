# Withings Weight Tracker

Babashka scripts for fetching weight data from Withings API with OAuth2 authentication.

## Setup

### Option 1: Using Nix (Recommended)

1. **Install Nix** with flakes enabled:
   ```bash
   # Install Nix
   curl -L https://nixos.org/nix/install | sh
   
   # Enable flakes (add to ~/.config/nix/nix.conf)
   experimental-features = nix-command flakes
   ```

2. **Enter development shell**:
   ```bash
   nix develop
   
   # Or with direnv (recommended)
   echo "use flake" > .envrc
   direnv allow
   ```

3. **Setup encryption** (choose one):
   ```bash
   # Age encryption (simpler)
   age-keygen -o key.txt
   export SOPS_AGE_KEY_FILE=./key.txt
   
   # GPG encryption (more secure)
   gpg --generate-key
   # Update .sops.yaml with your fingerprint
   ```

4. **Configure secrets**:
   ```bash
   # Edit secrets.yaml with your credentials
   nano secrets.yaml
   
   # Encrypt
   sops -e -i secrets.yaml
   ```

5. **Setup OAuth**:
   ```bash
   setup-oauth    # or: bb oauth.clj --setup
   ```

6. **Get your weight**:
   ```bash
   get-weight     # or: bb get-weight-oauth.clj
   ```

### Option 2: Manual Setup

1. **Install SOPS** (optional but recommended):
   ```bash
   # macOS
   brew install sops
   
   # Or download from: https://github.com/mozilla/sops/releases
   ```

2. **Register your application** at https://developer.withings.com/
   - Create a new application
   - Note down your Client ID and Client Secret
   - Set redirect URI to `http://localhost:8080/callback`

3. **Configure secrets with SOPS** (recommended):
   ```bash
   # Edit secrets.yaml with your credentials
   nano secrets.yaml
   
   # Initialize SOPS encryption (choose one method):
   # For Age encryption:
   age-keygen -o key.txt
   export SOPS_AGE_KEY_FILE=key.txt
   
   # For GPG encryption:
   gpg --generate-key
   # Update .sops.yaml with your PGP fingerprint
   
   # Encrypt the secrets file
   sops -e -i secrets.yaml
   ```

4. **Configure OAuth2 authentication**:
   ```bash
   # Using SOPS secrets (recommended)
   bb oauth.clj --setup
   
   # Or provide credentials directly
   bb oauth.clj --setup --client-id YOUR_CLIENT_ID --client-secret YOUR_CLIENT_SECRET
   ```
   
   This will:
   - Open a browser for authorization
   - Prompt you to enter the authorization code
   - Save tokens to `~/.withings-config.json`

## Usage

### Get Current Weight

```bash
# With Nix development shell
get-weight

# Or directly with Babashka
bb get-weight-oauth.clj

# Or provide token directly
bb get-weight-oauth.clj --access-token YOUR_TOKEN
```

### OAuth Management

```bash
# With Nix development shell
setup-oauth

# Or directly with Babashka
bb oauth.clj --setup

# Setup OAuth (with credentials)
bb oauth.clj --setup --client-id YOUR_ID --client-secret YOUR_SECRET

# Test current token
bb oauth.clj --test-token

# Get help
bb oauth.clj --help
```

### SOPS Management

```bash
# Check if SOPS is available
check-sops         # or: bb sops-helper.clj check

# Decrypt and show secrets (redacted)
decrypt-secrets    # or: bb sops-helper.clj decrypt

# Edit encrypted secrets
sops secrets.yaml
```

### Nix Commands

```bash
# Enter development shell
nix develop

# Run without entering shell
nix run .#get-weight
nix run .#oauth -- --help

# Build package
nix build

# Install package
nix profile install .
```

## Files

- `oauth.clj` - OAuth2 authentication flow and token management
- `get-weight-oauth.clj` - Main script with OAuth integration
- `get-weight.clj` - Simple script requiring manual token input
- `sops-helper.clj` - SOPS encryption helper utilities
- `secrets.yaml` - Encrypted secrets file (SOPS)
- `.sops.yaml` - SOPS configuration
- `flake.nix` - Nix flake for development environment
- `.envrc` - Direnv configuration
- `.gitignore` - Git ignore patterns
- `~/.withings-config.json` - OAuth configuration and tokens (auto-created)

## Features

- ✅ OAuth2 authentication flow
- ✅ Automatic token refresh
- ✅ Secure token storage
- ✅ SOPS encryption for secrets
- ✅ Nix flake for reproducible development
- ✅ Weight data fetching
- ✅ Date formatting
- ✅ Error handling

## Token Management

The OAuth system automatically:
- Refreshes expired tokens (with 5-minute buffer)
- Stores tokens securely in your home directory
- Handles token validation and renewal

## Example Output

```
Latest weight: 75.50 kg
Measured on: 2024-01-15T08:30:00Z
```

## API Endpoints

- Authorization: `https://account.withings.com/oauth2_user/authorize2`
- Token: `https://wbsapi.withings.net/v2/oauth2`
- Measurements: `https://wbsapi.withings.net/measure`