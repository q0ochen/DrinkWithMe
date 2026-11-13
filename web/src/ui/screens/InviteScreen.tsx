import { useState } from "preact/hooks";
import { inviteAppLink } from "../../core/social/inviteLinks";
import type { UserProfile } from "../../core/social/ports";
import { germanMessage } from "../../social/errors";
import { SignInPanel } from "../components/SignInPanel";
import { ErrorText, Link, Notice, PageHeader, ServerNotConfigured, Spinner } from "../components/common";
import { useAction, useAnnouncer, useDocumentTitle, useServices, useSessionUser, useSocial } from "../context";

/**
 * Einladungsseite `/einladung/<token>`: auf Android „In der App öffnen“ (drinkwithme://einladung/<token>),
 * sonst nach der Anmeldung direkt im Browser annehmen. Angenommen wird erst nach einem Tipp (Zustimmung).
 * Wer im Browser angemeldet ist (z. B. nach „Stattdessen im Browser annehmen“ → Google → zurück), sieht das
 * Annehmen sofort – die Wahl geht beim Umweg über Google nicht verloren.
 */
export function InviteScreen({ token }: { token: string }) {
  useDocumentTitle("Einladung");
  const services = useServices();
  const social = useSocial();
  const user = useSessionUser();
  const announcer = useAnnouncer();
  const [friend, setFriend] = useState<UserProfile | null>(null);
  const [browserChosen, setInBrowser] = useState(false);
  const inBrowser = !services.platform.isAndroid || browserChosen || user !== null;
  const accept = useAction(async () => {
    if (!social) return;
    const profile = await social.friends.acceptInvite(token);
    setFriend(profile);
    announcer.announce(`Du bist jetzt mit ${profile.displayName} befreundet.`);
  });

  return (
    <div class="screen">
      <PageHeader title="Einladung zu Drink With Me">
        <p class="lead">Jemand möchte sich mit dir vernetzen: Ihr seht dann gegenseitig, was ihr gerade trinkt.</p>
      </PageHeader>
      {services.platform.isAndroid ? (
        <section class="card">
          <h2>Auf Android</h2>
          <p>Hast du die App Drink With Me installiert? Dann öffne die Einladung direkt dort.</p>
          <a class="button button-primary button-large" href={inviteAppLink(token)}>
            In der App öffnen
          </a>
          {!inBrowser ? (
            <button type="button" class="button button-secondary" onClick={() => setInBrowser(true)}>
              Stattdessen im Browser annehmen
            </button>
          ) : null}
        </section>
      ) : null}
      {inBrowser ? (
        <section class="card">
          <h2>{services.platform.isAndroid ? "Im Browser annehmen" : "Einladung annehmen"}</h2>
          {!services.config.supabase ? (
            <ServerNotConfigured />
          ) : social === undefined ? (
            <Spinner label="Wird vorbereitet …" />
          ) : !user ? (
            <SignInPanel returnPath={`/einladung/${token}`} intro="Melde dich zuerst mit deinem Google-Konto an. Danach kannst du die Einladung annehmen." />
          ) : friend ? (
            <Notice tone="success" role="status">
              <p>
                <strong>Du bist jetzt mit {friend.displayName} befreundet.</strong>
              </p>
              <p>
                <Link to="/freunde">Zu deinen Freunden</Link>
              </p>
            </Notice>
          ) : (
            <>
              <p>Angemeldet als {user.displayName}. Mit einem Tipp seid ihr befreundet.</p>
              <button type="button" class="button button-primary button-large" onClick={() => void accept.run()} disabled={accept.busy}>
                Einladung annehmen
              </button>
              <ErrorText error={accept.error ? germanMessage(accept.error) : null} />
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}
