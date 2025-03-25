{
  lib,
  stdenv,
  jdk23,
  gradle,
  makeWrapper,
}: let
  jdk = jdk23;
  self = stdenv.mkDerivation (finalAttrs: {
    pname = "tpudp";
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

    gradleBuildTask = "buildAllJars";

    installPhase = ''
        mkdir -p $out/{bin,share/suuuuuuuuuudoku}
        ls build/libs
        cp build/libs/port-scanner-1.0.jar $out/share/suuuuuuuuuudoku
        cp build/libs/udp-client-1.0.jar $out/share/suuuuuuuuuudoku
        cp build/libs/udp-server-1.0.jar $out/share/suuuuuuuuuudoku

        makeWrapper ${jdk}/bin/java $out/bin/server \
            --add-flags "-jar $out/share/suuuuuuuuuudoku/udp-server-1.0.jar"

        makeWrapper ${jdk}/bin/java $out/bin/client \
            --add-flags "-jar $out/share/suuuuuuuuuudoku/udp-client-1.0.jar"

        makeWrapper ${jdk}/bin/java $out/bin/port-scanner \
            --add-flags "-jar $out/share/suuuuuuuuuudoku/port-scanner-1.0.jar"
    '';

    meta.sourceProvenance = with lib.sourceTypes; [
      fromSource
      binaryBytecode # mitm cache
    ];
  });
in
  self
