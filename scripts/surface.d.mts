export interface ExportedName {
  readonly kind: string;
  readonly name: string;
}

export interface ModuleSurface {
  readonly module: string;
  readonly names: readonly ExportedName[];
}

export interface SurfaceCheck {
  readonly ok: boolean;
  readonly reason: string;
}

export declare function exportsIn(text: string): ExportedName[];
export declare function collectSurface(): Promise<ModuleSurface[]>;
export declare function renderSurface(modules: readonly ModuleSurface[]): string;
export declare function writeSurface(): Promise<string>;
export declare function checkSurface(): Promise<SurfaceCheck>;
