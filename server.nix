{
  lib,
  stdenv,
  jdk23,
  gradle,
  makeWrapper,
}: let
  jdk = jdk23;
  self = stdenv.mkDerivation (finalAttrs: {
    pname = "tpudp_server";
    version = "0.0.0";

    src = lib.cleanSource ./.;

    nativeBuildInputs = [
      (gradle.override {java = jdk;})
      jdk
      makeWrapper
    ];

    buildInputs = [];

    mitmCache = gradle.fetchDeps {
      pkg = self;
      /*
      To update this file, run:
      nix build .#tpudp_server.mitmCache.updateScript
      ./result
      */
      data = ./deps.json;
    };

    gradleFlags = ["-Dfile.encoding=utf-8"];

    doCheck = true;

    # gradleBuildTask = "buildAllJars";

    installPhase = ''
        mkdir -p $out/{bin,share/suuuuuuuuuudoku}
        ls build/libs
        cp build/libs/tpudp-1.0-SNAPSHOT.jar $out/share/suuuuuuuuuudoku

        makeWrapper ${jdk}/bin/java $out/bin/tui \
          --add-flags "-jar $out/share/suuuuuuuuuudoku/tpudp-1.0-SNAPSHOT.jar"
    '';

    meta.sourceProvenance = with lib.sourceTypes; [
      fromSource
      binaryBytecode # mitm cache
    ];
  });
in
  self
