package masters.flexilag

import masters.libiostudy.Version

/**
 * @author ______
 */
class HaxelibLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        return if (declaration == "*")
            Declaration.getAny()
        else
            Declaration(Version.create(declaration), Version.create(declaration))
    }
}