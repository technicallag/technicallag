package masters

import java.io.Serializable

enum class PackageManager(val nameInDB: String) : Serializable {
    CPAN("CPAN"),
    CRAN("CRAN"),
    DUB("Dub"),
    ELM("Elm"),
    HAXELIB("Haxelib"),
    HOMEBREW("Homebrew"),
    PUB("Pub"),
    PUPPET("Puppet"),

    ATOM("Atom"),
    CARGO("Cargo"),
    HEX("Hex"),
    MAVEN("Maven"),
    NPM("NPM"),
    NUGET("NuGet"),
    PACKAGIST("Packagist"),
    PYPI("Pypi"),
    RUBYGEMS("Rubygems");

    public override fun toString() : String = nameInDB
}