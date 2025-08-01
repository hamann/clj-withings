# Withings Weight Tracker

Babashka scripts for fetching weight data from Withings API with OAuth2 authentication and uploading to intervals.icu and Strava.

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

3. **Configure secrets**:
   ```bash
   # Create secrets file
   mkdir -p ~/.config/clj-withings
   nano ~/.config/clj-withings/secrets.json
   ```

   Example `secrets.json` content:
   ```json
   {
     "withings": {
       "client_id": "YOUR_WITHINGS_CLIENT_ID",
       "client_secret": "YOUR_WITHINGS_CLIENT_SECRET",
       "redirect_uri": "http://localhost/callback"
     },
     "strava": {
       "client_id": "YOUR_STRAVA_CLIENT_ID",
       "client_secret": "YOUR_STRAVA_CLIENT_SECRET",
       "redirect_uri": "http://localhost/callback"
     },
     "intervals": {
       "api_key": "YOUR_INTERVALS_API_KEY",
       "athlete_id": YOUR_INTERVALS_ATHLETE_ID
     }
   }
   ```

4. **Setup OAuth**:
   ```bash
   # Setup Withings OAuth
   bb setup
   
   # Setup Strava OAuth
   bb setup-strava
   ```

5. **Get your weight**:
   ```bash
   bb weight
   ```

6. **Upload to intervals.icu**:
   ```bash
   # Complete workflow: fetch and upload
   bb weight | bb push-to-intervals
   
   # Or manually: echo "75.1 kg" | bb push-to-intervals
   ```

### Option 2: Manual Setup

1. **Register your application** at https://developer.withings.com/
   - Create a new application
   - Note down your Client ID and Client Secret
   - Set redirect URI to `http://localhost/callback`

2. **Get intervals.icu API key**:
   - Go to your intervals.icu account settings
   - Look for "Developer Settings" near the bottom
   - Generate an API key with wellness data write permissions
   - Note your athlete ID from your profile URL

3. **Get Strava API credentials**:
   - Register your application at https://developers.strava.com/
   - Create a new API application
   - Note down your Client ID and Client Secret
   - Set authorization callback domain to `localhost`

4. **Configure secrets**:
   ```bash
   # Create config directory
   mkdir -p ~/.config/clj-withings
   
   # Create secrets file
   cat > ~/.config/clj-withings/secrets.json << 'EOF'
   {
     "withings": {
       "client_id": "YOUR_WITHINGS_CLIENT_ID",
       "client_secret": "YOUR_WITHINGS_CLIENT_SECRET",
       "redirect_uri": "http://localhost/callback"
     },
     "strava": {
       "client_id": "YOUR_STRAVA_CLIENT_ID", 
       "client_secret": "YOUR_STRAVA_CLIENT_SECRET",
       "redirect_uri": "http://localhost/callback"
     },
     "intervals": {
       "api_key": "YOUR_INTERVALS_API_KEY",
       "athlete_id": YOUR_INTERVALS_ATHLETE_ID
     }
   }
   EOF
   
   # Set restrictive permissions
   chmod 600 ~/.config/clj-withings/secrets.json
   ```

5. **Configure OAuth2 authentication**:
   ```bash
   # Setup Withings OAuth using local secrets (recommended)
   bb setup
   
   # Or provide Withings credentials directly
   bb setup --client-id YOUR_CLIENT_ID --client-secret YOUR_CLIENT_SECRET
   
   # Setup Strava OAuth using local secrets (recommended)
   bb setup-strava
   
   # Or provide Strava credentials directly
   bb setup-strava --client-id YOUR_STRAVA_CLIENT_ID --client-secret YOUR_STRAVA_CLIENT_SECRET
   ```
   
   This will:
   - Open a browser for authorization (for each service)
   - Prompt you to enter the authorization code
   - Save Withings tokens to `~/.config/clj-withings/withings.json`
   - Save Strava tokens to `~/.config/clj-withings/strava.json`

## Usage

### Get Current Weight

```bash
bb weight
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

### Upload to Strava

```bash
# Complete workflow: fetch and upload weight
bb weight | bb push-to-strava

# Manual weight entry
echo "75.1 kg" | bb push-to-strava
echo "165.5 lbs 2025-01-01" | bb push-to-strava

# Upload with specific date
echo "75.1 kg $(date '+%Y-%m-%d')" | bb push-to-strava
```

### OAuth Management

```bash
# Setup Withings OAuth
bb setup

# Setup Strava OAuth  
bb setup-strava

# Check secrets configuration
bb check-secrets
bb check-strava-secrets
```

## Installation

### Development

For development work, use the development shell:

```bash
# Enter development shell with all tools
nix develop

# Use all bb commands directly
bb setup
bb weight
```

### Package Installation

To build and install the package:

```bash
# Build the package
nix build

# Install to your profile
nix profile install .

# Use the installed command
clj-withings setup
clj-withings weight
clj-withings weight | clj-withings push-to-intervals
```

## Files

**Core Scripts:**
- `src/withings/oauth.clj` - OAuth2 authentication flow and token management
- `src/withings/api.clj` - Main API integration with OAuth
- `src/withings/config.clj` - Configuration management

**intervals.icu Integration:**
- `src/intervals/config.clj` - Configuration management for intervals.icu
- `src/intervals/api.clj` - API integration for uploading wellness data

**Strava Integration:**
- `src/strava/config.clj` - Configuration management for Strava
- `src/strava/api.clj` - API integration for uploading weight data to Strava
- `src/strava/oauth.clj` - OAuth2 authentication flow for Strava

**Configuration:**
- `~/.config/clj-withings/secrets.json` - Local secrets file with API credentials (not in git)
- `bb.edn` - Babashka task definitions including `weight`, `push-to-intervals`, and `push-to-strava`
- `~/.config/clj-withings/withings.json` - Withings OAuth configuration and tokens (auto-created)
- `~/.config/clj-withings/strava.json` - Strava OAuth configuration and tokens (auto-created)

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

**Strava Integration:**
- ✅ OAuth2 authentication flow
- ✅ Weight data upload to athlete profile
- ✅ Automatic token refresh and management
- ✅ Support for both kg and lbs input
- ✅ Rate limiting and error handling

## Token Management

The OAuth system automatically:
- Refreshes expired tokens (with 5-minute buffer)
- Stores tokens securely in `~/.config/clj-withings/` directory
- Handles token validation and renewal
