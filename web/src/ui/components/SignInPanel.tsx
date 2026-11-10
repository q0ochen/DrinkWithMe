import { germanMessage } from "../../social/errors";
import { useAction, useServices, useSocial } from "../context";
import { ErrorText, ServerNotConfigured, Spinner } from "./common";

/** Erklärung und Knopf „Mit Google anmelden“ (Supabase, PKCE). */
export function SignInPanel({ returnPath, intro }: { returnPath: string; intro?: string }) {
  const services = useServices();
  const social = useSocial();
  const signIn = useAction(async () => {
    const loaded = social ?? (await services.social());
    if (!loaded) return;
    await loaded.auth.signInWithGoogle(returnPath);
  });

  if (!services.config.supabase) return <ServerNotConfigured />;
  return (
    <div class="sign-in">
      <p>{intro ?? "Für Freunde brauchst du ein Google-Konto. Bar, Verlauf und Vorlesen funktionieren auch ohne Anmeldung."}</p>
      <p class="muted">
        Geteilt werden nur Name, Bild, Art und Menge eines Getränks und der Zeitpunkt – nie Gramm, Zucker oder deine Statistik. Alkoholische
        Getränke erst nach deiner Zustimmung.
      </p>
      {social === undefined ? <Spinner label="Anmeldung wird vorbereitet …" /> : null}
      <button type="button" class="button button-primary button-large" onClick={() => void signIn.run()} disabled={signIn.busy || social === undefined}>
        Mit Google anmelden
      </button>
      <ErrorText error={signIn.error ? germanMessage(signIn.error) : null} />
    </div>
  );
}
