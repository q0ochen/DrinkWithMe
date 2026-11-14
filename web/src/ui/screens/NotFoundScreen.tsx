import { Link, PageHeader } from "../components/common";
import { useDocumentTitle } from "../context";

export function NotFoundScreen() {
  useDocumentTitle("Seite nicht gefunden");
  return (
    <div class="screen narrow">
      <PageHeader title="Seite nicht gefunden">
        <p class="lead">Diese Adresse gibt es in Drink With Me nicht – oder der Link ist unvollständig.</p>
      </PageHeader>
      <Link to="/" class="button button-primary">
        Zur Bar
      </Link>
    </div>
  );
}
