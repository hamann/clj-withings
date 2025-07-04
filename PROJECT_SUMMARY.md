# Project Summary: clj-withings

## Overview

The `clj-withings` project is a Clojure/Babashka application that integrates with the Withings API to fetch weight measurements and optionally uploads them to intervals.icu for wellness tracking. It provides a complete OAuth2 authentication flow, secure credential management using SOPS encryption, and a command-line interface for interacting with both services.

## Key Components

### Core Namespaces

#### Withings Integration
- **`src/withings/oauth.clj`** - OAuth2 authentication flow, token management, and refresh logic
- **`src/withings/api.clj`** - Withings API client for fetching weight measurements
- **`src/withings/config.clj`** - Configuration management with SOPS integration
- **`src/withings/main.clj`** - CLI entry point with command definitions

#### intervals.icu Integration  
- **`src/intervals/api.clj`** - intervals.icu API client for uploading wellness data
- **`src/intervals/config.clj`** - Configuration management for intervals.icu credentials

### Configuration Files

#### Project Configuration
- **`bb.edn`** - Babashka task definitions and project dependencies
- **`flake.nix`** - Nix development environment configuration
- **`.envrc`** - Direnv configuration for automatic environment loading

#### Security & Secrets
- **`secrets.yaml.example`** - Template for credentials configuration
- **`secrets.yaml`** - Encrypted credentials file (SOPS, not in git)
- **`.sops.yaml`** - SOPS encryption configuration with Age/PGP keys
- **`~/.withings-config.json`** - OAuth tokens storage (auto-created)

## Dependencies

### Core Dependencies (bb.edn)
- **`cheshire/cheshire 5.12.0`** - JSON parsing and generation
- **`clj-commons/clj-yaml 1.0.27`** - YAML file processing
- **`babashka.http-client`** - HTTP client for API calls
- **`babashka.cli`** - Command-line argument parsing

### Development Dependencies (flake.nix)
- **`babashka`** - Clojure scripting runtime
- **`sops`** - Secrets encryption/decryption
- **`age`** - Encryption backend for SOPS
- **`direnv`** - Environment management

## Available Commands (bb.edn tasks)

### Authentication & Setup
```bash
bb setup                    # OAuth2 setup with Withings
bb test-token              # Validate current OAuth token
bb check-sops              # Verify SOPS configuration
```

### Weight Management
```bash
bb weight                  # Fetch current weight from Withings
bb push-to-intervals       # Upload weight to intervals.icu (from stdin)
```

### Development
```bash
bb nrepl                   # Start nREPL server on port 7888
```

### Example Workflows
```bash
# Complete workflow: fetch and upload
bb weight | bb push-to-intervals

# Manual weight entry
echo "75.1 kg" | bb push-to-intervals
echo "165.5 lbs 2025-01-01" | bb push-to-intervals
```

## Architecture

### Data Flow
1. **Authentication**: OAuth2 flow stores tokens in `~/.withings-config.json`
2. **Weight Fetching**: API calls to Withings using valid OAuth tokens
3. **Data Processing**: Weight data formatted for intervals.icu API
4. **Upload**: Authenticated requests to intervals.icu wellness endpoint

### Security Model
- **SOPS Encryption**: All credentials encrypted at rest using Age/PGP
- **OAuth2 Tokens**: Automatic refresh with 5-minute expiry buffer
- **No Hardcoded Secrets**: All sensitive data managed through SOPS or environment

### Configuration Hierarchy
1. **Command-line arguments** (highest priority)
2. **SOPS-encrypted secrets.yaml**
3. **Default values** in code

## API Integrations

### Withings API
- **Authorization URL**: `https://account.withings.com/oauth2_user/authorize2`
- **Token Exchange**: `https://wbsapi.withings.net/v2/oauth2`
- **Measurements**: `https://wbsapi.withings.net/measure`
- **Authentication**: OAuth2 with automatic token refresh

### intervals.icu API
- **Wellness Upload**: `https://intervals.icu/api/v1/athlete/{athlete_id}/wellness-bulk`
- **Authentication**: HTTP Basic Auth (username: "API_KEY", password: api_key)
- **Data Format**: JSON with weight in kg and ISO date format

## Implementation Patterns

### Error Handling
- Consistent error maps with `:error` keys
- Graceful degradation for missing configuration
- User-friendly error messages with suggested fixes

### Configuration Management
- SOPS integration for secure credential storage
- Fallback mechanisms for missing configurations
- Environment-aware defaults

### OAuth Token Management
- Automatic token refresh with buffer time
- Persistent storage in user home directory
- Validation before API requests

### Data Transformation
- Unit conversion (lbs to kg) for intervals.icu
- Date formatting for API compatibility
- Flexible input parsing for different data sources

## Development Workflow

### Setup
1. **Install Nix** (recommended) or required tools manually
2. **Enter development shell**: `nix develop` or `direnv allow`
3. **Configure secrets**: Copy `secrets.yaml.example` → `secrets.yaml` and encrypt with SOPS
4. **Setup OAuth**: Run `bb setup` to complete authentication flow

### Testing
- **Token validation**: `bb test-token`
- **SOPS verification**: `bb check-sops`
- **API testing**: `bb weight` to test end-to-end flow

### REPL Development
- **Start nREPL**: `bb nrepl` (port 7888)
- **Connect with CIDER**: `(cider-connect "localhost" 7888)`

## Extension Points

### New Data Sources
- Add new measurement types in `withings.api/get-measurements`
- Extend `meastype` parameter handling
- Add support for additional Withings device data

### Additional Upload Targets
- Create new namespace following `intervals.api` pattern
- Implement authentication and data formatting
- Add new tasks to `bb.edn` for integration

### Enhanced Security
- Add support for additional SOPS backends (KMS, GCP KMS)
- Implement credential rotation workflows
- Add audit logging for API operations

### CLI Enhancements
- Add more flexible date range queries
- Implement batch operations
- Add data export/import capabilities

## Development Environment

### Nix Flake Features
- **Reproducible environment** with pinned dependencies
- **Shell integration** with direnv
- **Package building** and installation support
- **Development tools** (SOPS, Age, Babashka) included

### IDE Integration
- **nREPL server** for REPL-driven development
- **LSP support** through .lsp directory
- **Linting** via .clj-kondo configuration

## Security Considerations

### Credential Management
- All secrets encrypted with SOPS using Age encryption
- No credentials stored in source control
- OAuth tokens stored in user home directory only

### API Security
- OAuth2 flow with secure token refresh
- HTTPS for all API communications
- Basic Auth for intervals.icu with API key

### Development Security
- Gitignore patterns prevent credential leakage
- SOPS configuration version controlled for team sharing
- Age public key sharing for team access

This summary provides a comprehensive overview for LLM assistants to understand the project structure, capabilities, and extension points for effective code assistance.
