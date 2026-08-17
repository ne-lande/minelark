{
  description = "Minelark — Fabric Minecraft 1.21.x mod dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };

        # Minecraft 1.21.x requires Java 21. Temurin (Adoptium) is the
        # recommended JDK distribution for Minecraft development.
        jdk = pkgs.temurin-bin-21;

        # Native libraries the Minecraft *client* opens at runtime via dlopen (LWJGL/GLFW,
        # sound). Without them on LD_LIBRARY_PATH, `./gradlew runClient` crashes in GLFW init
        # with "Failed to detect any supported platform". Not needed to build or run the server.
        clientRuntimeLibs = with pkgs; [
          libGL
          xorg.libX11
          xorg.libXcursor
          xorg.libXrandr
          libxi
          libxext
          libxxf86vm
          wayland
          libxkbcommon
          openal
          libpulseaudio
        ];
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            pkgs.gradle    # a system Gradle; the project also ships a Gradle wrapper
            pkgs.git
            # Documentation site (Material for MkDocs). Build with: mkdocs build --strict
            (pkgs.python3.withPackages (ps: [ ps.mkdocs ps.mkdocs-material ]))
          ];

          JAVA_HOME = "${jdk}";

          shellHook = ''
            # Let the client's LWJGL/GLFW natives find the system graphics/sound libraries.
            # (build.gradle scans LD_LIBRARY_PATH to point LWJGL at the system libopenal.so, whose
            # bundled copy fails to load on NixOS.)
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath clientRuntimeLibs}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            echo "Minelark dev shell"
            echo "  JDK:    $(java -version 2>&1 | head -n1)"
            echo "  Gradle: $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'use ./gradlew')"
          '';
        };
      });
}
