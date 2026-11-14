import type { ComponentChildren } from "preact";
import { useEffect, useRef } from "preact/hooks";

let dialogCounter = 0;

/**
 * Modaler Dialog über das native `<dialog>` (Fokusfalle, Escape und Screenreader-Semantik kommen vom Browser).
 * Beim Schließen kehrt der Fokus zum auslösenden Element zurück.
 */
export function Dialog({
  open,
  title,
  onClose,
  children,
  actions,
  wide = false,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ComponentChildren;
  actions?: ComponentChildren;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const idRef = useRef(`dialog-${++dialogCounter}`);
  const returnFocus = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
      returnFocus.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const handleCancel = (event: Event) => {
      event.preventDefault();
      onClose();
    };
    dialog.addEventListener("cancel", handleCancel);
    return () => dialog.removeEventListener("cancel", handleCancel);
  }, [onClose]);

  return (
    <dialog ref={ref} class={wide ? "dialog dialog-wide" : "dialog"} aria-labelledby={`${idRef.current}-titel`}>
      {open ? (
        <div class="dialog-body">
          <h2 id={`${idRef.current}-titel`}>{title}</h2>
          {children}
          {actions ? <div class="dialog-actions">{actions}</div> : null}
        </div>
      ) : null}
    </dialog>
  );
}
