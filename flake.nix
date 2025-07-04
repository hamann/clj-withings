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
        
        # Custom babashka package with clj-yaml dependency
        babashka-with-deps = pkgs.babashka.overrideAttrs (oldAttrs: {
          nativeBuildInputs = oldAttrs.nativeBuildInputs or [] ++ [
            pkgs.makeWrapper
          ];
          
          postInstall = oldAttrs.postInstall or "" + ''
            # Add clj-yaml to babashka classpath
            wrapProgram $out/bin/bb \
              --set BABASHKA_CLASSPATH "${pkgs.clojure}/share/clojure/clojure.jar"
          '';
        });

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

        # Scripts for common tasks
        scripts = {
          setup-oauth = pkgs.writeShellScriptBin "setup-oauth" ''
            echo "🔐 Setting up OAuth with SOPS..."
            bb oauth.clj --setup
          '';
          
          get-weight = pkgs.writeShellScriptBin "get-weight" ''
            echo "⚖️  Getting current weight..."
            bb get-weight-oauth.clj
          '';
          
          check-sops = pkgs.writeShellScriptBin "check-sops" ''
            echo "🔍 Checking SOPS configuration..."
            bb sops-helper.clj check
          '';
          
          decrypt-secrets = pkgs.writeShellScriptBin "decrypt-secrets" ''
            echo "🔓 Decrypting secrets..."
            bb sops-helper.clj decrypt
          '';
        };

      in
      {
        # Development shell
        devShells.default = pkgs.mkShell {
          buildInputs = devDependencies ++ (builtins.attrValues scripts);
          
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
            echo "Custom scripts:"
            echo "  setup-oauth       - Setup OAuth authentication"
            echo "  get-weight        - Get current weight"
            echo "  check-sops        - Check SOPS configuration"
            echo "  decrypt-secrets   - Decrypt and show secrets"
            echo
            echo "First time setup:"
            echo "  1. Add your credentials to secrets.yaml"
            echo "  2. Run: sops -e -i secrets.yaml"
            echo "  3. Run: setup-oauth"
            echo "  4. Run: get-weight"
            echo
            
            # Check if secrets.yaml exists
            if [ ! -f secrets.yaml ]; then
              echo "⚠️  secrets.yaml not found. Creating template..."
              cat > secrets.yaml << 'EOF'
withings:
  client_id: "YOUR_CLIENT_ID_HERE"
  client_secret: "YOUR_CLIENT_SECRET_HERE"
  redirect_uri: "http://localhost:8080/callback"
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
          
          # Environment variables
          BABASHKA_CLASSPATH = "${pkgs.clojure}/share/clojure/clojure.jar";
          
          # Set up Age key file if it exists
          SOPS_AGE_KEY_FILE = "./key.txt";
        };

        # Packages that can be built
        packages = {
          # Script package
          withings-scripts = pkgs.stdenv.mkDerivation {
            pname = "withings-scripts";
            version = "0.1.0";
            
            src = ./.;
            
            buildInputs = [ babashka-with-deps ];
            
            installPhase = ''
              mkdir -p $out/bin
              
              # Install scripts
              cp *.clj $out/bin/
              chmod +x $out/bin/*.clj
              
              # Create wrapper scripts
              cat > $out/bin/withings-get-weight << 'EOF'
              #!/bin/bash
              bb $out/bin/get-weight-oauth.clj "$@"
              EOF
              chmod +x $out/bin/withings-get-weight
              
              cat > $out/bin/withings-oauth << 'EOF'
              #!/bin/bash
              bb $out/bin/oauth.clj "$@"
              EOF
              chmod +x $out/bin/withings-oauth
            '';
            
            meta = with pkgs.lib; {
              description = "Babashka scripts for Withings API";
              license = licenses.mit;
              platforms = platforms.unix;
            };
          };
        };
        
        # Default package
        defaultPackage = self.packages.${system}.withings-scripts;
        
        # Applications
        apps = {
          get-weight = {
            type = "app";
            program = "${self.packages.${system}.withings-scripts}/bin/withings-get-weight";
          };
          
          oauth = {
            type = "app";
            program = "${self.packages.${system}.withings-scripts}/bin/withings-oauth";
          };
        };
        
        # Default app
        defaultApp = self.apps.${system}.get-weight;
      });
}