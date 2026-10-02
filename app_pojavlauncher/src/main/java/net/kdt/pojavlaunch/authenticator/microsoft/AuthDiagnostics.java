package net.kdt.pojavlaunch.authenticator.microsoft;

/** Never include exception messages or causes: providers may put credentials in either. */
public final class AuthDiagnostics {
    private AuthDiagnostics() {}

    public static String failure(Throwable error) {
        return "Microsoft authentication failed (" + error.getClass().getSimpleName() + ")";
    }
}
