/** Erkennung von Plattform-Eigenheiten (iPhone-Hinweis, Android-App-Link). Reine Funktionen für Tests. */
export interface PlatformInfo {
  /** iPhone, iPad oder iPod (auch iPadOS mit Desktop-Kennung). */
  readonly isIos: boolean;
  readonly isAndroid: boolean;
  /** Als App vom Home-Bildschirm gestartet (PWA). */
  readonly isStandalone: boolean;
}

export interface NavigatorLike {
  readonly userAgent: string;
  readonly platform?: string;
  readonly maxTouchPoints?: number;
  readonly standalone?: boolean;
}

export function detectPlatform(nav: NavigatorLike, displayModeStandalone: boolean): PlatformInfo {
  const ua = nav.userAgent;
  const isIpadOs = nav.platform === "MacIntel" && (nav.maxTouchPoints ?? 0) > 1;
  const isIos = /iPad|iPhone|iPod/.test(ua) || isIpadOs;
  return {
    isIos,
    isAndroid: /Android/i.test(ua),
    isStandalone: displayModeStandalone || nav.standalone === true,
  };
}

export function currentPlatform(): PlatformInfo {
  const standalone = typeof matchMedia === "function" && matchMedia("(display-mode: standalone)").matches;
  return detectPlatform(navigator as NavigatorLike, standalone);
}

/** Auf dem iPhone gibt es Web Push nur nach „Zum Home-Bildschirm“ (ab iOS 16.4). */
export function needsHomeScreenForPush(platform: PlatformInfo): boolean {
  return platform.isIos && !platform.isStandalone;
}
