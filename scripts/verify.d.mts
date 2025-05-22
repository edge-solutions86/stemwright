/**
 * Types for the verify runner. The runner itself is plain javascript so it can
 * be run before anything is built, and this file is what lets the test that
 * checks its plan be type checked like everything else.
 */
export interface VerifyStage {
  readonly name: string;
  readonly command: string;
  readonly args: readonly string[];
}

export interface StageResult {
  readonly name: string;
  readonly ok: boolean;
  readonly ms: number;
}

export declare const STAGES: readonly VerifyStage[];
export declare function stageNamed(name: string): VerifyStage | undefined;
export declare function planFor(wanted: readonly string[]): VerifyStage[];
export declare function runStage(stage: VerifyStage): StageResult;
export declare function main(wanted: readonly string[]): number;
