{
  description = "Withings Weight Tracker - Babashka scripts for Withings API";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # Use standard babashka - bb.edn handles dependencies
        babashka-with-deps = pkgs.babashka;

        # Development dependencies
        devDependencies = with pkgs; [
          babashka-with-deps
          jq
          curl
          git

          # Optional but useful for development
          clojure
          clj-kondo
          jet # JSON/EDN processor
        ];

        # Build the package
        clj-withings = pkgs.callPackage ./clj-withings.nix {};

      in
      {
        # Packages
        packages = {
          default = clj-withings;
          clj-withings = clj-withings;
        };

        # Development shell
        devShells.default = pkgs.mkShell {
          buildInputs = devDependencies;

          shellHook = ''
            echo "🚀 Withings Weight Tracker Development Environment"
            echo
            echo "Available tools:"
            echo "  bb                - Babashka (Clojure scripting)"
            echo "  clj-kondo         - Clojure linter"
            echo "  jet               - JSON/EDN processor"
            echo
            echo "BB tasks:"
            echo "  bb setup          - Setup OAuth authentication" 
            echo "  bb weight         - Get current weight"
            echo "  bb check-secrets  - Check secrets configuration"
            echo "  bb test-token     - Test token validity"
            echo "  bb push-to-intervals - Push weight to intervals.icu"
            echo "  bb push-to-strava    - Push weight to Strava"
            echo
            echo "First time setup:"
            echo "  1. Configure secrets: ~/.config/clj-withings/secrets.json"
            echo "  2. Run: bb setup"
            echo "  3. Run: bb weight"
            echo

            # Check if secrets file exists
            if [ ! -f ~/.config/clj-withings/secrets.json ]; then
              echo "⚠️  Secrets file not found. Setting up template..."
              mkdir -p ~/.config/clj-withings
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
              chmod 600 ~/.config/clj-withings/secrets.json
              echo "✅ Created secrets.json template"
              echo "   Edit ~/.config/clj-withings/secrets.json with your credentials"
              echo
            fi
          '';

          # Let bb.edn handle the classpath
        };

      });
}
