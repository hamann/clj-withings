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
          sops
          age
          gnupg
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
            echo "  sops              - Secrets management"
            echo "  age               - Age encryption"
            echo "  gnupg             - GPG encryption"
            echo "  clj-kondo         - Clojure linter"
            echo "  jet               - JSON/EDN processor"
            echo
            echo "BB tasks:"
            echo "  bb setup          - Setup OAuth authentication" 
            echo "  bb weight         - Get current weight"
            echo "  bb check-sops     - Check SOPS configuration"
            echo "  bb test-token     - Test token validity"
            echo "  bb push-weight    - Push weight to intervals.icu"
            echo
            echo "First time setup:"
            echo "  1. Add your credentials to secrets.yaml"
            echo "  2. Run: sops -e -i secrets.yaml"
            echo "  3. Run: bb setup"
            echo "  4. Run: bb weight"
            echo

            # Check if secrets.yaml exists
            if [ ! -f secrets.yaml ]; then
              echo "⚠️  secrets.yaml not found. Creating template..."
              cat > secrets.yaml << 'EOF'
withings:
  client_id: "YOUR_CLIENT_ID_HERE"
  client_secret: "YOUR_CLIENT_SECRET_HERE"
  redirect_uri: "http://localhost:8080/callback"
intervals:
  api_key: "YOUR_INTERVALS_ICU_API_KEY_HERE"
EOF
              echo "✅ Created secrets.yaml template"
              echo "   Edit it with your credentials, then run: sops -e -i secrets.yaml"
              echo
            fi

            # Check if .sops.yaml exists
            if [ ! -f .sops.yaml ]; then
              echo "⚠️  .sops.yaml not found. Consider setting up encryption."
              echo "   For Age: age-keygen -o key.txt && export SOPS_AGE_KEY_FILE=./key.txt"
              echo "   For GPG: gpg --generate-key"
              echo
            fi
          '';

          # Let bb.edn handle the classpath
        };

      });
}
