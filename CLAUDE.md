# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Withings weight tracker built with Babashka (Clojure) scripts that fetches weight data via OAuth2 authentication and uploads it to intervals.icu. The project emphasizes security through SOPS encryption and provides a reproducible development environment with Nix.

## Development Environment Setup

**Recommended Workflow:**
```bash
# Enter Nix development shell (provides all dependencies)
nix develop

# Or use direnv for automatic environment loading
direnv allow
```

The Nix flake (`flake.nix`) provides all necessary dependencies including Babashka, SOPS, Age encryption, and development tools. It also creates helpful wrapper scripts.

## Core Architecture

### OAuth Flow Implementation
- **`oauth.clj`** - Complete OAuth2 flow with automatic token refresh (5-minute expiration buffer)
- **Token storage** - `~/.withings-config.json` for persistent OAuth tokens
- **SOPS integration** - Reads encrypted client credentials from `secrets.yaml`
- **Fallback support** - Can use command-line credentials if SOPS unavailable

### Weight Fetching and Upload
- **`get-weight-oauth.clj`** - Main script with automatic OAuth token management
- **`get-weight.clj`** - Simple version requiring manual token input
- **Withings API integration** - Withings REST API with proper error handling and unit conversion
- **intervals.icu integration** - Upload weight data to intervals.icu via API with Basic authentication

### Security Architecture
- **SOPS encryption** - All secrets encrypted at rest using Age encryption
- **Key management** - Age key stored in `key.txt`, referenced in `.sops.yaml`
- **Automatic decryption** - Scripts transparently decrypt secrets when needed
- **No plaintext secrets** - Credentials never stored unencrypted on disk

## Common Development Commands

**Environment:**
```bash
nix develop                    # Enter development shell
setup-oauth                   # Setup OAuth (uses SOPS secrets)
get-weight                    # Fetch current weight
check-sops                    # Validate SOPS configuration
decrypt-secrets               # View secrets (redacted output)
```

**Weight tracking:**
```bash
bb weight                     # Fetch current weight from Withings
bb push-to-intervals          # Push weight to intervals.icu (reads from stdin)
bb weight | bb push-to-intervals  # Complete workflow: fetch and upload
```

**Direct script execution:**
```bash
bb oauth.clj --setup          # OAuth setup with optional CLI credentials
bb get-weight-oauth.clj       # Fetch weight with automatic token refresh
bb sops-helper.clj check      # Check SOPS availability
```

**Nix package management:**
```bash
nix build                     # Build installable package
nix run .#get-weight          # Run without entering shell
nix run .#oauth -- --help     # Run OAuth script with help
```

## Secrets Management

**Initial setup:**
1. Edit `secrets.yaml` with Withings client credentials
2. Encrypt: `sops -e -i secrets.yaml`
3. Age key should be in `key.txt` (referenced by `.sops.yaml`)

**Key locations:**
- **`secrets.yaml`** - Encrypted secrets containing:
  - `withings` section: client_id, client_secret, redirect_uri
  - `intervals` section: api_key, athlete_id
- **`key.txt`** - Age encryption key (not committed to git)
- **`.sops.yaml`** - SOPS configuration with encryption rules

## API Integration Details

**Withings API endpoints:**
- Authorization: `https://account.withings.com/oauth2_user/authorize2`
- Token exchange: `https://wbsapi.withings.net/v2/oauth2`
- Measurements: `https://wbsapi.withings.net/measure`

**intervals.icu API endpoints:**
- Wellness data upload: `https://intervals.icu/api/v1/athlete/{athlete_id}/wellness-bulk`
- Authentication: Basic auth with username "API_KEY" and password as API key
- Documentation: `https://intervals.icu/api-docs.html`

**Data flow:**
1. OAuth script decrypts SOPS secrets → generates auth URL → exchanges code for tokens
2. Token stored in `~/.withings-config.json` with automatic refresh logic
3. Weight script reads token → fetches measurements → calculates actual values (value × 10^unit)
4. Upload script reads weight data → sends to intervals.icu via Basic auth with API key

## Dependencies and Tools

**Runtime:** Babashka with `babashka.http-client`, `cheshire.core`, `clj-yaml.core`, `babashka.cli`
**Security:** SOPS, Age encryption
**Development:** Nix flakes, direnv, clj-kondo (linting)

## Key Implementation Notes

**Withings Integration:**
- **Token refresh** - Automatic with 5-minute buffer before expiration
- **Error handling** - Comprehensive error messages for network/API failures  
- **Unit conversion** - Weight values require calculation: `value * Math.pow(10, unit)`
- **Measurement types** - Uses meastype=1 for weight, category=1 for real measurements
- **Scope** - OAuth scope is "user.metrics" for weight data access

**intervals.icu Integration:**
- **Authentication** - Basic auth with username "API_KEY" and API key as password
- **Data format** - JSON array with date (YYYY-MM-DD) and weight in kg
- **API endpoint** - Uses athlete-specific endpoint with athlete_id from secrets
- **Error handling** - Proper HTTP status code handling and error reporting

## Development Patterns

When modifying OAuth flow, ensure token refresh logic maintains the 5-minute expiration buffer. When adding new API endpoints, follow the existing pattern of automatic SOPS decryption with command-line fallback. All new secrets should be added to `secrets.yaml` and encrypted with SOPS rather than hardcoded.