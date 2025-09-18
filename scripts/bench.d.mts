export interface BenchSummary {
  readonly name: string;
  readonly runs: number;
  readonly medianMs: number;
  readonly fastestMs: number;
  readonly slowestMs: number;
  readonly spreadMs: number;
}

export declare function median(values: readonly number[]): number;
export declare function spread(values: readonly number[]): number;
export declare function summarise(name: string, samples: readonly number[]): BenchSummary;
export declare function formatSummary(summary: BenchSummary): string;
export declare function timeOnce(body: () => void): number;
export declare function bench(
  name: string,
  body: () => void,
  runs?: number,
  warmUp?: number,
): BenchSummary;
export declare function usage(): string;
