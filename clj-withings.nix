{ lib, stdenv, babashka, sops, age, makeWrapper }:

stdenv.mkDerivation rec {
  pname = "clj-withings";
  version = "0.1.0";

  src = ./.;

  nativeBuildInputs = [ makeWrapper ];

  buildInputs = [ babashka sops age ];

  dontBuild = true;

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    mkdir -p $out/share/clj-withings

    # Copy source files and bb.edn
    cp -r src $out/share/clj-withings/
    cp bb.edn $out/share/clj-withings/

    # Create wrapper script
    makeWrapper ${babashka}/bin/bb $out/bin/clj-withings \
      --chdir $out/share/clj-withings \
      --prefix PATH : ${lib.makeBinPath [ sops age ]}

    runHook postInstall
  '';

  meta = with lib; {
    description = "Clojure/Babashka tool for fetching weight data from Withings API and syncing to fitness platforms";
    homepage = "https://github.com/hamann/clj-withings";
    license = licenses.mit;
    maintainers = ["Holger Amann"];
    platforms = platforms.all;
    mainProgram = "clj-withings";
  };
}
