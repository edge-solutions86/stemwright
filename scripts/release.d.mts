export interface PackageLike {
  readonly version?: unknown;
  readonly bin?: Readonly<Record<string, string>>;
  readonly main?: unknown;
  readonly types?: unknown;
}

export interface ReleaseCheck {
  readonly ok: boolean;
  readonly problems: readonly string[];
  readonly version: string;
}

export declare function versionFromSource(text: string): string | undefined;
export declare function changelogMentions(text: string, version: string): boolean;
export declare function binariesIn(pkg: PackageLike): { name: string; path: string }[];
export declare function checkAgreement(
  pkg: PackageLike,
  sourceVersion: string | undefined,
  changelog: string,
): string[];
export declare function checkRelease(): Promise<ReleaseCheck>;
