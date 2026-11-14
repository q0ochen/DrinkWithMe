import { useEffect, useRef, useState } from "preact/hooks";
import { germanMessage } from "../../social/errors";
import { Link, PageHeader, ServerNotConfigured, Spinner } from "../components/common";
import { useDocumentTitle, useRouter, useServices } from "../context";

/**
 * Rückkehr von Google: `<WEB_APP_URL>/auth/callback?code=…` (PKCE). Der Code wird hier gegen eine Sitzung
 * getauscht; danach geht es dorthin zurück, wo die Anmeldung begonnen hat (z. B. /einladung/<token>).
 */
export function AuthCallbackScreen() {
  useDocumentTitle("Anmeldung");
  const services = useServices();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current || !services.config.supabase) return;
    started.current = true;
    const url = new URL(window.location.href);
    void (async () => {
      try {
        const social = await services.social();
        if (!social) throw new Error("Server nicht erreichbar");
        await social.auth.completeSignIn(url);
        const returnPath = services.settings.current().returnPath ?? "/freunde";
        await services.settings.update({ returnPath: null });
        router.navigate(returnPath, { replace: true });
      } catch (caught) {
        // Code nicht in der Adresszeile stehen lassen.
        window.history.replaceState(null, "", router.href("/auth/callback"));
        setError(germanMessage(caught));
      }
    })();
  }, [services, router]);

  return (
    <div class="screen narrow">
      <PageHeader title="Anmeldung" />
      {!services.config.supabase ? (
        <ServerNotConfigured />
      ) : error ? (
        <>
          <p class="error-text" role="alert">
            Die Anmeldung hat nicht geklappt. {error}
          </p>
          <Link to="/freunde" class="button button-primary">
            Zurück zu Freunde
          </Link>
        </>
      ) : (
        <Spinner label="Anmeldung wird abgeschlossen …" />
      )}
    </div>
  );
}
