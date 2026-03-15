{
  description = "Akeyless Credentials Provider for Jenkins";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    substrate = {
      url = "github:pleme-io/substrate";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, substrate, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs { inherit system; };
      mkJavaMavenPackage = (import "${substrate}/lib/java-maven.nix").mkJavaMavenPackage;
    in {
      packages.default = mkJavaMavenPackage pkgs {
        pname = "akeyless-credentials-provider";
        version = "0.0.0-dev";
        src = self;
        mvnHash = "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="; # TODO: set correct hash
        description = "Akeyless Credentials Provider for Jenkins — access credentials from Akeyless as a CredentialsProvider";
        homepage = "https://github.com/pleme-io/JenkinsSecretsManagerProvider";
      };

      devShells.default = pkgs.mkShellNoCC {
        packages = with pkgs; [ jdk17 maven ];
      };
    });
}
