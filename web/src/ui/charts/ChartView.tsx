import { useEffect, useRef, useState } from "preact/hooks";
import type { ChartModel } from "./chartModel";

/** Breite eines Elements in CSS-Pixeln (das Diagramm wird in echten Pixeln gezeichnet, damit Schrift mitwächst). */
function useElementWidth(ref: { current: HTMLElement | null }): number {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    setWidth(element.clientWidth);
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => setWidth(element.clientWidth));
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref]);
  return width;
}

function rootFontSize(): number {
  const size = Number.parseFloat(getComputedStyle(document.documentElement).fontSize);
  return Number.isFinite(size) && size > 0 ? size : 16;
}

let chartCounter = 0;

/**
 * Balkendiagramm als SVG mit Bezugslinien (Zucker: 50 g durchgezogen, 25 g gestrichelt) bzw. Bezugsbändern
 * (Alkohol: DGE 2024, kein Grün). Alle Werte stehen zusätzlich als Tabelle bereit (Screenreader, große Schrift).
 */
export function ChartView({ model }: { model: ChartModel }) {
  const container = useRef<HTMLDivElement>(null);
  const width = Math.max(useElementWidth(container), 240);
  const idRef = useRef(`diagramm-${++chartCounter}`);
  const id = idRef.current;
  const font = rootFontSize() * 0.8125;
  const plotHeight = Math.round(Math.min(240, Math.max(150, width * 0.42)));
  const margin = { top: Math.round(font * 0.8), right: Math.round(font * 3.4), bottom: Math.round(font * 2.9), left: Math.round(font * 3.6) };
  const height = plotHeight + margin.top + margin.bottom;
  const plotWidth = width - margin.left - margin.right;
  const y = (value: number) => margin.top + plotHeight - (Math.min(value, model.max) / model.max) * plotHeight;
  const slot = plotWidth / Math.max(model.bars.length, 1);
  const barWidth = Math.max(4, Math.min(slot * 0.62, 56));
  // Beschriftung nur so dicht, wie die Schriftgröße es erlaubt.
  const labelEvery = Math.max(1, Math.ceil((font * 2.6) / slot));
  const lastIndex = model.bars.length - 1;

  return (
    <figure class={`chart chart-${model.kind}`} aria-labelledby={`${id}-caption`}>
      <figcaption id={`${id}-caption`} class="chart-title">
        {model.title}
      </figcaption>
      <div ref={container} class="chart-canvas">
        <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={`${id}-t ${id}-d`}>
          <title id={`${id}-t`}>{model.title}</title>
          <desc id={`${id}-d`}>{model.summary}</desc>
          {model.bands.map((band) => (
            <rect
              key={band.band}
              class={`band band-${band.band.toLowerCase().replace("_", "-")}`}
              x={margin.left}
              width={plotWidth}
              y={y(band.to)}
              height={Math.max(0, y(band.from) - y(band.to))}
            />
          ))}
          {model.ticks.map((tick, index) => (
            <g key={tick}>
              <line class="grid" x1={margin.left} x2={margin.left + plotWidth} y1={y(tick)} y2={y(tick)} />
              <text class="axis-label" x={margin.left - font * 0.4} y={y(tick)} dy="0.35em" text-anchor="end">
                {tick}
                {index === model.ticks.length - 1 ? " g" : ""}
              </text>
            </g>
          ))}
          {model.bars.map((bar, index) => {
            const center = margin.left + slot * index + slot / 2;
            const top = y(bar.value);
            const showLabel = index === lastIndex || (lastIndex - index) % labelEvery === 0;
            return (
              <g key={bar.key}>
                {bar.value > 0 ? (
                  <rect
                    class={bar.isCurrent ? "bar bar-current" : "bar"}
                    x={center - barWidth / 2}
                    y={top}
                    width={barWidth}
                    height={Math.max(1.5, margin.top + plotHeight - top)}
                    rx={Math.min(4, barWidth / 4)}
                  />
                ) : null}
                {showLabel ? (
                  <text class={bar.isCurrent ? "axis-label axis-label-current" : "axis-label"} x={center} y={margin.top + plotHeight + font * 1.25} text-anchor="middle">
                    {bar.shortLabel}
                    {bar.secondLabel && slot > font * 3 ? (
                      <tspan x={center} dy="1.15em">
                        {bar.secondLabel}
                      </tspan>
                    ) : null}
                  </text>
                ) : null}
              </g>
            );
          })}
          {model.lines.map((line) => (
            <g key={line.value}>
              <line
                class={line.style === "DASHED" ? "reference reference-dashed" : "reference"}
                x1={margin.left}
                x2={margin.left + plotWidth}
                y1={y(line.value)}
                y2={y(line.value)}
              />
              <text class="reference-label" x={margin.left + plotWidth + font * 0.4} y={y(line.value)} dy="0.35em">
                {line.shortLabel}
              </text>
            </g>
          ))}
          <line class="axis" x1={margin.left} x2={margin.left + plotWidth} y1={margin.top + plotHeight} y2={margin.top + plotHeight} />
        </svg>
      </div>
      {model.lines.length > 0 ? (
        <ul class="chart-legend">
          {model.lines.map((line) => (
            <li key={line.value}>
              <svg class="legend-swatch" width="32" height="12" aria-hidden="true">
                <line class={line.style === "DASHED" ? "reference reference-dashed" : "reference"} x1="2" x2="30" y1="6" y2="6" />
              </svg>
              <span>
                <strong>{line.shortLabel}</strong> {line.style === "DASHED" ? "(gestrichelt)" : "(durchgezogen)"}: {line.label}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      {model.bands.length > 0 ? (
        <ul class="chart-legend">
          {model.bands.map((band) => (
            <li key={band.band}>
              <span class={`legend-band band-${band.band.toLowerCase().replace("_", "-")}`} aria-hidden="true" />
              <span>
                <strong>{band.label}</strong>:{" "}
                {band.band === "LOW_RISK" ? "unter 27 g pro Woche" : band.band === "MODERATE_RISK" ? "27 bis 81 g pro Woche" : "über 81 g pro Woche"}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      {model.notes.map((note) => (
        <p class="chart-note" key={note}>
          {note}
        </p>
      ))}
      <details class="chart-table">
        <summary>Werte als Tabelle</summary>
        <table>
          <caption class="visually-hidden">{model.title}</caption>
          <thead>
            <tr>
              <th scope="col">{model.kind === "sugar" ? "Tag" : "Woche"}</th>
              <th scope="col">{model.kind === "sugar" ? "Zucker" : "Alkohol"}</th>
              {model.kind === "alcohol" ? <th scope="col">Hinweis</th> : null}
            </tr>
          </thead>
          <tbody>
            {[...model.bars].reverse().map((bar) => (
              <tr key={bar.key}>
                <th scope="row">{bar.label}</th>
                <td>{bar.valueText}</td>
                {model.kind === "alcohol" ? <td>{bar.detail}</td> : null}
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
