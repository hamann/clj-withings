# Withings Weight Tracker

Babashka scripts for fetching weight data from Withings API with OAuth2 authentication and uploading to intervals.icu.

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
   # Copy example file and edit with your credentials
   cp secrets.yaml.example secrets.yaml
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
   get-weight     # or: bb weight
   ```

7. **Upload to intervals.icu**:
   ```bash
   # Complete workflow: fetch and upload
   bb weight | bb push-to-intervals
   
   # Or manually: echo "75.1 kg" | bb push-to-intervals
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
   - Set redirect URI to `http://localhost/callback`

3. **Get intervals.icu API key**:
   - Go to your intervals.icu account settings
   - Look for "Developer Settings" near the bottom
   - Generate an API key with wellness data write permissions
   - Note your athlete ID from your profile URL

4. **Configure secrets with SOPS** (recommended):
   ```bash
   # Copy example file and edit with your credentials
   cp secrets.yaml.example secrets.yaml
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

5. **Configure OAuth2 authentication**:
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
bb weight

# Or provide token directly
bb get-weight-oauth.clj --access-token YOUR_TOKEN
```

### Upload to intervals.icu

```bash
# Complete workflow: fetch and upload
bb weight | bb push-to-intervals

# Manual weight entry
echo "75.1 kg" | bb push-to-intervals
echo "165.5 lbs 2025-01-01" | bb push-to-intervals

# Upload with specific date
echo "75.1 kg $(date '+%Y-%m-%d')" | bb push-to-intervals
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

**Core Scripts:**
- `oauth.clj` - OAuth2 authentication flow and token management
- `get-weight-oauth.clj` - Main script with OAuth integration
- `get-weight.clj` - Simple script requiring manual token input

**intervals.icu Integration:**
- `src/intervals/config.clj` - Configuration management for intervals.icu
- `src/intervals/api.clj` - API integration for uploading wellness data

**Configuration:**
- `secrets.yaml.example` - Example configuration file with placeholder values
- `secrets.yaml` - Encrypted secrets file (SOPS) with Withings and intervals.icu credentials (not in git)
- `.sops.yaml` - SOPS configuration
- `bb.edn` - Babashka task definitions including `weight` and `push-to-intervals`
- `~/.withings-config.json` - OAuth configuration and tokens (auto-created)

**Development:**
- `flake.nix` - Nix flake for development environment
- `.envrc` - Direnv configuration
- `.gitignore` - Git ignore patterns

## Features

**Withings Integration:**
- ✅ OAuth2 authentication flow
- ✅ Automatic token refresh
- ✅ Secure token storage
- ✅ Weight data fetching
- ✅ Date formatting
- ✅ Error handling

**intervals.icu Integration:**
- ✅ API key authentication with Basic auth
- ✅ Wellness data upload (weight)
- ✅ Automatic date formatting
- ✅ Support for both kg and lbs input
- ✅ Flexible input formats (piped from `bb weight` or manual entry)

**Security & Development:**
- ✅ SOPS encryption for secrets
- ✅ Nix flake for reproducible development
- ✅ Comprehensive error handling

## Token Management

The OAuth system automatically:
- Refreshes expired tokens (with 5-minute buffer)
- Stores tokens securely in your home directory
- Handles token validation and renewal

## Example Output

**Weight fetching:**
```bash
$ bb weight
{:weight 75.11 kg, :date 2025-07-01T10:13:53Z}
```

**Upload to intervals.icu:**
```bash
$ bb weight | bb push-to-intervals
Weight uploaded successfully

$ echo "75.5 kg" | bb push-to-intervals
Weight uploaded successfully
```

## API Endpoints

**Withings API:**
- Authorization: `https://account.withings.com/oauth2_user/authorize2`
- Token: `https://wbsapi.withings.net/v2/oauth2`
- Measurements: `https://wbsapi.withings.net/measure`

**intervals.icu API:**
- Wellness data upload: `https://intervals.icu/api/v1/athlete/{athlete_id}/wellness-bulk`
- Authentication: Basic auth with username "API_KEY" and password as API key
- Documentation: `https://intervals.icu/api-docs.html`
