/** Zeitquelle als Port, damit Zeitgrenzen (10 Minuten, VAPID-Ablauf) in Tests festgelegt werden können. */
export interface Clock {
  now(): Date;
}

/** Echte Systemzeit. */
export const systemClock: Clock = { now: () => new Date() };
