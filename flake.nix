{
  description = "A Nix-flake-based Java development environment";

  inputs = {
    nixpkgs.url = "nixpkgs/nixpkgs-unstable";

    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
  };

  outputs = {
    flake-parts,
    ...
  } @ inputs:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = ["x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin"];

      perSystem = {
        self',
        lib,
        pkgs,
        ...
      }: {
        packages = {
          default = self'.packages.tpudp;
          tpudp = pkgs.callPackage ./package.nix {};
        };

        devShells.default = pkgs.mkShell {
          inputsFrom = with self'.packages; [default];

          # LD_LIBRARY_PATH = lib.makeLibraryPath [];
        };

        formatter = pkgs.alejandra;
      };
    };
}
